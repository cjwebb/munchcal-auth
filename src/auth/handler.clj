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

; ---------- data -----------
; dynamotable
;   hash id (for normal lookup)
;   secondary-index email (for login lookup)
(def account {:id "6ca2e9c0-f4ed-11e4-b443-353a40402a60"
              :name "Colin Webb"
              :email "colin@mailinator"
              :password "use https://github.com/weavejester/crypto-password"
              :date-created "2015-05-07 20:15:29.500"
              :date-modified "2015-05-07 20:15:29.500"})

; dynamotable
;   hash id (for checking if token exists)
;   secondary-index account-id (for login flow lookup)
(def token {:id "use https://github.com/weavejester/crypto-random"
            :account-id "6ca2e9c0-f4ed-11e4-b443-353a40402a60"
            :date-created "2015-05-07 20:15:29.500"})

(defn account-and-token-response []
  {:token (dissoc token :account-id)
   :account (dissoc account :password)})

; ------------ routes ----------
(defroutes app-routes
  (GET "/" [] {:body {:application-name "auth"}})
  (POST "/auth/signup" [] {:body (account-and-token-response)}) ;given sign-up stuff, return all
  (POST "/auth/login" [] {:body (account-and-token-response)}) ;given username+password, return all
  (POST "/auth/logout" [] {:status 204}) ; delete token
  ; don't need this yet
  ;(GET "/auth/accounts/:id" [] {:body (dissoc account :password)}) ;given the account-id, don't show token info
  (GET "/auth/tokens/:id" [] {:body (account-and-token-response)}) ;given token, show everything
  (route/not-found "Not Found"))

(def app
  (-> app-routes
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods [:get :post])
      middleware/wrap-json-response
      (middleware/wrap-json-body {:keywords? true})
      (wrap-defaults api-defaults)))

