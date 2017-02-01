(ns levanzo.payload-test
  (:require [clojure.test :refer :all]
            [levanzo.payload :as payload]
            [levanzo.namespaces :as lns]
            [levanzo.spec.utils :as spec-utils]
            [clojure.spec.test :as stest]))

(deftest context-test
  (spec-utils/check-symbol `payload/context)
  (with-bindings {#'lns/*ns-register* (atom {"hydra" "http://www.w3.org/ns/hydra/core#"})}
    (is (= {"hydra" "http://www.w3.org/ns/hydra/core#", "rdfs" "http://www.w3.org/2000/01/rdf-schema#" "xsd" "http://www.w3.org/2001/XMLSchema#" "sh" "http://www.w3.org/ns/shacl#"}
           (payload/context {})))
    (is (= {"hydra" "http://www.w3.org/ns/hydra/core#", "rdfs" "http://www.w3.org/2000/01/rdf-schema#" "xsd" "http://www.w3.org/2001/XMLSchema#"  "sh" "http://www.w3.org/ns/shacl#"}
           (payload/context {:ns []})))
    (is (= {"@base" "http://test.com/base"
            "@vocab" "http://test.com/base#vocab"
            "rdfs" "http://www.w3.org/2000/01/rdf-schema#"
            "hydra" "http://www.w3.org/ns/hydra/core#"
            "xsd" "http://www.w3.org/2001/XMLSchema#"
            "sh" "http://www.w3.org/ns/shacl#"}
           (payload/context {:base "http://test.com/base"
                             :vocab "http://test.com/base#vocab"
                             :ns [:hydra]})))))

(deftest compact-test
  (spec-utils/check-symbol `payload/compact)
  (is (= {"test" {"@id" "test/1"},
          "@context" {"@base" "http://test.com/",
                      "@vocab" "http://test.com/vocab#"
                      "hydra" "http://www.w3.org/ns/hydra/core#",
                      "rdfs" "http://www.w3.org/2000/01/rdf-schema#"
                      "xsd" "http://www.w3.org/2001/XMLSchema#"
                      "sh" "http://www.w3.org/ns/shacl#"}}
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
  (spec-utils/check-symbol `payload/jsonld)
  (with-bindings {#'payload/*context* (atom {"test" "http://test.com/"})}
    (is (= {"@id" "test1"
            "http://test.com/hey" [{"@value" 3}]}
           (payload/jsonld
            ["@id" "test1"]
            ["test:hey" 3])))))

(deftest supported-property-test
  (spec-utils/check-symbol `payload/supported-property))

(deftest supported-link-test
  (stest/instrument `levanzo.routing/link-for {:stub #{`levanzo.routing/link-for}})
  (spec-utils/check-symbol `payload/supported-link)
  (stest/unstrument))

(deftest partial-view-test
  (stest/instrument `levanzo.routing/link-for {:stub #{`levanzo.routing/link-for}})
  (spec-utils/check-symbol `payload/partial-view)
  (let [view (->> [(payload/partial-view  {:model "http://test.com/Collection"
                                           :current 3
                                           :next 4
                                           :previous 2
                                           :pagination-param "p"
                                           :first 0
                                           :last 100})]
                  (into {}))]
    (is (some? (get view "http://www.w3.org/ns/hydra/core#view")))
    (is (= ["@id" "@type"
            "http://www.w3.org/ns/hydra/core#first"
            "http://www.w3.org/ns/hydra/core#last"
            "http://www.w3.org/ns/hydra/core#next"
            "http://www.w3.org/ns/hydra/core#previous"]
           (keys (get view "http://www.w3.org/ns/hydra/core#view"))))
    (stest/unstrument)))


(deftest supported-template-test
  (spec-utils/check-symbol `payload/supported-template)
  (is (= ["http://test.com/template-link"
          {"@type" "http://www.w3.org/ns/hydra/core#IriTemplate",
           "http://www.w3.org/ns/hydra/core#template" "/test/{id}{?q}",
           "http://www.w3.org/ns/hydra/core#variableRepresentation" "BasicRepresentation",
           "http://www.w3.org/ns/hydra/core#mapping" [{"@type" "http://www.w3.org/ns/hydra/core#IriTemplateMapping",
                                                       "http://www.w3.org/ns/hydra/core#variable" "id",
                                                       "http://www.w3.org/ns/hydra/core#property" {"@type" "http://www.w3.org/2000/01/rdf-schema#Property",
                                                                                                   "http://www.w3.org/2000/01/rdf-schema#range" {"@id" "http://www.w3.org/2001/XMLSchema#string"}},
                                                       "http://www.w3.org/ns/hydra/core#required" true}
                                                      {"@type" "http://www.w3.org/ns/hydra/core#IriTemplateMapping",
                                                       "http://www.w3.org/ns/hydra/core#variable" "q",
                                                       "http://www.w3.org/ns/hydra/core#property" {"@type" "http://www.w3.org/2000/01/rdf-schema#Property",
                                                                                                   "http://www.w3.org/2000/01/rdf-schema#range" {"@id" "http://www.w3.org/2001/XMLSchema#string"}},
                                                       "http://www.w3.org/ns/hydra/core#required" false}]}]
         (payload/supported-template {:property "http://test.com/template-link"
                                      :template "/test/{id}{?q}"
                                      :representation :basic
                                      :mapping [{:variable :id
                                                 :range (levanzo.namespaces/xsd "string")
                                                 :required true}
                                                {:variable :q
                                                 :range (levanzo.namespaces/xsd "string")
                                                 :required false}]}))))


(deftest filter-triples
  (let [triples (->> (payload/jsonld
                      ["@id" "http://test.com/1"]
                      ["hydra:title" "test"]
                      ["hydra:description" "description"])
                     payload/->triples)]
    (is (= 1 (count (payload/filter-triples {:s nil
                                             :p {"@id" (lns/hydra "title")}
                                             :o nil}
                                            triples))))
    (is (= [{:s {"@id" "http://test.com/1"},
             :p {"@id" "http://www.w3.org/ns/hydra/core#title"},
             :o (payload/expand-literal "test")}]
           (payload/fill-pattern {:s nil
                                  :p {"@id" (lns/hydra "title")}
                                  :o nil}
                                 triples)))))
