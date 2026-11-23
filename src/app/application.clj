(ns app.application
  "The Clojure Meetup Pilot Program application form, described as data so
  the view and the validation share a single source of truth."
  (:require [clojure.string :as str]))

(def frequency-options
  ["Weekly" "Bi-weekly" "Monthly" "Bi-monthly" "Quarterly" "Other"])

(def channel-options
  ["Dedicated Meetup/Eventbrite page"
   "Clojurians Slack channel"
   "Discord server"
   "Social media (e.g., Reddit, LinkedIn)"
   "Mailing list/Newsletter"
   "Other"])

(def commitments
  ["If selected, I'm committed to providing the Clojure community team with the necessary meetup reports to track the initiative's success and ensure program sustainability."
   "I understand that this financial support does not imply employment or long-term financial commitment."])

(def sections
  [{:title  "About you"
    :fields [{:id :full-name :label "Full name" :required? true :autocomplete "name"}
             {:id :email :label "Email address" :type :email :required? true :autocomplete "email"}
             {:id :nubank-employee :label "Nubank employee?" :type :radio :options ["Yes" "No"] :required? true}
             {:id :github :label "GitHub profile link" :type :url :placeholder "https://github.com/your-user"}
             {:id :slack :label "Clojurians Slack user"}
             {:id :discord :label "Discord user"}
             {:id :linkedin :label "LinkedIn profile link" :type :url :placeholder "https://www.linkedin.com/in/your-user"}]}

   {:title  "Your meetup"
    :fields [{:id :meetup-name :label "Name of the Clojure meetup" :required? true}
             {:id :country :label "Meetup country" :required? true :datalist "countries"}
             {:id :city :label "Meetup city" :required? true :datalist "cities" :autocomplete "off"
              :hint "Choose the country first to see its cities. If yours isn't listed, type it in."}
             {:id :area :label "Meetup area" :hint "Neighborhood, campus or venue, if you know it already"}
             {:id :format :label "Meetup format" :type :radio :options ["In-person" "Hybrid" "Online"] :required? true}
             {:id :frequency :label "How frequently do you plan to hold the meetup?" :type :select
              :options frequency-options :required? true}
             {:id :frequency-other :label "Please specify your planned frequency" :required? true
              :show-when [:frequency "Other"]}
             {:id :expected-attendance :label "How many people do you anticipate attending a typical meetup?"
              :placeholder "e.g. 10-20"}
             {:id :description :label "Briefly describe your proposed meetup format" :type :textarea
              :hint "e.g. talks, workshops, coding sessions" :required? true :wide? true}
             {:id :promotion :label "How and where do you plan to promote the meetup locally to attract new members?"
              :type :textarea :wide? true}
             {:id :channels :label "Which communication channels do you plan to use for ongoing communication with meetup members (beyond promotion)?"
              :type :checkboxes :options channel-options :wide? true}
             {:id :channels-other :label "Which other channels?" :required? true :wide? true
              :show-when [:channels "Other"]}]}

   {:title  "Support"
    :fields [{:id :monthly-support :label "Expected monthly support" :placeholder "e.g. $150-$250/month"}
             {:id :budget-usd :label "Budget in USD" :type :number :attrs {:min 0 :step "any"}}
             {:id :budget-breakdown :label "Provide a breakdown of how the requested monthly funds will be used"
              :hint "e.g. subscriptions, venue, food/drinks, speaker travel, promotion" :type :textarea :wide? true}
             {:id :experience :label "On a scale of 1 to 5, how experienced are you in organizing technical community events or meetups?"
              :type :radio :options ["1" "2" "3" "4" "5"] :scale ["Not at all" "Very"] :required? true :wide? true}]}

   {:title  "Commitment and acknowledgment"
    :fields [{:id :commitment :label "Please confirm" :type :checkboxes :options commitments
              :required? :all :wide? true}]}])

(def fields
  (into [] (mapcat :fields) sections))

(def ^:private email-re #"[^\s@]+@[^\s@]+\.[^\s@]+")

(defn email? [s]
  (boolean (some->> s (re-matches email-re))))

(defn shown? [{[k v] :show-when} values]
  (or (nil? k)
      (let [x (get values k)]
        (if (coll? x) (contains? (set x) v) (= x v)))))

(defn- single [v]
  (if (sequential? v) (first v) v))

(defn parse
  "Picks the form's fields out of Ring's `:form-params`."
  [form-params]
  (into {}
        (for [{:keys [id type]} fields
              :let [v (get form-params (name id))]]
          [id (if (= :checkboxes type)
                (cond (sequential? v) (vec v) (some? v) [v] :else [])
                (some-> v single str/trim))])))

(defn- blank-value? [v]
  (if (coll? v) (empty? v) (str/blank? v)))

(defn- error [{:keys [type required? options]} v]
  (cond
    (blank-value? v)
    (when required? "This field is required.")

    (and (= :all required?) (not= (set options) (set v)))
    "Please confirm every statement."

    (and (= :email type) (not (email? v)))
    "Enter a valid email address."

    (and (= :url type) (not (re-matches #"https?://\S+" v)))
    "Enter the full link, starting with https://"

    (and (= :number type) (not (parse-double v)))
    "Enter a number."

    (and (#{:radio :select} type) (not (some #{v} options)))
    "Choose one of the options."

    (and (= :checkboxes type) (not (every? (set options) v)))
    "Choose from the listed options."))

(defn validate
  "Returns a map of field id -> error message; empty when the application is valid."
  [values]
  (into {}
        (keep (fn [{:keys [id] :as field}]
                (when (shown? field values)
                  (some->> (error field (get values id)) (vector id)))))
        fields))
