(ns levanzo.payload
  (:require [levanzo.routing :as routing]
            [levanzo.namespaces :as lns]
            [levanzo.hydra :as hydra]
            [levanzo.jsonld :as jsonld]
            [clojure.spec :as s]
            [clojure.test.check.generators :as tg]))

(s/def ::common-props (s/with-gen (s/keys :req [::hydra/id])
                        #(tg/fmap (fn [uri]
                                    {:common-props {::hydra/id uri}})
                                  (s/gen ::hydra/id))))
(s/def ::uri-or-model-args (s/or :uri ::hydra/id
                                 :with-uri (s/keys :req-un [::common-props])))

(def ^:dynamic *context* (atom {}))
(defn context [{:keys [vocab base ns]}]
  (let [prefixes (->> (or ns [])
                      (map (fn [ns] [(name ns) (lns/prefix-for-ns (name ns))]))
                      (filter (fn [[ns uri]] (some? uri)))
                      (into {}))
        vocab (if (lns/default-ns?)
                (or vocab (lns/default-ns))
                vocab)]
    (reset! *context*
            (->> prefixes
                 (merge {"@vocab" vocab
                         "@base" base})
                 (filter (fn [[k v]] (some? v)))
                 (into {})))))

(defn compact
  ([json-ld {:keys [context]}]
   (let [res (jsonld/compact-json-ld json-ld @*context*)
         res (if (map? res) res (first res))]
     (if context res (dissoc res "@context"))))
  ([json-ld] (compact json-ld {:context true})))

(defn expand [json-ld]
  (let [res (jsonld/expand-json-ld json-ld)]
    (if (map? res) res (first res))))

(defn normalize [json-ld]
  (-> json-ld
      expand
      jsonld/flatten-json-ld))


(s/fdef uri-or-model
        :args (s/cat :object ::uri-or-model-args)
        :ret string?)
(defn uri-or-model [object]
  (s/assert ::uri-or-model-args object)
  (let [uri (if (string? object)
              object
              (-> object :common-props ::hydra/id))]
    (if (string? uri)
      uri
      (throw (Exception. (str "Cannot obtain URI from " object))))))

(s/fdef link-for
        :args (s/cat :model ::uri-or-model-args
                     :args (s/map-of keyword? any?))
        :ret ::hydra/id)
(defn link-for [model args]
  (s/assert ::uri-or-model-args model)
  (s/assert (s/map-of keyword? any?) args)
  (let [args (concat [(lns/resolve (uri-or-model model))]
                     (->> args (into []) flatten))]
    (apply routing/link-for args)))

(defn json-ld
  "Builds a new JSON-LD object"
  [& props]
  (s/assert (s/coll-of (s/or :map-tuple (s/map-of string? any?)
                             :tupe (s/tuple string? any?))) props)
  (let [json (into {} props)
        context (get json "@context" {})
        context (merge @*context* context)]
    (-> (if (not= {} context)
          (assoc json "@context" context)
          json)
        expand)))

(declare merge*)
(defn resolve-merge-dups*
  [ms]
  (println "MERGING DUPS ")
  (prn ms)
  (->> ms
       (reduce (fn [acc m]
                 (let [id (get m "@id")
                       tuple (get acc id [])
                       tupe (cons m tuple)]
                   (if (= 2 (count tuple))
                     (assoc m id [(apply merge* tuple)])
                     (assoc m tuple))))
               {})
       vals
       concat))
(defn merge* [a b]
  (let [keys-a (keys a)
        keys-b (keys b)
        all-keys (set (concat keys-a keys-b))]
    (println "ALL KEYS")
    (prn all-keys)
    (reduce (fn [acc prop]
              (condp = prop
                "@id"   (assoc acc "@id" (get b "@id"))
                "@type" (let [types-a (get a "@type")
                              types-b (get b "@type")]
                          (assoc acc "@type" (into [] (set (concat types-a types-b)))))
                (let [value-a (get a prop)
                      value-b (get b prop)
                      _ (println "VALUES FOR " prop)
                      _ (prn [value-a value-b])
                      all-values (->> (flatten (concat value-a value-b))
                                      (filter #(some? %))
                                      set
                                      (into []))
                      nested (filter #(some? (get % "@id")) all-values)
                      _ (println "NESTED")
                      _ (prn nested)
                      not-nested (filter #(nil? (get % "@id")) all-values)
                      _ (println "NOT NESTED")
                      _ (prn not-nested)
                      nested (if (> (count nested) 1)
                               [(resolve-merge-dups* nested)]
                               nested)]
                  (println "FINAL VALUES")
                  (prn (concat nested not-nested))
                  (assoc acc prop (concat nested not-nested)))))
            {}
            all-keys)))
(defn deep-merge
  "Merges two json-ld documents for the same ID"
  ([a b]
   (let [a (expand a)
         b (expand b)]
     (merge* a b))))

(defn merge
  "Merges two json-ld documents for the same ID"
  ([a b]
   (let [a (expand a)
         b (expand b)]
     (clojure.core/merge a b))))

(defn title
  [title]
  ["hydra:title" title])

(defn description
  [description]
  ["hydra:description" description])

(defn total-items
  [count]
  ["hydra:totalItems" count])

(defn members
  "Generates a Hydra [hydra-member instances] tupes"
  [members]
  ["hydra:member" members])

(defn total-items
  [count]
  ["hydra:totalItems" count])

(defn id
  "Generates a JSON-LD [@id TYPE] pair"
  ([model args]
   {"@id" (link-for model args)})
  ([model] (id model {})))


(defn type
  "Generates a JSON-LD [@type URI] pair"
  ([model]
   {"@type" (uri-or-model model)}))

(s/fdef supported-property
        :args (s/cat :property ::uri-or-model-args
                     :value any?)
        :ret (s/tuple string? any?))
(defn supported-property
  "Generates a JSON-LD [proeprty literal-value] pair"
  ([property value]
   (s/assert ::uri-or-model-args property )
   {(uri-or-model property) value}))

(s/def ::supported-property-args (s/or
                                  :1-arg (s/cat :property ::uri-or-model-args)
                                  :2-arg (s/or :model-model (s/cat :property-model ::uri-or-model-args
                                                                   :target-model ::uri-or-model-args)
                                               :model-args  (s/cat :model ::uri-or-model-args
                                                                   :args  (s/map-of keyword? any?)))
                                  :3-arg (s/cat :property-model ::uri-or-model-args
                                                :target-model ::uri-or-model-args
                                                :args (s/map-of keyword? any?))))
(s/fdef supported-property
        :args ::supported-property-args
        :ret (s/tuple string? any?))
(defn supported-link
  "Generates a JSON-LD [property {@id link}]  pair"
  ([property-model target-model args]
   (let [uri (uri-or-model property-model)]
     (s/assert ::supported-property-args [property-model target-model args])
     {uri (id target-model args)}))
  ([arg1 arg2]
   (s/assert ::supported-property-args [arg1 arg2])
   (if (map? arg2)
     (supported-link arg1 arg1  arg2)
     (supported-link arg1 arg2  {})))
  ([model]
   (s/assert ::supported-property-args [model])
   (supported-link model model)))
