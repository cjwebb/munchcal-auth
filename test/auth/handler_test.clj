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

(defn- sign-up! [data] (http-post (str base-url "/auth/signup") data))
(defn- login! [data] (http-post (str base-url "/auth/login") data))
(defn- logout! [data] (http-post (str base-url "/auth/logout") data))
(defn- get-token! [token-id] (http-get (str base-url "/auth/tokens/" token-id)))

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
    (sign-up! {}) => (contains {:status 400 :body {:name "missing-required-key"
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
          sign-up-response (sign-up! {:email email :password "pw1" :name "colin"})
          token-id (get-in sign-up-response [:body :token :id])
          token-response (get-token! token-id)
          account (get-in token-response [:body :account])]
      (token-response :status) => 200
      account => (contains {:name "colin"
                            :email email
                            :id anything})))

  (facts "GET token with unknown token returns 404"
    (http-get (str base-url "/auth/tokens/invalid-token"))
    => (contains {:status 404}))

  (facts "Logout means that a following GET token returns 404"
    (let [email (random-email)
          sign-up-response (sign-up! {:email email :password "pw1" :name "colin"})
          token-id (get-in sign-up-response [:body :token :id])
          logout-response (logout! {:token {:id token-id}})
          token-response (get-token! token-id)]
      (logout-response :status) => 204
      (token-response :status) => 404))

  (facts "Logout with random token still returns 204"
    (let [response (logout! {:token {:id "random-token"}})]
      (:status response) => 204))

  (facts "Logout without a token returns 400 Bad Request"
    (logout! {}) => (contains {:status 400 :body {:token "missing-required-key"}}))

  (facts "Login returns 400 Bad Request if params not specified"
    (login! {}) => (contains {:status 400 :body {:email "missing-required-key"
                                                 :password "missing-required-key"}}))

  (facts "Login returns account, and creates new token"
    (let [email (random-email)
          sign-up-response (sign-up! {:email email :password "pw1" :name "colin"})
          sign-up-token-id (get-in sign-up-response [:body :token :id])
          sign-up-account (get-in sign-up-response [:body :account])
          login-response (login! {:email email :password "pw1"})
          login-account (get-in login-response [:body :account])
          token-id (get-in login-response [:body :token :id])
          token-response (get-token! token-id)
          token-account (get-in token-response [:body :account])
          old-token-response (get-token! sign-up-token-id)]
      (:status login-response) => 200
      (:status token-response) => 200
      login-account => sign-up-account
      login-account => token-account
      token-id =not=> sign-up-token-id
      (:status old-token-response) => 404))

  (facts "Login returns 401 if account doesn't exist"
    (let [response (login! {:email (random-email) :password "pw1"})]
      (:status response) => 401
      (:body response) => {:error "invalid-credentials"}))

  (facts "Login returns 401 if password incorrect"
    (let [email (random-email)
          sign-up-response (sign-up! {:email email :password "hello" :name "colin"})
          login-response (login! {:email email :password "world"})]
      (login-response :status) => 401
      (login-response :body) => {:error "invalid-credentials"})))

