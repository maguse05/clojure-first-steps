(ns app.sheet
  "Applications go to the Pilot Program responses spreadsheet, through the web
  app in apps-script/sync-meetups.gs. They land in the same columns the Google
  Form filled before it was closed."
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [org.httpkit.client :as http]))

(def columns
  "Form field -> header of its column in the spreadsheet. The web app also
  takes the start of a header, so the location goes to \"Where do you plan to
  hold the meetup? (City and area)\" whatever the parentheses say."
  {:full-name           "Full Name"
   :email               "Email address"
   :nubank-employee     "Nubank employee?"
   :github              "GitHub profile link"
   :slack               "Clojurians Slack user"
   :discord             "Discord user"
   :linkedin            "LinkedIn profile link"
   :meetup-name         "Name of the Clojure Meetup?"
   :frequency-other     "If 'Other' in the previous question, please specify your planned frequency."
   :format              "Meetup format"
   :country             "Country"
   :location            "Where do you plan to hold the meetup?"
   :expected-attendance "How many people do you anticipate attending a typical meetup?"
   :description         "Briefly describe your proposed meetup format (e.g., talks, workshops, coding sessions)."
   :website-description "Website description"
   :promotion           "How and where do you plan to promote the meetup locally to attract new members?"
   :channels            "Which communication channels do you plan to use for ongoing communication with meetup members (beyond promotion)?"
   :monthly-support     "Expected monthly support"
   :budget-usd          "Budget in USD"
   :frequency           "How frequently do you plan to hold the meetup?"
   :budget-breakdown    "Provide a breakdown of how the requested monthly funds will be used (e.g., subscriptions, venue, food/drinks, speaker travel, promotion)."
   :experience          "On a scale of 1 to 5, how experienced are you in organizing technical community events or meetups?"
   :commitment          "Commitment and Acknowledgment"})

(defn config
  ([] (config (System/getenv)))
  ([env]
   (let [url    (get env "APPLICATIONS_URL")
         secret (get env "APPLICATIONS_SECRET")]
     (when-not (some str/blank? [url secret])
       {:url url :secret secret}))))

(defn- location [{:keys [city area]}]
  (if (str/blank? area)
    city
    (str city " (" area ")")))

(defn answers
  "The application as headers -> cell values. Like Google Forms, checkboxes
  are joined with commas and the text typed for \"Other\" takes its place.
  City and area share a column, as \"City (area)\"."
  [{:keys [channels channels-other] :as values}]
  (let [values (assoc values
                      :channels (replace {"Other" channels-other} channels)
                      :location (location values))]
    (into {}
          (for [[field header] columns
                :let [v (get values field)
                      v (if (sequential? v) (str/join ", " (remove str/blank? v)) v)]
                :when (not (str/blank? v))]
            [header v]))))

(defn- post
  "Apps Script answers a POST with a redirect to where its response is. Follow
  it with a plain GET, which is what Google expects."
  [url body]
  (let [{:keys [status headers] :as response}
        @(http/post url {:headers          {"Content-Type" "application/json"}
                         :body             (json/write-str body)
                         :follow-redirects false
                         :timeout          20000})]
    (if (#{301 302 303} status)
      @(http/get (:location headers) {:timeout 20000})
      response)))

(defn- saved? [{:keys [status body]}]
  (and (= 200 status)
       (true? (get (try (json/read-str body) (catch Exception _ nil)) "ok"))))

(defn append!
  "Returns :saved or :failed."
  [{:keys [url secret]} values]
  (let [{:keys [status body error] :as response} (post url {:secret secret :answers (answers values)})]
    (if (saved? response)
      :saved
      (do (println "Saving an application to the sheet failed:" (or (some-> error ex-message) status) body)
          :failed))))
