(ns levanzo.schema-test
  (:require [clojure.test :refer :all]
            [levanzo.schema :as schema]
            [levanzo.spec.utils :as spec-utils]
            [clojure.spec :as s]
            [clojure.spec.test :as stest]))

(deftest check-xsd-range-test
  (spec-utils/check-symbol `schema/check-xsd-range)
  (is (thrown? Exception (schema/check-xsd-range "http://test.com/foo" {"@value" "test"}))))
