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
  (spec-utils/check-symbol `schema/parse-supported-link))


 (deftest parse-plain-property-test
   (spec-utils/check-symbol `schema/parse-plain-property))

(deftest parse-supported-property
   (spec-utils/check-symbol `schema/parse-supported-property))


(comment
  (spec-utils/check-symbol `schema/parse-supported-property)

  (gen/sample (tg/tuple (s/gen ::hydra/ApiDocumentation)
                        (s/gen ::jsonld-spec/datatype)
                        (s/gen ::jsonld-spec/jsonld-literal)) 1)
  )
