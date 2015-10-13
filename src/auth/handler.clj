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
            [dire.core :refer :all]))

(def db-opts
  {:access-key (System/getenv "MC_AWS_ACCESS_KEY")
   :secret-key (System/getenv "MC_AWS_SECRET_KEY")
   :endpoint "https://dynamodb.eu-west-1.amazonaws.com"})

; ---------- requirements ---------
; need to be able to sign up
; need to be able to login
; need to be able to logout
; need to be able to authorise X-AUTH-HEADER is valid

; also need an "auth filter" to add to other services

; future work
; - change password
; - password reset
; - refresh tokens
; - delete tokens
; - multi-user accounts (somehow)

; ---------- data -----------
(def user {:id "6ca2e9c0-f4ed-11e4-b443-353a40402a60"
           :name "Colin Webb"
           :email "colin@mailinator"
           :password "use https://github.com/weavejester/crypto-password"
           :date-created "2015-05-07 20:15:29.500"
           :date-modified "2015-05-07 20:15:29.500"})
; need to lookup by id
; need to lookup by email

(def token {:token "use https://github.com/weavejester/crypto-random"
            :account-id "6ca2e9c0-f4ed-11e4-b443-353a40402a60"
            :date-created "2015-05-07 20:15:29.500"})
; need to lookup by token

(defn get-token! [id]
  (db/get-item db-opts :mc-auth-tokens {:token id}))

; ------------ handlers --------
(defn get-token-handler [id]
  (let [data (get-token! id)]
    (if (nil? data)
      {:status 404}
      {:body data})))

; ------------ routes ----------
(defroutes app-routes
  (GET "/" [] {:body {:application-name "auth"}})
  (POST "/accounts/signup" [] "signup")
  (POST "/auth/login" [] {:body token})
  (POST "/accounts/logout" [] "given some user creds, remove them")
  (GET "/accounts" [] "?user-id= or ?email= return info of user, if creds provided")
  (GET "/tokens/:id" [id] (get-token-handler id))
  (route/not-found "Not Found"))

(def app
  (-> app-routes
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods [:get :post])
      middleware/wrap-json-response
      (middleware/wrap-json-body {:keywords? true})
      (wrap-defaults api-defaults)))

