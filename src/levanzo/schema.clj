(ns levanzo.schema
  "Functions to validate a JSON-LD payload based on the Hydra classes definitions of an ApiDocumentation.
   Functions in this namespaces assumes payloads will be expanded JSON-LD payloads."
  (:require [levanzo.hydra :as hydra]
            [levanzo.spec.jsonld :as jsonld-spec]
            [levanzo.namespaces :refer [xsd prefix-for-ns resolve]]
            [clojure.spec :as s]
            [clojure.string :as string]
            [clojure.spec.test :as stest]
            [clojure.spec.gen :as gen]
            [clojure.test.check.generators :as tg]
            [taoensso.timbre :as log]))

;; context in which a validation must be performed: reading (GET) , write (POST), update (PUT/PATCH)
(s/def ::mode #{:read :write :update})

;; Definitions around validation errors reporting data
(s/def ::error-type keyword?)
(s/def ::error-cause any?)
(s/def ::error-message string?)
(s/def ::ValidationError (s/keys :req-un [::error-type ::error-cause ::error-message]))
(defrecord ValidationError [error-type error-cause error-message])

;; A container of validations for different Hydra model components identified by URI
(s/def ::ValidationsMap (s/with-gen
                          (s/map-of ::jsonld-spec/uri ::predicate)
                          #(tg/return {})))

;; A validation predicate
(s/def ::predicate (s/fspec :args (s/cat :mode ::mode
                                         :validations-map ::ValidationsMap
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
        :args (s/with-gen (s/cat :mode ::mode
                                 :api ::ValidationsMap
                                 :range ::jsonld-spec/uri
                                 :value (s/or
                                         :nested ::jsonld-spec/expanded-jsonld
                                         :literal ::jsonld-spec/jsonld-literal))
                #(tg/tuple
                  (s/gen ::mode)
                  (tg/return {})
                  (s/gen ::jsonld-spec/datatype)
                  (s/gen ::jsonld-spec/jsonld-literal)))
        :ret (s/nilable ::ValidationError))
(defn check-range
  "Checks that one JSON-LD property value matches a particular range"
  [mode api-validations range value]
  (if (xsd-uri? range)
    (if (check-xsd-range range value)
      nil
      (->ValidationError :range-error [range value] (str "Error validating xsd range for range " range " and value " value)))
    (let [validation (get api-validations range)]
      (if (some? validation)
        (validation mode api-validations value)
        (throw (Exception. (str "Cannot validate missing range class " range)))))))


(s/fdef parse-link
        :args (s/cat :api ::hydra/ApiDocumentation
                     :link (s/and ::hydra/Property
                                  #(:is-link %)))
        :ret ::predicate)
(defn parse-link
  "Creates a validator for a hydra link property value"
  [api link]
  (fn [mode api-validations jsonld]
    (if (and (some? (get jsonld "@id"))
             (string? (get jsonld "@id")))
      nil
      (->ValidationError :link-value-error
                         link
                         (str "Not string value for link (" jsonld ") for property " (-> link :common-props ::hydra/id))))))

(s/fdef parse-property
        :args (s/cat :api ::hydra/ApiDocumentation
                     :link (s/and ::hydra/Property
                                  #(not (or (:is-link %)
                                            (:is-template %)))))
        :ret ::predicate)
(defn parse-property
  "Creates a validator for a hydra rdf literal property value"
  [api property]
  (fn [mode api-validations jsonld]
    (let [range (-> property :rdf-props ::hydra/range)]
      (log/debug "Range is " range " for property " (hydra/id property))
      (try
        (let [validation-result (check-range mode api-validations range jsonld)]
          (if (invalid? validation-result)
            (->ValidationError :property-range-error validation-result (str "Not value (" jsonld ") in range (" range ") of property " (-> property :common-props ::hydra/id)))
            nil))
        (catch Exception ex
          (->ValidationError :property-range-error property (.getMessage ex)))))))

(s/fdef check-access-mode
        :args (s/cat :mode ::mode
                     :supported-property ::hydra/SupportedProperty)
        :ret (s/nilable ::ValidationError))
(defn check-access-mode
  "Checks if a property is compatible with the access mode"
  [mode supported-property]
  (let [readonly (or (-> supported-property :property-props ::hydra/readonly) false)
        writeonly (or (-> supported-property :property-props ::hydra/writeonly) false)]
    (condp = mode
      :read (when writeonly
              (->ValidationError :access-mode-error {:property-mode {:readonly readonly :writeonly writeonly} :access-mode mode :property supported-property}
                                 "Writeonly property cannot be access in read mode"))
      :write (when readonly
               (->ValidationError :access-mode-error {:property-mode {:readonly readonly :writeonly writeonly} :access-mode mode :property supported-property}
                                  "Readonly property cannot be access in write mode"))
      :update (when (or readonly writeonly)
                (->ValidationError :access-mode-error {:property-mode {:readonly readonly :writeonly writeonly} :access-mode mode :property supported-property}
                                   "Readonly and writeonly properties cannot be access in update mode"))
      (throw (Exception. (str "Unknown access mode " mode))))))

(s/fdef compatible-mode?
        :args (s/cat :mode ::mode
                     :supported-property ::hydra/supported-propert)
        :ret boolean?)
(defn compatible-mode? [mode supported-property]
  (let [readonly    (or (-> supported-property :property-props ::hydra/readonly) false)
        writeonly   (or (-> supported-property :property-props ::hydra/writeonly) false)]
    (condp = mode
      :read (not writeonly)
      :write (not readonly)
      :update (and (not writeonly) (not readonly)))))

(s/fdef with-access-mode-validation
        :args (s/cat :supported-property ::hydra/SupportedProperty
                     :predicate ::predicate)
        :ret ::predicate)
(defn with-access-mode-validation
  "Wraps a validation to provide access mode validation"
  [supported-property predicate]
  (let [property-id (-> supported-property :property :common-props ::hydra/id)]
    (fn [mode api-validations jsonld]
      (if (contains? jsonld property-id)
        ;; the property is in the payload, we must check valid access mode
        (let [access-mode-result (check-access-mode mode supported-property)]
          (if (invalid? access-mode-result)
            access-mode-result
            (predicate mode api-validations jsonld)))
        ;; the property is not in the map, but might be a missing
        ;; mandatory property, we check if it is compatible with the access mode
        (if (compatible-mode? mode supported-property)
          (predicate mode api-validations jsonld)
          nil)))))

(s/fdef parse-supported-link
        :args (s/cat :api ::hydra/ApiDocumentation
                     :supported-property (s/and ::hydra/SupportedProperty
                                                #(-> % :property :is-link)))
        :ret ::predicate
        :fn (s/and
             #(let [readonly  (-> % :args :supported-property :property-props ::hydra/readonly)
                    writeonly(-> % :args :supported-property :property-props ::hydra/writeonly)
                    property (last (-> % :args :supported-property :property :common-props ::hydra/id))
                    required (-> % :args :supported-property :property-props ::hydra/required)
                    predicate (-> % :ret)
                    mode (cond
                           readonly  :read
                           writeonly :write
                           :else     :update)]
                (and (or (not required)
                         (invalid? (predicate mode {} {})))
                     (or (not required)
                         (invalid? (predicate mode {} {property []})))
                     (invalid? (predicate mode {} {property [{"@value" 1} {"@value" 2}]})))
                true)))
(defn parse-supported-link
  "Creates a specification for a hydra link"
  [api supported-property]
  (let [link (:property supported-property)
        property-validator (parse-link api link)]
    (with-access-mode-validation supported-property
      (fn [mode api-validations jsonld]
        (let [link-id (-> link :common-props ::hydra/id)
              is-optional (not (-> supported-property :property-props ::hydra/required))
              values (get jsonld link-id [])
              value (first values)]
          (cond
            (= 0 (count values)) (if is-optional
                                   nil
                                   (->ValidationError :link-property-error
                                                      supported-property
                                                      (str "Missing mandatory property " link-id)))
            (> 1 (count values)) (->ValidationError :link-property-error
                                                    supported-property
                                                    (str "Cardinality error, more than one link value for property " link-id))
            :else                   (let [validation-result (property-validator mode api-validations value)]
                                      (if (nil? validation-result)
                                        validation-result
                                        (->ValidationError :link-property-error
                                                           {:supported-property supported-property
                                                            :nested-validation-error validation-result}
                                                           (str "Erroneous link for supported property link" link-id))))))))))

(s/fdef parse-plain-property
        :args (s/cat :api ::hydra/ApiDocumentation
                     :supported-property (s/with-gen (s/and ::hydra/SupportedProperty
                                                            #(and (not (-> % :property :is-link))
                                                                  (not (-> % :property :is-template))))
                                           #(tg/fmap (fn [prop]
                                                       (-> prop
                                                           (assoc-in [:property :is-link] false)
                                                           (assoc-in [:property :is-template] false)
                                                           (assoc-in [:property :uri] (resolve "rdf:Property"))))
                                                     (s/gen ::hydra/SupportedProperty))))
        :ret ::predicate
        :fn (s/and
             #(let [readonly  (-> % :args :supported-property :property-props ::hydra/readonly)
                    writeonly(-> % :args :supported-property :property-props ::hydra/writeonly)
                    property  (last (-> % :args :supported-property :property :common-props ::hydra/id))
                    required  (-> % :args :supported-property :property-props ::hydra/required)
                    predicate (-> % :ret)
                    mode (cond
                           readonly  :read
                           writeonly :write
                           :else     :update)]
                (and (or (not required)
                         (invalid? (predicate mode {} {})))
                     (or (not required)
                         (invalid? (predicate mode {} {property []})))
                     (invalid? (predicate mode {} {property [{"@value" 1} {"@value" 2}]}))))))
