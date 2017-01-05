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

(deftest check-xsd-range-test
  (spec-utils/check-symbol `schema/check-xsd-range)
  (is (thrown? Exception (schema/check-xsd-range "http://test.com/foo" {"@value" "test"}))))

(deftest check-range-test
  (spec-utils/check-symbol `schema/check-range))


(deftest parse-supported-link-test
  (let [iterations spec-utils/num-tests
        apis (take iterations (gen/sample (s/gen ::hydra/ApiDocumentation)))
        properties (take iterations (gen/sample (s/gen (s/and ::hydra/SupportedProperty
                                                  #(:is-link %)))))
        property-ids (map (fn [property] (-> property :property)) properties)
        required-properties (map (fn [property] (-> property :property-props ::hydra/required)) properties)
        predicates (map (fn [api property]
                          (schema/parse-supported-link api property))
                        apis
                        properties)
        results (map (fn [required predicate property-id]
                       (and (or (not required)
                                (schema/invalid? (predicate {})))
                            (or (not required)
                                (schema/invalid? (predicate {property-id []})))
                            (schema/invalid? (predicate {property-id [{"@value" 1} {"@value" 2}]}))))
                     required-properties
                     predicates
                     property-ids)]
    (is (reduce (fn [acc result] (and acc result)) true results))
    (spec-utils/check-symbol `schema/parse-supported-link)))
