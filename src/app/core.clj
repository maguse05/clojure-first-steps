(ns app.core
  (:require [app.routes :as routes]
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
  (start! {:port (parse-long (or (System/getenv "PORT") "8080"))}))

(comment
  (start! {:port 8080})
  (stop!))
