(ns app.application-test
  (:require [app.application :as application]
            [clojure.test :refer [deftest is testing]]))

(def ^:private valid-params
  {"full-name"       "Ana Silva"
   "email"           "ana@example.com"
   "nubank-employee" "No"
   "meetup-name"     "Clojure Lima"
   "country"         "Peru"
   "city"            "Lima"
   "format"          "Hybrid"
   "frequency"       "Monthly"
   "description"     "Talks and coding sessions"
   "channels"        "Clojurians Slack channel"
   "experience"      "3"
   "commitment"      application/commitments})

(deftest parse-test
  (let [values (application/parse (assoc valid-params "full-name" "  Ana Silva  "))]
    (is (= "Ana Silva" (:full-name values)))
    (is (= ["Clojurians Slack channel"] (:channels values)) "a single checkbox still parses as a vector")
    (is (= [] (:channels (application/parse (dissoc valid-params "channels")))))))

(deftest validate-test
  (is (= {} (application/validate (application/parse valid-params))))

  (testing "required fields"
    (is (= "This field is required."
           (:meetup-name (application/validate (application/parse (dissoc valid-params "meetup-name")))))))

  (testing "conditional fields are only required when shown"
    (let [errors (comp application/validate application/parse)]
      (is (nil? (:frequency-other (errors valid-params))))
      (is (some? (:frequency-other (errors (assoc valid-params "frequency" "Other")))))
      (is (nil? (:frequency-other (errors (assoc valid-params "frequency" "Other"
                                                 "frequency-other" "Every six weeks")))))))

  (testing "every commitment has to be confirmed"
    (is (some? (:commitment (application/validate
                             (application/parse (assoc valid-params "commitment" (first application/commitments))))))))

  (testing "values must come from the listed options"
    (let [errors (application/validate (application/parse (assoc valid-params
                                                                 "format" "Underwater"
                                                                 "email" "ana@"
                                                                 "budget-usd" "a lot")))]
      (is (= #{:format :email :budget-usd} (set (keys errors)))))))
