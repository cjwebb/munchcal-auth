(ns auth.generators
  (:require [clojure.test.check.generators :as gen]))

; stolen from https://github.com/clojure/test.check/blob/master/doc/intro.md
(def domain (gen/elements ["mailinator.com" "munchcal.com"]))

(def email-gen
  (gen/fmap (fn [[name domain-name]]
              (str name "@" domain-name))
            (gen/tuple (gen/not-empty gen/string-alphanumeric) domain)))

(defn random-email []
  (last (gen/sample email-gen)))

