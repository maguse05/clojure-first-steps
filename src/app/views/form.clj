(ns app.views.form
  (:require [app.application :as application]
            [app.views.layout :as layout]
            [clojure.string :as str]))

(defn- dom-id [id & suffix]
  (apply str "apply-" (name id) suffix))

(defn- choice-field? [{:keys [type]}]
  (contains? #{:radio :checkboxes} type))

(defmulti ^:private input (fn [field _value _attrs] (:type field :text)))

(defmethod input :default
  [{:keys [id type required? placeholder autocomplete datalist attrs show-when]} value aria]
  [:input (merge {:id           (dom-id id)
                  :name         (name id)
                  :type         (name (or type :text))
                  :value        value
                  :required     (and required? (not show-when))
                  :placeholder  placeholder
                  :autocomplete autocomplete
                  :list         datalist}
                 attrs
                 aria)])

(defmethod input :textarea [{:keys [id required? placeholder]} value aria]
  [:textarea (merge {:id (dom-id id) :name (name id) :rows 4
                     :required required? :placeholder placeholder}
                    aria)
   value])

(defmethod input :select [{:keys [id options required?]} value aria]
  [:select (merge {:id (dom-id id) :name (name id) :required required?} aria)
   [:option {:value ""} "Choose one"]
   (for [option options]
     [:option {:value option :selected (= value option)} option])])

(defmethod input :radio [{:keys [id options required? scale]} value aria]
  [:div {:class (cond-> "choices" scale (str " choices--scale"))}
   (when scale [:span.choices__end (first scale)])
   (for [option options]
     [:label.choice
      [:input (merge {:type "radio" :name (name id) :value option
                      :required required? :checked (= value option)}
                     aria)]
      [:span option]])
   (when scale [:span.choices__end (last scale)])])

(defmethod input :checkboxes [{:keys [id options required?]} value aria]
  (let [checked (set value)]
    [:div.choices.choices--stacked
     (for [option options]
       [:label.choice
        [:input (merge {:type "checkbox" :name (name id) :value option
                        :required (= :all required?) :checked (contains? checked option)}
                       aria)]
        [:span option]])]))

(defn- field [{:keys [id label hint required? wide? show-when] :as f} values errors autofocus]
  (let [error    (get errors id)
        choice?  (choice-field? f)
        aria     {:aria-invalid     (when error "true")
                  :aria-describedby (some->> [(when hint (dom-id id "-hint"))
                                              (when error (dom-id id "-error"))]
                                             (remove nil?)
                                             (seq)
                                             (str/join " "))
                  :autofocus        (and (= id autofocus) (not choice?))}]
    [(if choice? :fieldset.field :div.field)
     {:class           (when wide? "field--wide")
      :hidden          (not (application/shown? f values))
      :data-show-field (some-> show-when first name)
      :data-show-value (second show-when)}
     [(if choice? :legend.field__label :label.field__label)
      (when-not choice? {:for (dom-id id)})
      label
      (when-not required? [:span.optional " (optional)"])]
     (when hint [:p.field__hint {:id (dom-id id "-hint")} hint])
     (input f (get values id) aria)
     (when error [:p.field__error {:id (dom-id id "-error")} error])]))

(defn application-form [{:keys [values errors submitted?]}]
  (let [autofocus (some (comp (set (keys errors)) :id) application/fields)]
    [:form.card.application-form {:method "post" :action "/apply#apply"}
     (cond
       submitted?    (layout/notice :success "Thanks for applying! We'll review your proposal and get back to you by email.")
       (seq errors)  [:p.notice.notice--error {:role "alert"} "Some answers need another look, see the fields marked below."])
     (for [{:keys [title fields]} application/sections]
       [:fieldset.form-section
        [:legend.form-section__title title]
        [:div.form-grid
         (for [f fields]
           (field f values errors autofocus))]])
     [:button.button {:type "submit"} "Submit application"]]))
