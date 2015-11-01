(ns auth.db
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [taoensso.faraday :as db]
            [schema.core :as s]
            [dire.core :refer :all]))

; ---------- config ---------
; todo - get endpoint from env
(def db-opts
  {:access-key (System/getenv "MC_AWS_ACCESS_KEY")
   :secret-key (System/getenv "MC_AWS_SECRET_KEY")
   :endpoint "http://localhost:8000"})

; ---------- setup ---------
(defn- create-accounts-table []
  (db/create-table db-opts :accounts
    [:id :s]
    {:throughput {:read 1 :write 1}
     :block? true
     :gsindexes [{:name "email-index"
                  :hash-keydef [:email :s]
                  :projection :all
                  :throughput {:read 1 :write 1}}]}))

(with-pre-hook! #'create-accounts-table #(println "Creating :accounts database table"))

(defn- create-tokens-table []
  (db/create-table db-opts :tokens
    [:id :s]
    {:throughput {:read 1 :write 1}
     :block? true
     :gsindexes [{:name "account-index"
                  :hash-keydef [:account-id :s]
                  :projection :all
                  :throughput {:read 1 :write 1}}]}))

(with-pre-hook! #'create-tokens-table #(println "Creating :tokens database table"))

(defn init! []
  (let [tables (set (db/list-tables db-opts))]
    (do
      (when-not (contains? tables :accounts) (create-accounts-table))
      (when-not (contains? tables :tokens) (create-tokens-table)))))

; ---------- methods  -----------
(defn put-account! [data]
  ; todo - add date-created/modified
  (db/put-item db-opts :accounts data))

(s/defn get-account!
  "Retrieve an account, via the account-id"
  [account-id :- s/Str]
  (db/get-item db-opts :accounts {:id account-id}))

(s/defn get-account-by-email!
  "Retrieve an account, via email address"
  [email :- s/Str]
  (first (db/query db-opts :accounts
                   {:email [:eq email]}
                   {:index "email-index"})))

(defn put-token! [data]
  (db/put-item db-opts :tokens data))

(s/defn get-token!
  "Retrieve an auth token, via the token-id"
  [token-id :- s/Str]
  (db/get-item db-opts :tokens {:id token-id}))

(s/defn get-token-by-account-id!
  "Retrieve an auth token, via the account-id"
  [account-id :- s/Str]
  (first (db/query db-opts :tokens
                   {:account-id [:eq account-id]}
                   {:index "account-index"})))

(s/defn delete-token!
  "Delete an auth token, via the token-id"
  [token-id :- s/Str]
  (db/delete-item db-opts :tokens {:id token-id}))

