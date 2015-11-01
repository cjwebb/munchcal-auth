(ns auth.handler-test
  (:require [midje.sweet :refer :all]
            [clj-http.client :as client]
            [ring.adapter.jetty :refer [run-jetty]]
            [auth.handler :refer :all]
            [auth.db :as db]
            [auth.generators :refer [random-email]]))

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

(defn- sign-up! [data]
  (http-post (str base-url "/auth/signup") data))

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
    (sign-up! {})
    => (contains {:status 400 :body {:name "missing-required-key"
                                     :email "missing-required-key"
                                     :password "missing-required-key"}}))

  (facts "Sign-Up returns account and token info"
    (let [email (random-email)
          response (sign-up! {:email email :password "pw1" :name "colin"})
          account (get-in response [:body :account])
          token (get-in response [:body :token])]
      (response :status) => 200
      account => (contains {:name "colin"
                            :email email
                            :id anything})
      token => (contains {:id anything})))

  (facts "Sign-Up with same email address returns error"
    (let [email (random-email)
          response1 (sign-up! {:email email :password "pw1" :name "colin"})
          response2 (sign-up! {:email email :password "pw1" :name "colin"})]
      (response1 :status) => 200
      (response2 :status) => 400
      (response2 :body) => {:error {:email "already-in-use"}}))

  (facts "Sign-Up and GET token"
    (let [email (random-email)
          signup-response (sign-up! {:email email :password "pw1" :name "colin"})
          token-id (get-in signup-response [:body :token :id])
          token-response (http-get (str base-url "/auth/tokens/" token-id))
          account (get-in token-response [:body :account])]
      (token-response :status) => 200
      account => (contains {:name "colin"
                            :email email
                            :id anything})))

  (facts "GET token with unknown token returns 404"
    (http-get (str base-url "/auth/tokens/invalid-token"))
    => (contains {:status 404})))

