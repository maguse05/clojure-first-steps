(ns app.countries
  (:require [clojure.string :as str])
  (:import (java.text Collator Normalizer Normalizer$Form)
           (java.util Locale Locale$Builder)))

(defn normalize [s]
  (-> (Normalizer/normalize (str s) Normalizer$Form/NFD)
      (str/replace #"\p{M}" "")
      (str/lower-case)
      (str/trim)))

(defn- display-name [iso-code]
  (-> (Locale$Builder.)
      (.setRegion iso-code)
      (.build)
      (.getDisplayCountry Locale/ENGLISH)))

(def names
  (->> (Locale/getISOCountries)
       (map display-name)
       (sort (Collator/getInstance Locale/ENGLISH))
       (vec)))

(def aliases
  {"usa"                      "United States"
   "us"                       "United States"
   "united states of america" "United States"
   "uk"                       "United Kingdom"
   "england"                  "United Kingdom"
   "great britain"            "United Kingdom"
   "czech republic"           "Czechia"
   "brasil"                   "Brazil"})

(def ^:private by-normalized-name
  (merge (zipmap (map normalize names) names) aliases))

(defn canonical [s]
  (by-normalized-name (normalize s)))

(def ^:private codes
  (into {} (map (juxt display-name identity)) (Locale/getISOCountries)))

(defn code
  "The two-letter ISO code of a country, by name or alias."
  [s]
  (codes (canonical s)))

(defn by-code
  "The country for a two-letter ISO code, like `BR`."
  [s]
  (let [code (str/upper-case (str/trim (str s)))]
    (when (some #{code} (Locale/getISOCountries))
      (display-name code))))

(defn matches?
  "An exact country (or alias) matches that country only, anything else is a
  substring match, so `us` doesn't bring up Australia and Belarus."
  [query country]
  (let [q (normalize query)]
    (cond
      (str/blank? q) true
      (canonical q)  (= country (canonical q))
      :else          (str/includes? (normalize country) q))))
