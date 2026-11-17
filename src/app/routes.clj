(ns app.routes
  (:require [app.application :as application]
            [app.db :as db]
            [app.views.home :as home]
            [app.views.layout :as layout]
            [app.views.meetup :as meetup]
            [clojure.string :as str]
            [reitit.ring :as ring]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as response])
  (:import (java.time Instant)))

(def ^:private flash-statuses
  {"applied"       :applied
   "subscribed"    :subscribed
   "invalid-email" :invalid-email})

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

(defn- submit-application [{:keys [form-params]}]
  (let [values (application/parse form-params)
        errors (application/validate values)]
    (if (seq errors)
      (html 422 (home/page {:uri         "/"
                            :meetups     (db/meetups)
                            :application {:values values :errors errors}}))
      (do (swap! db/applications conj (assoc values :submitted-at (Instant/now)))
          (response/redirect "/?status=applied#apply" :see-other)))))

(defn- local-path
  "Only send people back to a path on this site."
  [path]
  (if (and (string? path) (re-matches #"/(?![/\\]).*" path)) path "/"))

(defn- subscribe [{:keys [form-params]}]
  (let [email (get form-params "email")
        back  (local-path (get form-params "back"))]
    (if (application/email? email)
      (do (swap! db/subscribers conj (-> email str/trim str/lower-case))
          (response/redirect (str back "?status=subscribed#newsletter") :see-other))
      (response/redirect (str back "?status=invalid-email#newsletter") :see-other))))

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
