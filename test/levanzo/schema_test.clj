(ns levanzo.schema-test
  (:require [clojure.test :refer :all]
            [levanzo.schema :as schema]
            [levanzo.hydra :as hydra]
            [levanzo.spec.schema :as schema-spec]
            [levanzo.spec.utils :as spec-utils]
            [clojure.spec :as s]
            [clojure.spec.gen :as gen]))


(deftest check-xsd-range-test
  (spec-utils/check-symbol `schema/check-xsd-range)
  ;(is (thrown? Exception (schema/check-xsd-range "http://test.com/foo" {"@value" "test"})))
  )

(deftest check-range-test
  (spec-utils/check-symbol `schema/check-range))

(deftest parse-supported-link-test
  (spec-utils/check-symbol `schema/parse-supported-link))

 (deftest parse-plain-property-test
   (spec-utils/check-symbol `schema/parse-plain-property))

(deftest parse-supported-property-test
   (spec-utils/check-symbol `schema/parse-supported-property))

(deftest parse-supported-class-test
  (let [klasses (take 3 (gen/sample (schema-spec/make-class-gen "http://test.com/Test" 15)))]
    (doseq [klass klasses]
      (doseq [mode [:read :write :update]]
        (doseq [instance (gen/sample (schema-spec/make-payload-gen mode klass nil) 10)]
          (let [errors ((schema/parse-supported-class {} klass) mode {} instance)
                valid (nil? errors)]
            (is valid)))))))

(deftest parse-supported-class-api-test
  (doseq [api (take-last 3 (gen/sample (schema-spec/make-api-tree-gen) 15))]
    (s/valid? ::hydra/ApiDocumentation api)
    (let [validations-map (schema/build-api-validations api)]
      (doseq [klass (:supported-classes api)]
        (doseq [mode [:read :write :update]]
          (doseq [instance (gen/sample (schema-spec/make-payload-gen mode klass api) 10)]
            (let [validation (get validations-map (-> klass :common-props ::hydra/id))
                  errors (validation mode validations-map instance)
                  valid (nil? errors)]
              (is valid))))))))

(deftest parse-plain-property-test
  (spec-utils/check-symbol `schema/parse-plain-property))
