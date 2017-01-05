(ns levanzo.schema-test
  (:require [clojure.test :refer :all]
            [levanzo.schema :as schema]
            [levanzo.hydra :as hydra]
            [levanzo.spec.jsonld :as jsonld-spec]
            [levanzo.namespaces :refer [xsd]]
            [levanzo.spec.utils :as spec-utils]
            [clojure.spec :as s]
            [clojure.spec.gen :as gen]
            [clojure.spec.test :as stest]
            [clojure.test.check.generators :as tg]))

(defn make-xsd-type-gen [type]
  (condp = type
    (xsd "string") (tg/fmap (fn [v] {"@value" v
                                    "@type" (xsd "string")})
                            (s/gen string?))
    (xsd "float")  (tg/fmap (fn [v] {"@value" v
                                    "@type" (xsd "float")})
                            (s/gen float?))
    (xsd "decimal") (tg/fmap
                     (fn [v] {"@value" v
                             "@type" (xsd "decimal")})
                     (s/gen integer?))
    (xsd "boolean") (tg/fmap
                     (fn [v] {"@value" v
                             "@type" (xsd "boolean")})
                     (s/gen boolean?))
    (first (s/gen (s/or
                   :string string?
                   :number number?
                   :boolean boolean?)))))

(defn make-valid-payload-gen [class]
  (tg/fmap (fn [properties]
             (let [m (->> properties
                          (map (fn [[k v]]
                                 (if (nil? v)
                                   [k []]
                                   (if (string? v)
                                     [k [{"@id" v}]]
                                     [k [v]]))))
                          (into {}))]
               (-> m
                   (assoc "@id" "http://test.com/generated")
                   (assoc "@type" [(-> class :common-props ::hydra/id)]))))
           (tg/bind
            (tg/return (-> class :supported-properties))
            (fn [properties]
              (let [generators (->> properties
                                    (mapv (fn [{:keys [property-props property is-link]}]
                                            (let [required (-> property-props ::hydra/required)
                                                  range (-> property-props ::hydra/range)]
                                              (tg/tuple (tg/return property)
                                                        ;;(tg/return required)
                                                        ;;boolean?
                                                        ;;(tg/return is-link)
                                                        (if required
                                                          (if is-link
                                                            (s/gen ::jsonld-spec/uri)
                                                            (if (schema/xsd-uri? range)
                                                              (make-xsd-type-gen range)
                                                              (s/gen ::jsonld-spec/uri)))
                                                          (tg/return nil)))))))]
                (apply tg/tuple generators))))))

(defn make-xsd-property-gen [type required]
  (tg/fmap (fn [property]
             (-> property
                 (assoc :operations [])
                 (assoc-in [:property-props ::hydra/required] required)
                 (assoc-in [:property-props ::hydra/range] type)
                 (assoc :is-link false)
                 (assoc :is-template false)))
           (s/gen ::hydra/SupportedProperty)))


(defn make-link-property-gen [target required]
  (tg/fmap (fn [[property operation]]
             (let [operation (-> operation
                                 (assoc-in [:operation-props ::hydra/method] "GET")
                                 (assoc-in [:operation-props ::hydra/returns] target))]
               (-> property
                   (assoc :operations [operation])
                   (assoc-in [:property-props ::hydra/required] required)
                   (assoc-in [:property-props ::hydra/range] target)
                   (assoc :is-link true)
                   (assoc :is-template false))))
           (tg/tuple
            (s/gen ::hydra/SupportedProperty)
            (s/gen ::hydra/Operation))))

(defn make-properties-map-gen [max-properties]
  (tg/vector
   (tg/bind (s/gen (s/tuple #{:literal :link}
                            boolean?))
            (fn [[kind required]]
              (condp = kind
                :literal (tg/bind (s/gen ::jsonld-spec/datatype)
                                  (fn [type]
                                    (make-xsd-property-gen type required)))
                :link (tg/bind (s/gen ::jsonld-spec/uri)
                               (fn [uri]
                                 (make-link-property-gen uri required))))))
   max-properties))

(defn make-class-gen [uri max-properties]
  (tg/fmap
   (fn [[title description type properties]]
     (hydra/class {::hydra/id uri
                   ::hydra/type type
                   ::hydra/title title
                   ::hydra/description description
                   ::hydra/operations []
                   ::hydra/supported-properties properties}))
   (tg/tuple
            (s/gen ::hydra/title)
            (s/gen ::hydra/description)
            (s/gen ::hydra/type)
            (make-properties-map-gen max-properties))))


(deftest check-xsd-range-test
  (spec-utils/check-symbol `schema/check-xsd-range)
  (is (thrown? Exception (schema/check-xsd-range "http://test.com/foo" {"@value" "test"}))))

(deftest check-range-test
  (spec-utils/check-symbol `schema/check-range))

(deftest parse-supported-link-test
  (spec-utils/check-symbol `schema/parse-supported-link))


 (deftest parse-plain-property-test
   (spec-utils/check-symbol `schema/parse-plain-property))

(deftest parse-supported-property-test
   (spec-utils/check-symbol `schema/parse-supported-property))

(deftest parse-supported-cass-test
  (let [klasses (take 5 (gen/sample (make-class-gen "http://test.com/Test" 15)))]
    (doseq [klass klasses]
      (doseq [instance (gen/sample (make-valid-payload-gen klass) 10)]
        (let [errors ((schema/parse-supported-class {} klass) instance)
              valid (nil? errors)]
          (when (not valid)
            (prn instance)
            (prn errors))
          (is valid))))))
