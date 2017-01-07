(ns levanzo.schema
  "Functions to validate a JSON-LD payload based on the Hydra classes definitions of an ApiDocumentation.
   Functions in this namespaces assumes payloads will be expanded JSON-LD payloads."
  (:require [levanzo.hydra :as hydra]
            [levanzo.spec.jsonld :as jsonld-spec]
            [levanzo.namespaces :refer [xsd prefix-for-ns]]
            [clojure.spec :as s]
            [clojure.string :as string]
            [clojure.spec.test :as stest]
            [clojure.spec.gen :as gen]
            [clojure.test.check.generators :as tg]))

;; Definitions around validation errors reporting data
(s/def ::error-type keyword?)
(s/def ::error-cause any?)
(s/def ::error-message string?)
(s/def ::ValidationError (s/keys :req-un [::error-type ::error-cause ::error-message]))
(defrecord ValidationError [error-type error-cause error-message])

(s/def ::ValidationsMap (s/with-gen
                          (s/map-of ::jsonld-spec/uri ::predicate)
                          #(tg/return {})))
(s/def ::predicate (s/fspec :args (s/cat :validations-map ::ValidationsMap
                                         :jsonld ::jsonld-spec/expanded-jsonld)
                            :ret (s/nilable ::ValidationError)))


;; is something a validation error?
(s/fdef invalid?
        :args (s/cat :maybe-error (s/alt :non-error  any?
                                         :validation-error ::ValidationError))
        :ret boolean?)
(defn invalid?
  "Checks if the argument is a validation error"
  [maybe-error]
  (instance? ValidationError maybe-error))

;; validation predicates

(defn xsd-uri?
  "Checks if t is XSD URI encoded in a string"
  [t]
  (and (string? t)
       (string/starts-with? t (prefix-for-ns "xsd"))))


(s/fdef check-xsd-range
        :args (s/cat :range ::jsonld-spec/datatype
                     :value ::jsonld-spec/jsonld-literal)
        :ret boolean?)
(defn check-xsd-range
  "Checks that the value of JSON-LD property is within the XSD range of a property"
  [range value]
  (condp = range
    (xsd "string") (string? (get value "@value"))
    (xsd "decimal") (int? (get value "@value"))
    (xsd "float")   (float? (get value "@value"))
    (xsd "boolean") (boolean? (get value "@value"))
    (throw (Exception. (str "Unknown/not implemented xsd validation for type " range)))))


(s/fdef check-range
        :args (s/with-gen (s/cat :api ::ValidationsMap
                                 :range ::jsonld-spec/uri
                                 :value (s/or
                                         :nested ::jsonld-spec/expanded-jsonld
                                         :literal ::jsonld-spec/jsonld-literal))
                #(tg/tuple (tg/return {})
                           (s/gen ::jsonld-spec/datatype)
                           (s/gen ::jsonld-spec/jsonld-literal)))
        :ret (s/nilable ::ValidationError))
(defn check-range
  "Checks that one JSON-LD property value matches a particular range"
  [api-validations range value]
  (if (xsd-uri? range)
    (if (check-xsd-range range value)
      nil
      (->ValidationError :range-error [range value] (str "Error validating xsd range for range " range " and value " value)))
    (let [validation (get api-validations range)]
      (if (some? validation)
        (validation api-validations value)
        (throw (Exception. (str "Cannot validate missing range class " range)))))))


(s/fdef parse-supported-link
        :args (s/cat :api ::hydra/ApiDocumentation
                     :supported-property (s/and ::hydra/SupportedProperty
                                                #(:is-link %)))
        :ret ::predicate
        :fn (s/and
             #(let [property (-> % :args :supported-property :property)
                    required (-> % :args :supported-property :property-props ::hydra/required)
                    predicate (-> % :ret)]
                (and (or (not required)
                         (invalid? (predicate {} {})))
                     (or (not required)
                         (invalid? (predicate {} {property []})))
                     (invalid? (predicate {} {property [{"@value" 1} {"@value" 2}]}))))))
(defn parse-supported-link
  "Creates a specification for a hydra link"
  [api supported-property]
  (fn [api-validations jsonld]
    (let [property (-> supported-property :property)
          is-optional (not (-> supported-property :property-props ::hydra/required))
          values (get jsonld property [])
          value (first values)]
      (cond
        (= 0 (count values)) (if is-optional
                               nil
                               (->ValidationError :link-property-error
                                                  supported-property
                                                  (str "Missing mandatory property " property)))
        (> 1 (count values)) (->ValidationError :link-property-error
                                                supported-property
                                                (str "Cardinality error, more than one link value for property " property))
        :else                   (if (and (some? (get value "@id"))
                                         (string? (get value "@id")))
                                  nil
                                  (->ValidationError :link-property-error
                                                     supported-property
                                                     (str "Not string value for link (" value ") for property " property)))))))


(s/fdef parse-plain-property
        :args (s/cat :api ::hydra/ApiDocumentation
                     :supported-property (s/and ::hydra/SupportedProperty
                                                #(and (not (:is-link %))
                                                      (not (:is-template %)))))
        :ret ::predicate
        :fn (s/and
             #(let [property  (-> % :args :supported-property :property)
                    required  (-> % :args :supported-property :property-props ::hydra/required)
                    predicate (-> % :ret)]
                (and (or (not required)
                         (invalid? (predicate {} {})))
                     (or (not required)
                         (invalid? (predicate {} {property []})))
                     (invalid? (predicate {} {property [{"@value" 1} {"@value" 2}]})))
                true)))
