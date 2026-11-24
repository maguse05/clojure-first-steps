(ns app.meetups
  "The site lists the meetups approved in the applications sheet, as exported
  by apps-script/sync-meetups.gs, with the details from meetups.edn on top."
  (:require [app.countries :as countries]
            [clojure.string :as str])
  (:import (java.net URI)
           (java.time LocalDate)))

(def ^:private formats
  {"in-person" :in-person
   "hybrid"    :hybrid
   "remote"    :online
   "online"    :online})

(def ^:private link-labels
  {"meetup.com"  "Meetup.com"
   "luma.com"    "Luma"
   "lu.ma"       "Luma"
   "heylo.com"   "Heylo"
   "discord.com" "Discord"
   "discord.gg"  "Discord"
   "youtube.com" "YouTube"
   "youtu.be"    "YouTube"
   "github.com"  "GitHub"})

(defn slugify [s]
  (-> (countries/normalize s)
      (str/replace #"[^a-z0-9]+" "-")
      (str/replace #"^-|-$" "")))

(defn- link [url]
  (let [host (some-> (try (.getHost (URI. url)) (catch Exception _ nil))
                     (str/replace #"^www\." ""))]
    {:label (link-labels host (or host url))
     :url   url}))

(defn- past-meetup
  "A link keeps the title it was given in Drive or the report, if any."
  [{:keys [date title photos links]}]
  {:date   date
   :title  title
   :photos (vec photos)
   :links  (mapv (fn [{:keys [title url]}]
                   (cond-> (link url) title (assoc :label title)))
                 links)})

(defn- from-sheet
  [{:keys [id name organizer country city format frequency description links next-meetup past-meetups lat lng]}]
  (cond-> {:applied-at   id
           :name         name
           :organizer    organizer
           :country      (or (countries/canonical country) (countries/by-code country) country)
           :city         city
           :format       (formats (str/lower-case (str format)) :in-person)
           :frequency    frequency
           :description  (not-empty description)
           :links        (mapv link links)
           :past-meetups (mapv past-meetup past-meetups)}
    next-meetup (assoc :next-event {:date next-meetup})
    lat         (assoc :lat lat :lng lng)))

(defn- next-event
  "The soonest event still to come, out of the curated one and the date
  reported in the sheet. On the same day the curated one wins, as it usually
  has a time and a venue too."
  [curated reported ^LocalDate today]
  (or (->> [curated reported]
           (filter #(and % (not (.isBefore (LocalDate/parse (:date %)) today))))
           (sort-by :date)
           (first))
      curated
      reported))

(defn- tbc? [s]
  (or (str/blank? s) (= "tbc" (str/lower-case (str/trim s)))))

(defn- with-details
  "Names, descriptions and links always come from the sheet. Until the final
  name is set, the meetup goes by its city."
  [row details today]
  (let [{:keys [name city organizer] :as meetup}
        (merge row (dissoc details :name :description :links :next-event))
        name (if (tbc? name) (str city " Meetup") name)]
    (assoc meetup
           :name       name
           :slug       (or (:slug details)
                           (some not-empty [(slugify name) (slugify organizer) "meetup"]))
           :next-event (next-event (:next-event details) (:next-event row) today))))

(defn- unique-slugs [meetups]
  (first
   (reduce (fn [[acc taken] {:keys [slug] :as meetup}]
             (let [slug (->> (iterate inc 2)
                             (map #(str slug "-" %))
                             (cons slug)
                             (remove taken)
                             (first))]
               [(conj acc (assoc meetup :slug slug)) (conj taken slug)]))
           [[] #{}]
           meetups)))

(defn listed
  "`rows` are the approved meetups from the sheet, `details` the entries in
  meetups.edn, matched on :applied-at. A meetup without details is still
  listed, with what the sheet says about it."
  ([rows details]
   (listed rows details (LocalDate/now)))
  ([rows details today]
   (let [details-for (into {} (map (juxt :applied-at identity)) details)]
     (->> rows
          (map from-sheet)
          (map #(with-details % (details-for (:applied-at %)) today))
          (unique-slugs)))))
