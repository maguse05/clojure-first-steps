(ns app.meetups-test
  (:require [app.meetups :as meetups]
            [clojure.test :refer [deftest is testing]])
  (:import (java.time LocalDate)))

(def ^:private today (LocalDate/parse "2026-11-23"))

(def ^:private row
  {:id          "2026-08-29T12:48:33"
   :name        "Clojure Chiang Mai"
   :organizer   "Fabian Merkle"
   :country     "Thailand"
   :city        "Chiang Mai Thailand"
   :format      "In-person"
   :frequency   "Weekly"
   :links       ["https://www.meetup.com/clojure-chiang-mai/events/"]
   :next-meetup "2026-12-01"
   :lat         18.7883
   :lng         98.9853})

(defn- listed [rows details]
  (meetups/listed rows details today))

(deftest from-the-sheet-alone
  (let [[m] (listed [row] [])]
    (is (= "clojure-chiang-mai" (:slug m)))
    (is (= :in-person (:format m)))
    (is (= [{:label "Meetup.com" :url "https://www.meetup.com/clojure-chiang-mai/events/"}] (:links m)))
    (is (= {:date "2026-12-01"} (:next-event m))))

  (testing "a TBC link, or none, still lists the meetup"
    (let [[m] (listed [(assoc row :links [] :next-meetup nil)] [])]
      (is (= "Clojure Chiang Mai" (:name m)))
      (is (= [] (:links m)))
      (is (nil? (:next-event m)))))

  (testing "sheet values are tidied up"
    (let [[m] (listed [(assoc row :country "BR" :format "Remote" :lat nil :lng nil)] [])]
      (is (= "Brazil" (:country m)))
      (is (= :online (:format m)))
      (is (not (contains? m :lat)) "no pin until the sheet has coordinates"))))

(deftest with-details
  (let [details {:applied-at "2026-08-29T12:48:33"
                 :slug       "chiang-mai"
                 :name       "Chiang Mai Clojurians"
                 :city       "Chiang Mai"
                 :links      [{:label "Discord" :url "https://discord.gg/x"}]}]
    (let [[m] (listed [row] [details])]
      (is (= "chiang-mai" (:slug m)))
      (is (= "Chiang Mai" (:city m)))
      (is (= "Clojure Chiang Mai" (:name m)) "the name comes from the sheet")
      (is (= ["Meetup.com"] (map :label (:links m))) "and so do the links"))

    (testing "a TBC name becomes the city's meetup"
      (is (= "Chiang Mai Meetup" (:name (first (listed [(assoc row :name "TBC")] [details])))))
      (let [[m] (listed [(assoc row :name "" :city "Lima")] [])]
        (is (= "Lima Meetup" (:name m)))
        (is (= "lima-meetup" (:slug m)))))

    (testing "details for another application don't apply"
      (is (= "Chiang Mai Thailand" (:city (first (listed [row] [(assoc details :applied-at "2026-01-01T00:00:00")]))))))

    (testing "details without a sheet row aren't listed"
      (is (empty? (listed [] [details]))))))

(deftest next-event
  (let [curated #(hash-map :applied-at (:id row) :next-event %)
        event   (fn [rows details] (:next-event (first (listed rows details))))]
    (is (= {:date "2026-12-01"} (event [row] [(curated {:date "2026-11-20" :time "18:00"})]))
        "a past curated event gives way to the reported date")
    (is (= {:date "2026-11-28" :time "19:00"} (event [row] [(curated {:date "2026-11-28" :time "19:00"})]))
        "the soonest upcoming one wins")
    (is (= {:date "2026-12-01" :time "19:00"} (event [row] [(curated {:date "2026-12-01" :time "19:00"})]))
        "on the same day, the curated one")
    (is (= {:date "2026-11-20"} (event [(assoc row :next-meetup nil)] [(curated {:date "2026-11-20"})]))
        "with nothing upcoming, whatever there is")))

(deftest slugs-are-unique
  (is (= ["clojure-chiang-mai" "clojure-chiang-mai-2" "clojure-chiang-mai-3"]
         (map :slug (listed [row (assoc row :id "b") (assoc row :id "c")] [])))))
