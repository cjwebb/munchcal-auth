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

(defn make-token-id [] (url-part 32))
(defn make-account-id [] (str (java.util.UUID/randomUUID)))

(def db-opts
  {:access-key (System/getenv "MC_AWS_ACCESS_KEY")
   :secret-key (System/getenv "MC_AWS_SECRET_KEY")
   :endpoint "https://dynamodb.eu-west-1.amazonaws.com"})

(defn encrypt-password [plain-text]
  (password/encrypt plain-text
                    (read-string (System/getenv "MUNCHCAL_BCRYPT_WORK_FACTOR"))))

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
  {:id (make-token-id)
   :account-id "6ca2e9c0-f4ed-11e4-b443-353a40402a60"
   :date-created "2015-05-07 20:15:29.500"})

; ---------- handlers -----------
(defn account-and-token-response []
  {:token (dissoc (token) :account-id)
   :account (dissoc (account) :password)})

; todo - make this actually validate
(s/defschema SignUpRequest
  {:email s/Str
   :password s/Str
   :name s/Str})

(defn sign-up [req]
  ; check email isn't already in use
  ; hash password
  ; persist to dynamo
  (let [data (st/select-schema (req :body) SignUpRequest)
        account-id (make-account-id)
        token-id (make-token-id)
        encrypted-password (encrypt-password (data :password))]
    {:body {:account (assoc (dissoc data :password) :id account-id)
            :token {:id token-id}}}))

(defn validation-error? [e]
  (if (instance? clojure.lang.ExceptionInfo e)
    (let [data (ex-data e)
          type (:type data)]
      (= type :schema-tools.coerce/error))))

(defn handle-validation-error [e & args]
  (let [data (ex-data e)]
    {:status 400 :body (:error data)}))

(with-handler! #'sign-up validation-error? handle-validation-error)

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

(defn lookup-token [id]
  ; lookup token by token-id
  ; lookup account by account-id
  {:body (account-and-token-response)})

; ------------ routes ----------
(defroutes app-routes
  (GET "/" [] {:body {:application-name "auth"}})
  (POST "/auth/signup" req (sign-up req))
  (POST "/auth/login" req (login req))
  (POST "/auth/logout" req (logout req))
  (GET "/auth/tokens/:id" [id] (lookup-token id))
  (route/not-found {:body {:error "Not Found"}}))

(def app
  (-> app-routes
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods [:get :post])
      (middleware/wrap-json-body {:keywords? true})
      middleware/wrap-json-response
      (wrap-defaults api-defaults)))

