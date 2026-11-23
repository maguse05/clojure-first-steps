(ns app.routes
  (:require [app.application :as application]
            [app.db :as db]
            [app.mailchimp :as mailchimp]
            [app.sheet :as sheet]
            [app.views.home :as home]
            [app.views.layout :as layout]
            [app.views.meetup :as meetup]
            [clojure.string :as str]
            [reitit.ring :as ring]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as response])
  (:import (java.time Instant)))

(def ^:private flash-statuses
  {"applied"            :applied
   "subscribed"         :subscribed
   "already-subscribed" :already-subscribed
   "failed"             :subscribe-failed
   "invalid-email"      :invalid-email})

(defn- page-opts [{:keys [uri query-params]}]
  {:uri   uri
   :flash (flash-statuses (get query-params "status"))})

(defn- html [status body]
  (-> (response/response body)
      (response/status status)
      (response/content-type "text/html; charset=utf-8")))

(defn- not-found [req]
  (html 404 (layout/not-found (page-opts req))))

(defn- home-page [req]
  (html 200 (home/page (assoc (page-opts req)
                              :meetups (db/meetups)
                              :query (get-in req [:query-params "country"])))))

(defn- meetup-page [req]
  (if-let [m (db/meetup (get-in req [:path-params :slug]))]
    (html 200 (meetup/page m (page-opts req)))
    (not-found req)))

(defn- save-application!
  "Without a sheet to write to, as in development, applications are kept in
  memory."
  [values]
  (if-let [sheet (sheet/config)]
    (sheet/append! sheet values)
    (do (swap! db/applications conj (assoc values :submitted-at (Instant/now)))
        :saved)))

(defn- application-page [status application]
  (html status (home/page {:uri         "/"
                           :meetups     (db/meetups)
                           :application application})))

(defn- submit-application [{:keys [form-params]}]
  (let [values (application/parse form-params)
        errors (application/validate values)]
    (cond
      (seq errors)
      (application-page 422 {:values values :errors errors})

      (= :failed (save-application! values))
      (application-page 503 {:values values :failed? true})

      :else
      (do (some-> (mailchimp/config) (mailchimp/add-applicant! values))
          (response/redirect "/?status=applied#apply" :see-other)))))

(defn- local-path
  "Only send people back to a path on this site."
  [path]
  (if (and (string? path) (re-matches #"/(?![/\\]).*" path)) path "/"))

(defn- add-subscriber! [email]
  (if-let [mailchimp (mailchimp/config)]
    (mailchimp/subscribe! mailchimp email)
    (do (swap! db/subscribers conj email)
        :subscribed)))

(defn- subscribe [{:keys [form-params]}]
  (let [email  (some-> (get form-params "email") str/trim str/lower-case)
        back   (local-path (get form-params "back"))
        status (if (application/email? email)
                 (name (add-subscriber! email))
                 "invalid-email")]
    (response/redirect (str back "?status=" status "#newsletter") :see-other)))

(def app
  (ring/ring-handler
   (ring/router
    [["/" {:get home-page}]
     ["/apply" {:post submit-application}]
     ["/subscribe" {:post subscribe}]
     ["/meetups/:slug" {:get meetup-page}]]
    {:data {:middleware [wrap-params]}})
   (ring/routes
    (ring/create-resource-handler {:path "/" :root "public"})
    (ring/create-default-handler {:not-found not-found}))))
