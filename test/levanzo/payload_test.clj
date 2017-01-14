(ns levanzo.payload-test
  (:require [clojure.test :refer :all]
            [levanzo.payload :as payload]
            [levanzo.namespaces :as lns]
            [levanzo.spec.utils :as spec-utils]
            [clojure.spec.test :as stest]))

(deftest context-test
  (spec-utils/check-symbol `payload/context)
  (with-bindings {#'lns/*ns-register* (atom {"hydra" "http://www.w3.org/ns/hydra/core#"})}
    (is (= {} (payload/context {})))
    (is (= {} (payload/context {:ns []})))
    (is (= {"@base" "http://test.com/base"
            "@vocab" "http://test.com/base#vocab"
            "hydra" "http://www.w3.org/ns/hydra/core#"}
           (payload/context {:base "http://test.com/base"
                             :vocab "http://test.com/base#vocab"
                             :ns [:hydra]})))))

(deftest compact-test
  (spec-utils/check-symbol `payload/compact)
  (is (= {"test" {"@id" "test/1"},
          "@context" {"@base" "http://test.com/",
                      "@vocab" "http://test.com/vocab#"}}
       (with-bindings {#'payload/*context* (atom {"@base" "http://test.com/"
                                                  "@vocab" "http://test.com/vocab#"})}
         (payload/compact {"http://test.com/vocab#test" {"@id" "http://test.com/test/1"}}))))
  (is (= {"test" {"@id" "test/1"}}
         (with-bindings {#'payload/*context* (atom {"@base" "http://test.com/"
                                                    "@vocab" "http://test.com/vocab#"})}
           (payload/compact {"http://test.com/vocab#test" {"@id" "http://test.com/test/1"}}
                            {:context false})))))

(deftest expand-test
  (spec-utils/check-symbol `payload/expand))

(deftest uri-or-model-test
  (spec-utils/check-symbol `payload/uri-or-model))

(deftest link-for-test
  (stest/instrument `levanzo.routing/link-for {:stub #{`levanzo.routing/link-for}})
  (spec-utils/check-symbol `payload/link-for)
  (stest/unstrument))

(deftest jsonld-test
  (spec-utils/check-symbol `payload/json-ld)
  (with-bindings {#'payload/*context* (atom {"test" "http://test.com/"})}
    (is (= {"@id" "test1"
            "http://test.com/hey" [{"@value" 3}]}
           (payload/json-ld
            ["@id" "test1"]
            ["test:hey" 3])
           ))))

(deftest supported-property-test
  (spec-utils/check-symbol `payload/supported-property))

(deftest supported-link-test
  (stest/instrument `levanzo.routing/link-for {:stub #{`levanzo.routing/link-for}})
  (spec-utils/check-symbol `payload/supported-link)
  (stest/unstrument))
