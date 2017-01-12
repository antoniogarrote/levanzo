(ns levanzo.jsonld
  (:require [clojure.spec :as s]
            [clojure.test.check.generators :as tg])
  (:import [com.github.jsonldjava.core JsonLdProcessor JsonLdOptions DocumentLoader]))

;; list without duplicates
(s/def ::list-no-dups (s/with-gen
                        (s/and
                         ;; it has to be a collection
                         (s/coll-of any?)
                         ;; after transforming collection into set, the number of items is the same
                         #(= (->> %
                                  (into #{})
                                  count)
                             (count %)))
                        ;; we generate a vector of ints for the tests
                        #(tg/fmap distinct (tg/vector tg/int))))
(s/fdef add-not-dup
        :args (s/cat :xs ::list-no-dups
                     :y any?)
        :ret ::list-no-dups
        :fn (s/or
             ;; if we don't find the element in the list,
             ;; the length of the args collection and output colleciton
             ;; must be the same
             :not-found #(= (-> % :args :xs)
                            (-> % :ret))
             ;; if the element is in the list, the length of the ret collection
             ;; is one element bigger than the arg collection, and the new
             ;; element is at the end
             :found (s/and
                     #(= (inc (count (-> % :args :xs)))
                         (count (-> % :ret)))
                     #(= (-> % :args :y)
                         (last (-> % :ret))))))
(defn add-not-dup
  "Adds y to xs if y is not present in xs"
  [xs y]
  (->> (concat xs [y])
       (reduce (fn [[xs m] x]
                 (if (some? (get m x))
                   [xs m]
                   [(concat xs [x]) (assoc m x true)]))
               [[]{}])
       first))

(defn assoc-if-some
  "Assocs a value to target map with property target if source property is in the source map"
  [source target element jsonld]
  (if (some? (get element source))
    (let [source-value (get element source)
          target-value (get jsonld target)]
      (cond
        (nil? target-value) (assoc jsonld target source-value)
        (and (coll? target-value)
             (not (map? target-value))) (assoc jsonld target (distinct (concat target-value [source-value])))
        :else (assoc jsonld target (distinct [target-value source-value ]))))
    jsonld))

(defn set-if-some
  "Sets a property in the target if it exists in the source"
  [source target jsonld]
  (if (some? source)
    (assoc jsonld target source)
    jsonld))


(defn java->clj
  "Transforms a data structure made of Java objects into native
   Clojure data structures"
  [obj]
  (cond
    (instance? java.util.Map obj) (->> obj
                                       (map (fn [[k v]] [k (java->clj v)]))
                                       (into {}))
    (instance? java.util.List obj) (->> obj
                                        (map #(java->clj %))
                                        (into []))
    :else obj))

(defn expand-json-ld
  ([json-ld]
   (java->clj (JsonLdProcessor/expand json-ld))))


(defn flatten-json-ld
  ([json-ld context]
   (java->clj (JsonLdProcessor/flatten json-ld context (JsonLdOptions.))))
  ([json-ld]
   (flatten-json-ld json-ld nil)))

(defn compact-json-ld [json-ld context]
  (java->clj (JsonLdProcessor/compact json-ld context (JsonLdOptions.))))