(defn parse-plain-property
  "Creates a specification for a hydra property"
  [api supported-property]
  (let [property (:property supported-property)
        property-validator (parse-property api property)]
    (log/debug "Validating property " property)
    (log/debug "Validating property " (-> property :common-props ::hydra/id))
    (with-access-mode-validation supported-property
      (fn [mode api-validations jsonld]
        (log/debug "Validating plain property " (-> property :common-props ::hydra/id))
        (let [property-id (-> property :common-props ::hydra/id)
              is-optional (not (-> supported-property :property-props ::hydra/required))
              values (get jsonld property-id)
              value (first values)]
          (log/debug {:is-optional is-optional
                      :values (count values)
                      :value value})
          (cond
            (= 0 (count values))  (if is-optional
                                    nil
                                    (->ValidationError :property-error supported-property (str "Missing mandatory property " property)))
            (> 1 (count values))  (->ValidationError :property-error supported-property (str "Cardinality error, more than one link value for property " property))
            :else                   (let [validation-result (property-validator mode api-validations value)]
                                      (if (nil? validation-result)
                                        validation-result
                                        (->ValidationError :property-error
                                                           {:supported-property supported-property
                                                            :nested-validation-error validation-result}
                                                           (str "Erroneous value for supported property" property-id))))))))))


