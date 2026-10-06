(ns app.core
  (:require [org.httpkit.server :as http])
  (:gen-class))

(defn handler [_req]
  {:status  200
   :headers {"Content-Type" "text/plain"}
   :body    "Hello, World!"})

(defn -main [& _args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "8080"))]
    (println (str "Server running on port " port))
    (http/run-server handler {:port port})))
