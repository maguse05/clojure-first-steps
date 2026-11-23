(ns cities
  "Writes resources/public/cities/<country code>.json, the city suggestions
  for the application form, from a GeoNames dump:

    curl -O https://download.geonames.org/export/dump/cities1000.zip
    curl -O https://download.geonames.org/export/dump/admin1CodesASCII.txt
    unzip cities1000.zip
    clojure -M:cities cities1000.txt admin1CodesASCII.txt

  Cities go from most to least populated. A name used by more than one city
  of the same country gets its region, like \"Springfield, Illinois\"."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def out-dir "resources/public/cities")

(defn- tsv [file]
  (with-open [r (io/reader file)]
    (mapv #(str/split % #"\t" -1) (line-seq r))))

(defn- regions [file]
  (into {} (map (fn [[code name]] [code name])) (tsv file)))

(defn- city [regions [_ name _ _ _ _ _ _ country _ admin1 _ _ _ population]]
  {:name       name
   :country    country
   :region     (regions (str country "." admin1))
   :population (parse-long population)})

(defn- suggestions
  "One country's cities as the strings the form suggests."
  [cities]
  (let [shared? (->> cities (map :name) (frequencies) (keep (fn [[n k]] (when (> k 1) n))) (set))]
    (->> (sort-by :population > cities)
         (map (fn [{:keys [name region]}]
                (if (and (shared? name) region) (str name ", " region) name)))
         (distinct))))

(defn -main [cities-file regions-file]
  (let [regions (regions regions-file)
        by-code (group-by :country (map #(city regions %) (tsv cities-file)))]
    (.mkdirs (io/file out-dir))
    (doseq [[code cities] by-code]
      (spit (io/file out-dir (str code ".json"))
            (json/write-str (suggestions cities) :escape-unicode false :escape-slash false)))
    (println "Wrote" (count by-code) "countries," (count (mapcat val by-code)) "cities")))
