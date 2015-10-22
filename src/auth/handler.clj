(ns auth.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :as middleware]
            [ring.middleware.cors :refer [wrap-cors]]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [taoensso.faraday :as db]
            [schema.core :as s]
            [schema.coerce :as coerce]
            [schema-tools.core :as st]
            [dire.core :refer :all]
            [crypto.random :refer [url-part]]
            [crypto.password.bcrypt :as password]))

(defn token-id [] (url-part 32))

(def db-opts
  {:access-key (System/getenv "MC_AWS_ACCESS_KEY")
   :secret-key (System/getenv "MC_AWS_SECRET_KEY")
   :endpoint "https://dynamodb.eu-west-1.amazonaws.com"})

(defn encrypt-password [plain-text]
  (password/encrypt plain-text (System/getenv "MUNCHCAL_BCRYPT_WORK_FACTOR")))

; ---------- data -----------
; dynamotable
;   hash id (for normal lookup)
;   secondary-index email (for login lookup)
(defn account []
  {:id "6ca2e9c0-f4ed-11e4-b443-353a40402a60"
   :name "Colin Webb"
   :email "colin@mailinator"
   :password (password/encrypt "password1" 12)
   :date-created "2015-05-07 20:15:29.500"
   :date-modified "2015-05-07 20:15:29.500"})

; dynamotable
;   hash id (for checking if token exists)
;   secondary-index account-id (for login flow lookup)
(defn token []
  {:id (token-id)
   :account-id "6ca2e9c0-f4ed-11e4-b443-353a40402a60"
   :date-created "2015-05-07 20:15:29.500"})

; ---------- handlers -----------
(defn account-and-token-response []
  {:token (dissoc (token) :account-id)
   :account (dissoc (account) :password)})

(defn sign-up [req]
  ; require email, password, name
  ; check they are valid, and above within limits
  ; check email isn't already in use
  ; check name isn't already in use?
  ; generate auth-token
  ; hash password
  ; persist to dynamo
  {:body (account-and-token-response)})

(defn login [req]
  ; require email, password
  ; check they are valid, and within limits
  ; hash password
  ; lookup by email, and compare password
  ; if that works, lookup token by account-id
  (do (println req) {:body (account-and-token-response)}))

(defn logout [req]
  ; require token
  ; delete token from dynamodb
  {:status 204})

(defn lookup-token [req]
  ; lookup token by token-id
  ; lookup account by account-id
  {:body (account-and-token-response)})

; ------------ routes ----------
(defroutes app-routes
  (GET "/" [] {:body {:application-name "auth"}})
  (POST "/auth/signup" req (sign-up req))
  (POST "/auth/login" req (login req))
  (POST "/auth/logout" req (logout req))
  (GET "/auth/tokens/:id" req (lookup-token req))
  (route/not-found "Not Found"))

(def app
  (-> app-routes
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods [:get :post])
      middleware/wrap-json-response
      (middleware/wrap-json-body {:keywords? true})
      (wrap-defaults api-defaults)))

