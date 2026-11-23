(ns app.mailchimp-test
  (:require [app.mailchimp :as mailchimp]
            [clojure.data.json :as json]
            [clojure.test :refer [deftest is testing]]
            [org.httpkit.client :as http]))

(def ^:private cfg
  (mailchimp/config {"MAILCHIMP_API_KEY"     "0123456789abcdef-us21"
                     "MAILCHIMP_AUDIENCE_ID" "a1b2c3"}))

(defn- respond-with
  "A stand-in for http/request that answers with `responses` in order, and the
  requests it got."
  [& responses]
  (let [requests  (atom [])
        responses (atom responses)]
    [requests (fn [req]
                (swap! requests conj (update req :body json/read-str))
                (let [[response] @responses]
                  (swap! responses rest)
                  (delay response)))]))

(defn- quietly [f]
  (let [result (volatile! nil)]
    (with-out-str (vreset! result (f)))
    @result))

(deftest config-test
  (is (= "https://us21.api.mailchimp.com/3.0" (:api-url cfg)))
  (is (nil? (mailchimp/config {"MAILCHIMP_API_KEY" "key-us21"})))
  (is (nil? (mailchimp/config {}))))

(deftest subscribe-test
  (testing "new members are added as pending, tagged with the site"
    (let [[requests request] (respond-with {:status 200 :body "{}"})]
      (with-redefs [http/request request]
        (is (= :subscribed (mailchimp/subscribe! cfg "ana@example.com"))))
      (let [[{:keys [method url body basic-auth]}] @requests]
        (is (= :post method))
        (is (= "https://us21.api.mailchimp.com/3.0/lists/a1b2c3/members" url))
        (is (= "0123456789abcdef-us21" (second basic-auth)))
        (is (= {"email_address" "ana@example.com"
                "status"        "pending"
                "tags"          ["Clojure Meetups website"]}
               body)))))

  (testing "existing members"
    (let [[_ request] (respond-with {:status 400 :body (json/write-str {:title "Member Exists"})})]
      (with-redefs [http/request request]
        (is (= :already-subscribed (mailchimp/subscribe! cfg "ana@example.com"))))))

  (testing "anything else is a failure"
    (doseq [response [{:status 400 :body (json/write-str {:title "Invalid Resource"})}
                      {:status 401 :body "{}"}
                      {:error (ex-info "timeout" {})}]]
      (let [[_ request] (respond-with response)]
        (with-redefs [http/request request]
          (is (= :failed (quietly #(mailchimp/subscribe! cfg "ana@example.com")))))))))

(deftest add-applicant-test
  (let [applicant {:email "Ana@Example.com" :full-name "Ana Maria Silva"}]
    (testing "the applicant is upserted without touching their status, then tagged"
      (let [[requests request] (respond-with {:status 200 :body "{}"} {:status 204 :body ""})]
        (with-redefs [http/request request]
          (is (= :tagged (mailchimp/add-applicant! cfg applicant))))
        (let [[upsert tag] @requests]
          (is (= :put (:method upsert)))
          (is (= "https://us21.api.mailchimp.com/3.0/lists/a1b2c3/members/cdb9d6a1dddc375a09cc83e3001598dc"
                 (:url upsert))
              "MD5 of the lower-cased email")
          (is (= {"email_address" "Ana@Example.com"
                  "status_if_new" "transactional"
                  "merge_fields"  {"FNAME" "Ana" "LNAME" "Maria Silva"}}
                 (:body upsert)))
          (is (= :post (:method tag)))
          (is (= (str (:url upsert) "/tags") (:url tag)))
          (is (= {"tags" [{"name" "Meetup applicant" "status" "active"}]} (:body tag))))))

    (testing "no tagging when the upsert fails"
      (let [[requests request] (respond-with {:status 400 :body "{}"})]
        (with-redefs [http/request request]
          (is (= :failed (quietly #(mailchimp/add-applicant! cfg applicant)))))
        (is (= 1 (count @requests)))))))