(defn parse-plain-property
  "Creates a specification for a hydra property"
  [api supported-property]
  (fn [api-validations jsonld]
    (let [property (-> supported-property :property)
          range (-> supported-property :property-props ::hydra/range)
          is-optional (not (-> supported-property :property-props ::hydra/required))
          values (get jsonld property)
          value (first values)]
      (cond
        (= 0 (count values))  (if is-optional
                                nil
                                (->ValidationError :property-error supported-property (str "Missing mandatory property " property)))
        (> 1 (count values))  (->ValidationError :property-error supported-property (str "Cardinality error, more than one link value for property " property))
        :else                   (try
                                  (let [validation-result (check-range api-validations range value)]
                                    (if (invalid? validation-result)
                                      (->ValidationError :property-error validation-result (str "Not value (" value ") in range (" range ") of property " property))
                                      nil))
                                  (catch Exception ex
                                    (->ValidationError :property-error supported-property (.getMessage ex))))))))


(s/fdef parse-supported-property
        :args (s/cat :api ::hydra/ApiDocumentation
                     :supported-property (s/and ::hydra/SupportedProperty
                                                #(not (:is-template %)) ;; @todo remove me when we have support for templates
                                                ))
        :ret ::predicate)
(defn parse-supported-property
  "Creates a specification for a hydra supported-property"
  [api supported-property]
  (cond
    (:is-link supported-property) (parse-supported-link api supported-property)
    (:is-template supported-property) (throw (Exception. "Not supported yet"))
    :else  (parse-plain-property api supported-property)))


(s/fdef parse-supported-class
        :args (s/cat :api ::hydra/ApiDocumentation
                     :api-class ::hydra/SupportedClass)
        :ret ::predicate)
(defn parse-supported-class
  "Creates a specification for a hydra supported class"
  [api api-class]
  (let [validations (map #(parse-supported-property api %) (-> api-class :supported-properties))]
    (fn [api-validations jsonld]
      ;(prn jsonld)
      (and (map? jsonld)
           (let [errors (->> validations
                             (map (fn [validation]
                                    ;(prn validation)
                                    (validation api-validations jsonld)))
                             (filter #(not (nil? %))))]
             ;(prn "ERRORS?")
             ;(prn errors)
             (if (empty? errors)
               nil
               (->ValidationError :invalid-payload errors (str "Errors (" (count errors) ") found during validation"))))))))

(s/fdef build-api-validations
        :args (s/cat :apiDocumentation ::hydra/ApiDocumentation)
        :ret ::ValidationsMap)
(defn build-api-validations [api]
  (let [classes (:supported-classes api)]
    (reduce (fn [acc class]
              (let [uri (-> class :common-props ::hydra/id)
                    validation (parse-supported-class api class)]
                (assoc acc uri validation)))
            {}
            classes)))