(s/fdef parse-supported-property
        :args (s/cat :api ::hydra/ApiDocumentation
                     :supported-property ::hydra/SupportedProperty)
        :ret ::predicate)
(defn parse-supported-property
  "Creates a specification for a hydra supported-property"
  [api supported-property]
  (cond
    (-> supported-property :property :is-link) (parse-supported-link api supported-property)
    (-> supported-property :property :is-template) (fn [_ _ _] nil)
    :else  (parse-plain-property api supported-property)))


(s/fdef parse-supported-class
        :args (s/cat :api ::hydra/ApiDocumentation
                     :api-class ::hydra/SupportedClass)
        :ret ::predicate)
(defn parse-supported-class
  "Creates a specification for a hydra supported class"
  [api api-class]
  (let [validations (map #(parse-supported-property api %) (-> api-class :supported-properties))]
    (fn [mode api-validations jsonld]
      (log/debug "Validating " (-> api-class :common-props ::hydra/id) ", mode " mode " and " (count validations) " validations")
      (and (map? jsonld)
           (let [errors (->> validations
                             (map (fn [validation]
                                    (validation mode api-validations jsonld)))
                             (filter #(not (nil? %))))]
             (if (empty? errors)
               nil
               (->ValidationError :invalid-payload errors (str "Errors (" (count errors) ") found during validation"))))))))

(s/fdef parse-supported-collection
        :args (s/cat :api ::hydra/ApiDocumentation
                     :api-class ::hydra/Collection)
        :ret ::predicate)
(defn parse-supported-collection
  "Creates a specification for a hydra supported class"
  [api api-collection]
  (let [member-class (-> api-collection :member-class)]
    (if (nil? member-class)
      (parse-supported-class api api-collection)
      (let [api-class (hydra/find-model api member-class)]
        (let [member-validation (parse-supported-class api api-class)
              collection-class-validation (parse-supported-class api api-collection)]
          (fn [mode api-validations jsonld]
            (log/debug "Validating collection " (-> api-collection :common-props ::hydra/id) ", mode " mode)
            (and (map? jsonld)
                 (let [collection-errors (collection-class-validation mode api-validations jsonld)
                       members (get jsonld (resolve "hydra:member") [])
                       member-errors (->> members
                                          (map (fn [member] (member-validation mode api-validations member)))
                                          (filter some?))
                       total-errors (filter some? (concat [collection-errors] member-errors))]
                   (if (empty? total-errors)
                     nil
                     (->ValidationError :invalid-collection-payload total-errors (str "Errors (" (count total-errors) ") found during validation")))))))))))

(s/fdef build-api-validations
        :args (s/cat :apiDocumentation ::hydra/ApiDocumentation)
        :ret ::ValidationsMap)
(defn build-api-validations [api]
  (let [classes (:supported-classes api)]
    (reduce (fn [acc class]
              (let [uri (-> class :common-props ::hydra/id)
                    is-collection (hydra/collection-model? class)
                    validation (if is-collection
                                 (parse-supported-collection api class)
                                 (parse-supported-class api class))]
                (assoc acc uri validation)))
            {}
            classes)))


(defn valid-instance? [mode instance {:keys [supported-classes]}]
  (if (some? (get instance "@type"))
    (let [validations (build-api-validations {:supported-classes supported-classes})
          types (flatten [(get instance "@type")])]
      (->> types
           (map (fn [type] (if (some? (get validations type))
                            [type ((get validations type) mode validations instance)]
                            nil)))
           (filter some?)
           (into {})))
    (throw (ex-info (str "Cannot validate json-ld document without type")))))
