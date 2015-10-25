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
  (client/get url {:throw-exceptions false
                   :as :json
                   :coerce :always}))

(defn http-post [url body]
  (client/post url {:throw-exceptions false
                    :as :json
                    :coerce :always
                    :form-params body
                    :content-type :json}))

(background
  (around :checks
          (let [server (start-server)]
            ?form
            (stop-server server))))

(facts "Auth App"
  (facts "main route returns application-name"
    (http-get base-url)
    => (contains {:status 200 :body {:application-name "auth"}}))

  (facts "not-found route"
    (http-get (str base-url "/invalid"))
    => (contains {:status 404}))

  (facts "Sign-Up returns 400 Bad Request if params are not specified"
    (http-post (str base-url "/auth/signup") {})
    => (contains {:status 400 :body {:name "missing-required-key"
                                     :email "missing-required-key"
                                     :password "missing-required-key"}}))

  (facts "Sign-Up and GET token"
    (http-post (str base-url "/auth/signup") {:email "colin@mailinator.com"
                                              :password "password1"
                                              :name "Colin"})
    ; todo - check more things in assertion
    => (contains {:status 200 :body {:account {:name "Colin"
                                               :email "colin@mailinator.com"}}}))

;  (facts "GET token with unknown token returns 404"
;    (http-get (str base-url "/auth/tokens/invalid-token"))
;    => (contains {:status 404}))
)

