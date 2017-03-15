(ns levanzo.utils
  (:require [clojure.spec :as s]
            [clojure.test.check.generators :as tg]
            [clojure.string :as string]))


(s/fdef clean-nils
        :args (s/cat :map (s/map-of some? any?))
        :ret (s/map-of some? some?))
(defn clean-nils
  "Clean keys with nil values in maps"
  [m]
  (->> m
       (filter (fn [[k v]] (some? v)))
       (into {})))


(defmacro conformant
  [fn-name s args & rest]
  `(let [parsed# (s/conform ~s ~args)]
     (if (= parsed# ::s/invalid)
       (throw (ex-info (str "Invalid arguments for function " ~fn-name)
                       (s/explain-data ~s ~args)))
       ~@rest)))
