(ns app.mailchimp
  "Newsletter sign-ups go to a Mailchimp audience as `pending`, so Mailchimp
  sends the double opt-in email itself. Applicants are added as
  `transactional` contacts: they're in the audience, tagged, but not signed up
  to the newsletter."
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [org.httpkit.client :as http])
  (:import (java.security MessageDigest)))

(def newsletter-tag "Clojure Meetups website")
(def applicant-tag "Meetup applicant")

(defn config
  "Reads MAILCHIMP_API_KEY and MAILCHIMP_AUDIENCE_ID. The API key ends in the
  account's data center (`...-us21`), which is also the API host."
  ([] (config (System/getenv)))
  ([env]
   (let [api-key  (get env "MAILCHIMP_API_KEY")
         audience (get env "MAILCHIMP_AUDIENCE_ID")]
     (when-not (some str/blank? [api-key audience])
       {:api-key  api-key
        :audience audience
        :api-url  (str "https://" (peek (str/split api-key #"-")) ".api.mailchimp.com/3.0")}))))

(defn- member-path
  "Mailchimp identifies a member by the MD5 of their lower-cased email."
  [audience email]
  (let [digest (.digest (MessageDigest/getInstance "MD5")
                        (.getBytes (str/lower-case email) "UTF-8"))]
    (str "/lists/" audience "/members/" (format "%032x" (BigInteger. 1 digest)))))

(def ^:private any-merge-fields
  "The audience is shared and may require merge fields the site doesn't ask
  for, like a birthday. Mailchimp rejects the member unless told to skip that."
  {:skip_merge_validation true})

(defn- request [{:keys [api-key api-url]} method path body & [query]]
  @(http/request {:method       method
                  :url          (str api-url path)
                  :query-params query
                  :basic-auth   ["clojure-meetups" api-key]
                  :headers      {"Content-Type" "application/json"}
                  :body         (json/write-str body)
                  :timeout      5000}))

(defn- ok? [{:keys [status]}]
  (and status (<= 200 status 299)))

(defn- error-title [{:keys [body]}]
  (try
    (get (json/read-str body) "title")
    (catch Exception _ nil)))

(defn- failed [what {:keys [status body error]}]
  (println "Mailchimp" what "failed:" (or (some-> error ex-message) status) body)
  :failed)

(defn subscribe!
  "Returns :subscribed, :already-subscribed or :failed."
  [{:keys [audience] :as cfg} email]
  (let [response (request cfg :post (str "/lists/" audience "/members")
                          {:email_address email
                           :status        "pending"
                           :tags          [newsletter-tag]}
                          any-merge-fields)]
    (cond
      (ok? response)                             :subscribed
      (= "Member Exists" (error-title response)) :already-subscribed
      :else                                      (failed "sign-up" response))))

(defn add-applicant!
  "Adds the applicant to the audience, or updates them if they're already in
  it, without changing their newsletter status. Returns :tagged or :failed."
  [{:keys [audience] :as cfg} {:keys [email full-name]}]
  (let [member                 (member-path audience email)
        [first-name last-name] (str/split (str/trim full-name) #"\s+" 2)
        upsert                 (request cfg :put member
                                        {:email_address email
                                         :status_if_new "transactional"
                                         :merge_fields  {:FNAME first-name
                                                         :LNAME (or last-name "")}}
                                        any-merge-fields)]
    (if-not (ok? upsert)
      (failed "adding an applicant" upsert)
      (let [tagged (request cfg :post (str member "/tags")
                            {:tags [{:name applicant-tag :status "active"}]})]
        (if (ok? tagged)
          :tagged
          (failed "tagging an applicant" tagged))))))
