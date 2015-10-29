(ns auth.db
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [taoensso.faraday :as db]
            [schema.core :as s]
            [dire.core :refer :all]))

; ---------- config ---------
(def db-opts
  {:access-key (System/getenv "MC_AWS_ACCESS_KEY")
   :secret-key (System/getenv "MC_AWS_SECRET_KEY")
   :endpoint "http://localhost:8000"})

; ---------- setup ---------
(defn- create-accounts-table []
  (db/create-table db-opts :accounts
    [:id :s]
    {:throughput {:read 1 :write 1} :block? true}))

(with-pre-hook! #'create-accounts-table #(println "Creating :accounts database table"))

(defn- create-tokens-table []
  (db/create-table db-opts :tokens
    [:id :s]
    {:throughput {:read 1 :write 1} :block? true}))

(with-pre-hook! #'create-tokens-table #(println "Creating :tokens database table"))

(defn init! []
  (let [tables (set (db/list-tables db-opts))]
    (do
      (when-not (contains? tables :accounts) (create-accounts-table))
      (when-not (contains? tables :tokens) (create-tokens-table)))))

; ---------- methods  -----------
; dynamotable
;   hash id (for normal lookup)
;   secondary-index email (for login lookup)
(defn put-account! [data]
  ; todo - add date-created/modified
  (db/put-item db-opts :accounts data))

(s/defn get-account!
  "Retrieve an account, via the account-id"
  [account-id :- s/Str]
  (db/get-item db-opts :accounts {:id account-id}))

;{:id id
;   :name "Colin Webb"
;   :email "colin@mailinator"
;   :password "password1"
;   :date-created "2015-05-07 20:15:29.500"
;   :date-modified "2015-05-07 20:15:29.500"})


; dynamotable
;   hash id (for checking if token exists)
;   secondary-index account-id (for login flow lookup)
(defn put-token! [data]
  (db/put-item db-opts :tokens data))

(s/defn get-token!
  "Retrieve an auth token, given the token-id"
  [token-id :- s/Str]
  (db/get-item db-opts :tokens {:id token-id}))

;  {:id id 
;   :account-id "6ca2e9c0-f4ed-11e4-b443-353a40402a60"
;   :date-created "2015-05-07 20:15:29.500"})

