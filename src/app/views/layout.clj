(ns app.views.layout
  (:require [hiccup2.core :as h]))

(def repo-url "https://github.com/maguse05/clojure-first-steps")

(def ^:private leaflet-cdn "https://unpkg.com/leaflet@1.9.4/dist")

(def ^:private fonts-url
  (str "https://fonts.googleapis.com/css2"
       "?family=Open+Sans:ital,wght@0,300;0,400;0,600;0,700;1,400"
       "&family=PT+Serif:ital@0;1"
       "&family=Source+Code+Pro:wght@500"
       "&display=swap"))

(def ^:private community-links
  [{:href  "https://clojurians.slack.com/app_redirect?channel=events"
    :title "#events on Clojurians Slack"
    :text  "Announce and discover Clojure events worldwide."
    :extra [:span "New to Slack? " [:a {:href "https://clojurians.net"} "Get an invite"]]}
   {:href  "https://www.linkedin.com/company/clojure-conj/"
    :title "Clojure/Conj on LinkedIn"
    :text  "News about the Clojure/Conj conference and the community."}])

(defn notice [kind & content]
  [:p {:class (str "notice notice--" (name kind)) :role "status"} content])

(defn- header []
  [:header.site-header
   [:div.container.site-header__inner
    [:a.brand {:href "/"}
     [:img.brand__logo {:src "/images/clojure-logo.png" :width 48 :height 48 :alt ""}]
     [:span.brand__text "Clojure " [:span.brand__highlight "Meetups"]]]
    [:nav
     [:a {:href "/#map"} "Map"]
     [:a {:href "/#meetups"} "Meetups"]
     [:a.button.button--small {:href "/#apply"} "Add your meetup"]]]])

(defn- newsletter [{:keys [uri flash]}]
  [:div.footer-card {:id "newsletter"}
   [:span.footer-card__title "Newsletter"]
   [:p "Get new meetups and events in your inbox. No spam."]
   (case flash
     :subscribed         (notice :success "Almost there! Check your inbox to confirm your subscription.")
     :already-subscribed (notice :success "You're already on the list. Thanks!")
     :subscribe-failed   (notice :error "We couldn't sign you up right now, please try again later.")
     :invalid-email      (notice :error "That email doesn't look right, please try again.")
     nil)
   [:form.newsletter {:method "post" :action "/subscribe"}
    [:input {:type "hidden" :name "back" :value uri}]
    [:label.visually-hidden {:for "newsletter-email"} "Email address"]
    [:input#newsletter-email {:type "email" :name "email" :required true
                              :placeholder "you@example.com" :autocomplete "email"}]
    [:button.button {:type "submit"} "Sign up"]]])

(defn- footer [opts]
  [:footer.site-footer
   [:div.container
    [:h2 "Connect with the community"]
    [:div.footer-grid
     (for [{:keys [href title text extra]} community-links]
       [:div.footer-card
        [:a.footer-card__title {:href href :target "_blank" :rel "noopener"} title " ↗"]
        [:p text]
        (when extra [:p.footer-card__hint extra])])
     (newsletter opts)]
    [:p.site-footer__small
     "A community site, not affiliated with " [:a {:href "https://clojure.org"} "clojure.org"] ". "
     [:a {:href repo-url} "Source on GitHub"]]]])

(defn page [{:keys [title description leaflet? scripts] :as opts} & content]
  (str
   "<!DOCTYPE html>"
   (h/html
    [:html {:lang "en"}
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:title (cond->> "Clojure Meetups" title (str title " · "))]
      [:meta {:name "description" :content (or description "Find a Clojure meetup near you, or start one.")}]
      [:link {:rel "icon" :type "image/png" :href "/images/favicon.png"}]
      [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
      [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin ""}]
      [:link {:rel "stylesheet" :href fonts-url}]
      (when leaflet?
        [:link {:rel "stylesheet" :href (str leaflet-cdn "/leaflet.css")}])
      [:link {:rel "stylesheet" :href "/style.css"}]
      [:script (h/raw "document.documentElement.classList.add('js')")]]
     [:body
      (header)
      [:main content]
      (footer opts)
      (when leaflet?
        [:script {:src (str leaflet-cdn "/leaflet.js")}])
      (for [src scripts]
        [:script {:src src}])]])))

(defn not-found [opts]
  (page (assoc opts :title "Not found")
        [:section.hero
         [:div.container
          [:h1 "Page not found"]
          [:p [:a {:href "/"} "Back to all meetups"]]]]))
