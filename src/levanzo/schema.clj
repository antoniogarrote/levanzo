(ns levanzo.schema
  (:require [levanzo.hydra :as hydra]
            [levanzo.spec.jsonld :as jsonld-spec]
            [clojure.spec :as s]
            [clojure.string :as string]
            [clojure.spec.test :as stest]
            [clojure.spec.gen :as gen]
            [clojure.test.check.generators :as tg]))

(s/def ::predicate (s/fspec :args (s/cat :jsonld ::jsonld-spec/expanded-jsonld)
                            :ret boolean?))

(def xsd-ns "http://www.w3.org/2001/XMLSchema#")

(defn xsd [t]
  (str xsd-ns t))

(defn xsd-uri? [t]
  (string/starts-with? t xsd-ns))

(s/fdef check-xsd-range
        :args (s/cat :range ::jsonld-spec/datatype
                     :value ::jsonld-spec/jsonld-literal)
        :ret boolean?)
(defn check-xsd-range [range value]
  (condp = range
    (xsd "string") (string? (get value "@value"))
    (xsd "decimal") (int? (get value "@value"))
    (xsd "float")   (float? (get value "@value"))
    (xsd "boolean") (boolean (get value "@value"))
    (throw (Exception. (str "Unknown/not implemented xsd validation for type " range)))))

(s/fdef check-range
        :args (s/cat :api ::hydra/ApiDocumentation
                     :range ::jsonld-spec/uri
                     :value ::jsonld-spec/uri)
        :ret boolean?)
(defn check-range [api range value]
  (if (xsd-uri? range)
    (check-xsd-range range value)
    (let [api-class (hydra/find-class api range)]
      (if (some? api-class)
        (throw (Exception. (str "Class validation not implemented yet")))
        (throw (Exception. (str "Cannot validate missing range class " range)))))))


(s/fdef parse-supported-link
        :args (s/cat :api ::hydra/ApiDocumentation
                     :supported-property (s/and ::hydra/SupportedProperty
                                                #(:is-link %)))
        :ret ::predicate)
(defn parse-supported-link
  "Creates a specification for a hydra link"
  [api supported-property]
  (fn [jsonld]
    (let [property (-> supported-property :property)
          is-optional (-> supported-property :property-props ::hydra/required)
          values (get jsonld property)
          value (first values)]
      (cond
        (= 0 (count values))    (if is-optional
                                  true
                                  (throw (Exception. (str "Missing mandatory property " property))))
        (not= 1 (count values)) (throw (Exception. (str "Cardinality error, more than one link value for property " property)))
        :else                   (if (and (some? (get value "@id"))
                                         (string? (get value "@id")))
                                  true
                                  (throw (Exception. (str "Not string value for link (" value ") for property " property))))))))

(s/fdef parse-plain-property
        :args (s/cat :api ::hydra/ApiDocumentation
                     :supported-property (s/and ::hydra/SupportedProperty
                                                #(and (not (:is-link %))
                                                      (not (:is-template %)))))
        :ret ::predicate)
(defn parse-plain-property
  "Creates a specification for a hydra property"
  [api supported-property]
  (fn [jsonld]
    (let [property (-> supported-property :property)
          range (-> supported-property :common-props ::hydra/range)
          is-optional (-> supported-property :property-props ::hydra/required)
          values (get jsonld property)
          value (first values)]
      (cond
        (= 0 (count values))    (if is-optional
                                  true
                                  (throw (Exception. (str "Missing mandatory property " property))))
        (not= 1 (count values)) (throw (Exception. (str "Cardinality error, more than one link value for property " property)))
        :else                   (if (check-range api range value)
                                  true
                                  (throw (Exception. (str "Not string value for link (" value ") for property " property))))))))

(s/fdef parse-supported-link
        :args (s/cat :api ::hydra/ApiDocumentation
                     :supported-property ::hydra/SupportedProperty)
        :ret ::predicate)
(defn parse-supported-property
  "Creates a specification for a hydra supported-property"
  [api supported-property]
  (cond
    (:is-link supported-property) (parse-supported-link supported-property)
    (:is-template supported-property) (throw (Exception. "Not supported yet"))
    :else  (parse-plain-property supported-property)))

(s/fdef parse-supported-class
        :args (s/cat :api ::hydra/ApiDocumentation
                     :api-class ::hydra/SupportedClass)
        :ret ::predicate)
(defn parse-supported-class
  "Creates a specification for a hydra supported class"
  [api api-class]
  (let [validations (map #(parse-supported-property api %) (-> api-class :supported-properties))]
    (fn [jsonld]
      (and (map? jsonld)
           (reduce (fn [acc v]
                     (and acc (v jsonld))
                     true
                     validations))))))
