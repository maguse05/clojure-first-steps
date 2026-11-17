(ns app.db
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn meetups []
  (edn/read-string (slurp (io/resource "meetups.edn"))))

(defn meetup [slug]
  (some #(when (= slug (:slug %)) %) (meetups)))

;; In memory until we pick a real backend for these.
(defonce applications (atom []))
(defonce subscribers (atom #{}))
