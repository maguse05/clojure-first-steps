(ns app.core
  (:require [org.httpkit.server :as http])
  (:gen-class))

(defn html-response [status body]
  {:status status
  :headers {"Content-Type" "text/html"}
  :body body})

(defn handler [_req]
  (html-response 200 "<h1 style='color: red;'>Hi, I'm Magda</h1>
<p>Welcome to my Clojure website</p>"))

(defn -main [& _args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "8080"))]
    (println (str "Server running on port " port))
    (http/run-server handler {:port port})))
