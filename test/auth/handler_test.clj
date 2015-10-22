(ns auth.handler-test
  (:require [midje.sweet :refer :all]
            [clj-http.client :as client]
            [ring.adapter.jetty :refer [run-jetty]]
            [auth.handler :refer :all]))

(def test-port 10035)
(def base-url (str "http://localhost:" test-port))

(defn start-server []
  (loop [server (run-jetty app {:port test-port
                                :join? false})]
    (if (.isStarted server)
      server
      (recur server))))

(defn stop-server [server]
  (.stop server))

(defn http-get [url]
  (client/get url {:throw-exceptions false :as :json}))

(facts "Auth App"
  (facts "main route returns application-name"
    (let [server (start-server)]
      (http-get base-url)
      => (contains {:status 200 :body {:application-name "auth"}})
      (stop-server server)))

  (facts "not-found route"
    (let [server (start-server)]
      (http-get (str base-url "/invalid"))
      => (contains {:status 404})
      (stop-server server))))

