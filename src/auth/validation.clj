(ns auth.validation
  (:require [schema.core :as s]))

(defn validation-error? [e]
  (if (instance? clojure.lang.ExceptionInfo e)
    (let [data (ex-data e)
          type (:type data)]
      (= type :schema-tools.coerce/error))))

(defn handle-validation-error [e & args]
  (let [data (ex-data e)]
    {:status 400 :body (:error data)}))

