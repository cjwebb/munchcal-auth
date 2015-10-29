(ns auth.handler-test
  (:require [midje.sweet :refer :all]
            [clj-http.client :as client]
            [ring.adapter.jetty :refer [run-jetty]]
            [auth.handler :refer :all]
            [auth.db :as db]))

(def test-port 10035)
(def base-url (str "http://localhost:" test-port))

(def server (atom nil))

(defn start-server []
  (swap! server (fn [_] (run-jetty app {:port test-port :join? false}))))

(defn stop-server []
  (.stop @server))

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

(against-background [(before :contents (db/init!))
                     (before :contents (start-server))
                     (after :contents (stop-server))]

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

  (facts "Sign-Up returns account and token info"
    (let [response (http-post (str base-url "/auth/signup")
                              {:email "colin1@mailinator.com"
                               :password "password1"
                               :name "Colin1"})
          account (get-in response [:body :account])
          token (get-in response [:body :token])]
      (response :status) => 200
      account => (contains {:name "Colin1"
                            :email "colin1@mailinator.com"
                            :id anything})
      token => (contains {:id anything})))

  (facts "Sign-Up and GET token"
    (let [signup-response (http-post (str base-url "/auth/signup")
                                     {:email "colin@mailinator.com"
                                      :password "password1"
                                      :name "Colin"})
          token-id (get-in signup-response [:body :token :id])
          token-response (http-get (str base-url "/auth/tokens/" token-id))
          account (get-in token-response [:body :account])]
      (token-response :status) => 200
      account => (contains {:name "Colin"
                            :email "colin@mailinator.com"
                            :id anything})))

  (facts "GET token with unknown token returns 404"
    (http-get (str base-url "/auth/tokens/invalid-token"))
    => (contains {:status 404})))

