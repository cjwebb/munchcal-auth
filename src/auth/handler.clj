(ns auth.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :as middleware]
            [ring.middleware.cors :refer [wrap-cors]]
;            [clj-time.core :as t]
;            [clj-time.format :as f]
            [schema.core :as s]
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

(defn account-and-token-response [account token]
  {:body {:account (dissoc account :password)
          :token (dissoc token :account-id)}})

; ---------- handlers -----------
; todo - make this actually validate
(s/defschema SignUpRequest {:email s/Str :password s/Str :name s/Str})

(defn- make-account-data [sign-up-data]
  (let [encrypted-password (encrypt-password (sign-up-data :password))]
    (-> sign-up-data
        (assoc :password encrypted-password)
        (assoc :id (make-account-id)))))

(defn- make-token-data [account]
  (-> {:id (make-token-id)}
      (assoc :account-id (account :id))))

(defn sign-up [req]
  (let [data (st/select-schema (req :body) SignUpRequest)
        email-exists? (some? (db/get-account-by-email! (:email data)))
        account-data (make-account-data data)
        token-data (make-token-data account-data)]
    (if email-exists?
      {:status 400 :body {:error {:email "already-in-use"}}}
      (do
        (db/put-token! token-data)
        (db/put-account! account-data)
        (account-and-token-response account-data token-data)))))

(with-handler! #'sign-up v/validation-error? v/handle-validation-error)

(s/defschema LoginRequest {:email s/Str :password s/Str})

(defn login [req]
  (let [data (st/select-schema (:body req) LoginRequest)
        account (db/get-account-by-email! (:email data))]
    (if-not (and (some? (:password account))
                 (password/check (:password data) (:password account)))
      {:status 401 :body {:error "invalid-credentials"}}
      (let [new-token-data (make-token-data account)
            old-token (db/get-token-by-account-id! (:id account))]
        (do
          (db/put-token! new-token-data)
          (db/delete-token! (:id old-token))
          (account-and-token-response account new-token-data))))))

(with-handler! #'login v/validation-error? v/handle-validation-error)

(s/defschema LogoutRequest {:token {:id s/Str}})

(defn logout [req]
  (let [data (st/select-schema (:body req) LogoutRequest)
        token-id (get-in data [:token :id])]
    (do
      (db/delete-token! token-id)
      {:status 204})))

(with-handler! #'logout v/validation-error? v/handle-validation-error)

(defn lookup-token [id]
  (let [token (db/get-token! id)]
    (if (nil? (:account-id token))
      {:status 404}
      (let [account (db/get-account! (:account-id token))]
        (if (nil? account)
          {:status 404}
          (account-and-token-response account token))))))

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

