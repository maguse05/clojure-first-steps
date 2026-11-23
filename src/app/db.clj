(ns app.db
  (:require [app.meetups :as meetups]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- read-resource [path read]
  (some-> (io/resource path) slurp read))

(defn- read-json [s]
  (json/read-str s :key-fn #(keyword (str/replace % \_ \-))))

(defn meetups []
  (meetups/listed (:meetups (read-resource "meetups-sheet.json" read-json))
                  (read-resource "meetups.edn" edn/read-string)))

(defn meetup [slug]
  (some #(when (= slug (:slug %)) %) (meetups)))

;; In memory until we pick a real backend for these.
(defonce applications (atom []))
(defonce subscribers (atom #{}))
