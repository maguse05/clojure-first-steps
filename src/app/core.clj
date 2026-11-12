(ns app.core
  (:require [app.views :as views]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [org.httpkit.server :as http])
  (:import (java.net URLDecoder))
  (:gen-class))

;; ---------------------------------------------------------------------------
;; Meetup data
;;
;; The meetups live in resources/meetups.edn. We read the file on every
;; request, so edits show up on the next page refresh — no restart needed.

(defn load-meetups []
  (edn/read-string (slurp (io/resource "meetups.edn"))))

(defn find-meetup [meetups slug]
  (first (filter #(= slug (:slug %)) meetups)))

;; ---------------------------------------------------------------------------
;; In-memory storage
;;
;; These atoms are reset whenever the server restarts (and on Vercel every
;; instance has its own copy), so they're only good for trying things out.
;; Before going live, send these to a real service — e.g. a newsletter tool
;; for emails and a database or GitHub issue for meetup submissions.

(defonce subscribers (atom #{}))
(defonce submissions (atom []))

;; ---------------------------------------------------------------------------
;; Responses

(defn html-response [status body]
  {:status  status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    body})

(defn redirect [location]
  {:status 303 :headers {"Location" location}})

;; ---------------------------------------------------------------------------
;; Request helpers

(defn- decode [s]
  (URLDecoder/decode (or s "") "UTF-8"))

(defn parse-params
  "Turns \"a=1&b=hello+world\" into {:a \"1\" :b \"hello world\"}."
  [s]
  (if (str/blank? s)
    {}
    (into {}
          (for [pair (str/split s #"&")
                :let [[k v] (str/split pair #"=" 2)]
                :when (not (str/blank? k))]
            [(keyword (decode k)) (str/trim (decode v))]))))

(defn- form-params [req]
  (parse-params (some-> (:body req) slurp)))

(defn- query-params [req]
  (parse-params (:query-string req)))

(def ^:private flash-keys
  "Query-string values we show as messages after a form is submitted."
  {"subscribed" :subscribed
   "bad-email"  :bad-email
   "submitted"  :submitted
   "bad-meetup" :bad-meetup})

(defn- page-opts [req]
  {:path  (:uri req)
   :flash (flash-keys (:status (query-params req)))})

(defn valid-email? [s]
  (boolean (and s (re-matches #"[^\s@]+@[^\s@]+\.[^\s@]+" s))))

(defn- safe-path
  "Only redirect back to paths on this site (avoids open redirects)."
  [path]
  (if (and path (str/starts-with? path "/") (not (str/starts-with? path "//")))
    path
    "/"))

;; ---------------------------------------------------------------------------
;; Handlers

(defn- home [req]
  (html-response 200 (views/home-page (load-meetups) (page-opts req))))

(defn- meetup [req slug]
  (if-let [m (find-meetup (load-meetups) slug)]
    (html-response 200 (views/meetup-page m (page-opts req)))
    (html-response 404 (views/not-found-page (page-opts req)))))

(defn- subscribe [req]
  (let [{:keys [email back]} (form-params req)
        back (safe-path back)]
    (if (valid-email? email)
      (do (swap! subscribers conj (str/lower-case email))
          (println "New newsletter subscriber. Total:" (count @subscribers))
          (redirect (str back "?status=subscribed#newsletter")))
      (redirect (str back "?status=bad-email#newsletter")))))

(defn- submit-meetup [req]
  (let [params   (form-params req)
        required [:name :organizer :city :country]]
    (if (and (every? #(not (str/blank? (get params %))) required)
             (valid-email? (:contact params)))
      (do (swap! submissions conj (select-keys params [:name :organizer :city :country
                                                       :frequency :contact :link :description]))
          (println "New meetup submission:" (:name params))
          (redirect "/?status=submitted#add-meetup"))
      (redirect "/?status=bad-meetup#add-meetup"))))

(defn- static-file [path content-type]
  (if-let [file (io/resource (str "public" path))]
    {:status 200 :headers {"Content-Type" content-type} :body (slurp file)}
    (html-response 404 "Not found")))

;; ---------------------------------------------------------------------------
;; Routing

(defn handler [{:keys [request-method uri] :as req}]
  (let [meetup-slug (second (re-matches #"/meetups/([a-z0-9-]+)/?" uri))]
    (cond
      (and (= request-method :get) (= uri "/"))                 (home req)
      (and (= request-method :get) (= uri "/style.css"))        (static-file uri "text/css; charset=utf-8")
      (and (= request-method :post) (= uri "/subscribe"))       (subscribe req)
      (and (= request-method :post) (= uri "/meetups/submit"))  (submit-meetup req)
      (and (= request-method :get) meetup-slug)                 (meetup req meetup-slug)
      :else (html-response 404 (views/not-found-page (page-opts req))))))

(defn -main [& _args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "8080"))]
    (println (str "Server running on http://localhost:" port))
    (http/run-server #'handler {:port port})))
