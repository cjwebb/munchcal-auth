(ns auth.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :as middleware]
            [ring.middleware.cors :refer [wrap-cors]]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [schema.core :as s]
            [schema.coerce :as coerce]
            [schema-tools.core :as st]
            [auth.validation :as v]
            [auth.db :as db]
            [dire.core :refer :all]
            [crypto.random :refer [url-part]]
            [crypto.password.bcrypt :as password]))

(defn make-token-id [] (url-part 32))
(defn make-account-id [] (str (java.util.UUID/randomUUID)))

(defn encrypt-password [plain-text]
  (password/encrypt plain-text
                    (read-string (System/getenv "MUNCHCAL_BCRYPT_WORK_FACTOR"))))

; ---------- handlers -----------
(defn account-and-token-response []
  (let [account-id (make-account-id)
        token-id (make-token-id)]
  {:token (dissoc (db/get-token! token-id) :account-id)
   :account (dissoc (db/get-account! account-id) :password)}))

; todo - make this actually validate
(s/defschema SignUpRequest
  {:email s/Str
   :password s/Str
   :name s/Str})

(defn- make-account-data [sign-up-data]
  (let [encrypted-password (encrypt-password (sign-up-data :password))]
    (-> sign-up-data
        (assoc :password encrypted-password)
        (assoc :id (make-account-id)))))

(defn- make-token-data [account]
  (-> {:id (make-token-id)}
      (assoc :account-id (account :id))))

(defn sign-up [req]
  ; check email isn't already in use
  (let [data (st/select-schema (req :body) SignUpRequest)
        account-data (make-account-data data)
        token-data (make-token-data account-data)
        token (db/put-token! token-data)]
    {:body {:account (db/put-account! account-data) ; todo - db/put-account! returns nothing
            :token {:id (token-data :id)}}}))

(with-handler! #'sign-up v/validation-error? v/handle-validation-error)

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

; todo - make this nicer, maybe using a monad,
;        or some preconditions with dire
(defn lookup-token [id]
  (let [token (db/get-token! id)]
    (if (nil? (:account-id token))
      {:status 404}
      (let [account (db/get-account! (:account-id token))]
        (if (nil? account)
          {:status 404}
          {:body {:account account :token token}})))))

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

