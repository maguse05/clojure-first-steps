(ns app.sheet-test
  (:require [app.application :as application]
            [app.sheet :as sheet]
            [clojure.data.json :as json]
            [clojure.test :refer [deftest is testing]]
            [org.httpkit.client :as http]))

(def ^:private cfg
  (sheet/config {"APPLICATIONS_URL"    "https://script.google.com/macros/s/abc/exec"
                 "APPLICATIONS_SECRET" "s3cret"}))

(def ^:private values
  (application/parse {"full-name"      "Ana Silva"
                      "email"          "ana@example.com"
                      "meetup-name"    "Clojure Lima"
                      "city"           "Lima"
                      "area"           "Miraflores"
                      "channels"       ["Clojurians Slack channel" "Other"]
                      "channels-other" "WhatsApp group"
                      "experience"     "3"
                      "commitment"     application/commitments}))

(deftest config-test
  (is (= {:url "https://script.google.com/macros/s/abc/exec" :secret "s3cret"} cfg))
  (is (nil? (sheet/config {"APPLICATIONS_URL" "https://script.google.com/macros/s/abc/exec"}))))

(deftest answers-test
  (let [answers (sheet/answers values)]
    (is (= "Ana Silva" (answers "Full Name")))
    (is (= "Clojure Lima" (answers "Name of the Clojure Meetup?")))
    (is (= "Clojurians Slack channel, WhatsApp group"
           (answers "Which communication channels do you plan to use for ongoing communication with meetup members (beyond promotion)?")))
    (is (= (str (first application/commitments) ", " (second application/commitments))
           (answers "Commitment and Acknowledgment")))
    (is (= "Lima (Miraflores)" (answers "Where do you plan to hold the meetup?")))
    (is (= "Lima" (get (sheet/answers (dissoc values :area)) "Where do you plan to hold the meetup?"))
        "without an area, just the city")
    (is (not (contains? answers "Discord user")) "blank answers are left out")
    (is (every? (set (vals sheet/columns)) (keys answers)))))

(deftest every-field-has-a-column
  (is (= (set (map :id application/fields))
         (-> (set (keys sheet/columns)) (disj :location) (conj :channels-other :city :area)))
      ":channels-other goes into the channels column, city and area into the location one"))

(deftest append-test
  (testing "the redirect Apps Script answers with is followed with a GET"
    (let [posted (atom nil)]
      (with-redefs [http/post (fn [url opts]
                                (reset! posted [url (json/read-str (:body opts))])
                                (delay {:status 302 :headers {:location "https://script.googleusercontent.com/echo"}}))
                    http/get  (fn [url _]
                                (delay (if (= "https://script.googleusercontent.com/echo" url)
                                         {:status 200 :body "{\"ok\":true}"}
                                         {:status 404})))]
        (is (= :saved (sheet/append! cfg values))))
      (let [[url body] @posted]
        (is (= "https://script.google.com/macros/s/abc/exec" url))
        (is (= "s3cret" (body "secret")))
        (is (= "Ana Silva" (get-in body ["answers" "Full Name"]))))))

  (testing "a wrong secret, a sign-in page or an error is a failure"
    (doseq [response [{:status 200 :body "{\"ok\":false,\"error\":\"Wrong secret\"}"}
                      {:status 200 :body "<html>Sign in</html>"}
                      {:error (ex-info "timeout" {})}]]
      (with-redefs [http/post (fn [_ _] (delay response))]
        (with-out-str
          (is (= :failed (sheet/append! cfg values))))))))
