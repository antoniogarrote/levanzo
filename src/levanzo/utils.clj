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
