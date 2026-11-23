(ns app.core
  (:require [app.mailchimp :as mailchimp]
            [app.routes :as routes]
            [app.sheet :as sheet]
            [org.httpkit.server :as http])
  (:gen-class))

(defonce ^:private server (atom nil))

(defn stop! []
  (when-let [stop @server]
    (stop :timeout 100)
    (reset! server nil)))

(defn start! [{:keys [port]}]
  (stop!)
  (reset! server (http/run-server #'routes/app {:port port}))
  (println (str "Listening on http://localhost:" port)))

(defn -main [& _]
  (when-not (mailchimp/config)
    (println "MAILCHIMP_API_KEY / MAILCHIMP_AUDIENCE_ID not set, newsletter sign-ups stay in memory"))
  (when-not (sheet/config)
    (println "APPLICATIONS_URL / APPLICATIONS_SECRET not set, applications stay in memory"))
  (start! {:port (parse-long (or (System/getenv "PORT") "8080"))}))

(comment
  (start! {:port 8080})
  (stop!))
