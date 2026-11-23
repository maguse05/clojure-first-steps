(ns app.countries-test
  (:require [app.countries :as countries]
            [clojure.test :refer [are deftest is]]))

(deftest canonical-test
  (are [input expected] (= expected (countries/canonical input))
    "brazil"         "Brazil"
    " Brasil "       "Brazil"
    "USA"            "United States"
    "czech republic" "Czechia"
    "Perú"           "Peru"
    "Narnia"         nil))

(deftest matches-test
  (is (countries/matches? "" "Brazil"))
  (is (countries/matches? nil "Brazil"))
  (is (countries/matches? "bra" "Brazil"))
  (is (countries/matches? "us" "United States"))
  (is (not (countries/matches? "us" "Australia")) "an exact alias doesn't fall back to substring matching")
  (is (not (countries/matches? "Peru" "Brazil"))))
