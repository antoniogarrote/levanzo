(ns levanzo.hydra-test
  (:require [clojure.spec :as s]
            [clojure.test :refer :all]
            [levanzo.namespaces :as lns]
            [levanzo.spec.utils :as spec-utils]
            [levanzo.hydra :as hydra]))

(lns/default-ns "http://test.com#")
(lns/register "test" "http://test.com#")

(deftest handler-test
  (let [handler (fn [args body request] {})]
    (is (s/valid? ::hydra/handler handler))))

(deftest generic->jsonld-test
  (is (= {"@id" "http://test.com#test"} (hydra/generic->jsonld {::hydra/id "http://test.com#test"} {})))
  (is (= {"http://www.w3.org/ns/hydra/core#title" "test"} (hydra/generic->jsonld {::hydra/title "test"} {})))
  (is (= {"http://www.w3.org/ns/hydra/core#title" "test"} (hydra/generic->jsonld {::hydra/title "test"} {"http://www.w3.org/ns/hydra/core#title" "other"})))
  (is (= {"@type" "http://test.com#test"} (hydra/generic->jsonld {::hydra/type "http://test.com#test"} {})))
  (is (= {"@type" ["http://test.com#other" "http://test.com#test"]} (hydra/generic->jsonld {::hydra/type "http://test.com#test"} {"@type" "http://test.com#other"}))))

(defn operation-jsonld
  ([method expects returns]
   (->> {"@type" "http://www.w3.org/ns/hydra/core#Operation"
         "http://www.w3.org/ns/hydra/core#method" method
         "http://www.w3.org/ns/hydra/core#expects" (if (some? expects) {"@id" expects} nil)
         "http://www.w3.org/ns/hydra/core#returns" (if (some? returns) {"@id" returns} nil)}
        (filter (fn [[k v]] (not (nil? v))))
        (into {})))
  ([] (operation-jsonld "GET"))
  ([method] (operation-jsonld method nil nil)))

(deftest method-test
  (let [methods ["GET" "POST" "PUT" "PATCH" "DELETE" "OPTIONS" "HEAD" "OTHER"]
        invalid ["get" "post" "G3T"]]
    (doseq [method methods]
      (is (s/valid? ::hydra/method method)))
    (doseq [method invalid]
      (is (not (s/valid? ::hydra/method method))))))

(deftest operation-test
  (let [op (hydra/map->Operation {:uri "http://www.w3.org/ns/hydra/core#Operation"
                                  :common-props {}
                                  :operation-props {::hydra/method "GET"
                                                    ::hydra/expects "http://test.com#expects"
                                                    ::hydra/returns "http://test.com#returns"}})]
    (is (s/valid? ::hydra/Operation op))
    (is (= "GET" (-> op :operation-props ::hydra/method)))
    (is (= "http://www.w3.org/ns/hydra/core#Operation" (:uri op)))
    (is (= "http://test.com#expects" (-> op :operation-props ::hydra/expects)))
    (is (= "http://test.com#returns" (-> op :operation-props ::hydra/returns)))
    (is (= (operation-jsonld "GET" "http://test.com#expects" "http://test.com#returns")
           (hydra/->jsonld op)))
    (is (s/valid? ::hydra/Operation (assoc op
                                           :handler (fn [a b r] nil))))))

(deftest operation-helper-tests
  (let [null-handler (fn [_ _ _] true)
        expected {"GET" hydra/get-operation
                  "POST" hydra/post-operation
                  "PATCH" hydra/patch-operation
                  "PUT" hydra/put-operation
                  "DELETE" hydra/delete-operation}]
    (doseq [[expected-method operation-fn] expected]
      (let [operation (operation-fn {::hydra/handler null-handler})]
        (is (= "http://www.w3.org/ns/hydra/core#Operation" (:uri operation)))
        (is (= expected-method (-> operation :operation-props ::hydra/method)))
        (is ((:handler operation) nil nil nil))
        (is (= (operation-jsonld expected-method) (hydra/->jsonld operation)))))))

(deftest property-jsonld-tests
  (let [null-operation (hydra/get-operation {::hydra/handler (fn [_ _ _] true)})
        prop (hydra/property  {::hydra/id "http://test.com#prop"
                               ::hydra/domain "http://test.com#Domain"
                               ::hydra/range "http://test.com#Range"})
        link (hydra/link {::hydra/id "http://test.com#link"
                          ::hydra/domain "http://test.com#Domain"
                          ::hydra/range "http://test.com#Range"})
        template (hydra/templated-link {::hydra/id "http://test.com#template"
                                        ::hydra/domain "http://test.com#Domain"
                                        ::hydra/range "http://test.com#Range"})
        options {::hydra/readonly true
                 ::hydra/writeonly false
                 ::hydra/required true
                 ::hydra/operations [null-operation]}
        supported-property (hydra/->jsonld (hydra/supported-property (-> options (assoc ::hydra/property prop) (assoc ::hydra/operations []))))
        supported-link (hydra/->jsonld (hydra/supported-property (assoc options ::hydra/property link)))
        supported-template (hydra/->jsonld (hydra/supported-property (assoc options ::hydra/property template)))]

    (is (= "http://www.w3.org/ns/hydra/core#Link" (-> supported-link (get "http://www.w3.org/ns/hydra/core#property") (get "@type"))))
    (is (= "http://www.w3.org/ns/hydra/core#TemplatedLink" (-> supported-template (get "http://www.w3.org/ns/hydra/core#property") (get "@type"))))
    (is (= "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property" (-> supported-property (get "http://www.w3.org/ns/hydra/core#property") (get "@type"))))

    (doseq [supported [supported-property supported-link supported-template]]
      (is (= true (-> supported (get "http://www.w3.org/ns/hydra/core#required"))))
      (is (= true (-> supported (get "http://www.w3.org/ns/hydra/core#readonly"))))
      (is (= false (-> supported (get "http://www.w3.org/ns/hydra/core#writeonly"))))
      (is (= "http://test.com#Domain" (-> supported (get "http://www.w3.org/ns/hydra/core#property") (get "http://www.w3.org/2000/01/rdf-schema#domain") (get "@id"))))
      (is (= "http://test.com#Range" (-> supported (get "http://www.w3.org/ns/hydra/core#property") (get "http://www.w3.org/2000/01/rdf-schema#range") (get "@id")))))))

(def test-class (hydra/class {::hydra/id "http://test.com#MyClass"
                              ::hydra/title "MyClass"
                              ::hydra/description "Test class"
                              ::hydra/operations [(hydra/delete-operation {::hydra/title "Destroys a MyClass instance"
                                                                           ::hydra/handler (fn [_ _ _] "Destroyed")})]
                              ::hydra/supported-properties [(hydra/supported-property {::hydra/property (hydra/property {::hydra/id "http://xmlns.com/foaf/0.1/name"
                                                                                                                         ::hydra/range "http://www.w3.org/2001/XMLSchema#string"})
                                                                                       ::hydra/required true})
                                                            (hydra/supported-property {::hydra/property (hydra/property  {::hydra/id "http://xmlns.com/foaf/0.1/age"
                                                                                                                          ::hydra/range "http://www.w3.org/2001/XMLSchema#decimal"})})]}))
(deftest class-jsonld-tests
  (is (= {"@type" "http://www.w3.org/ns/hydra/core#Class",
          "http://www.w3.org/ns/hydra/core#supportedProperty"
          [{"@type" "http://www.w3.org/ns/hydra/core#SupportedProperty",
            "http://www.w3.org/ns/hydra/core#property"
            {"@id" "http://xmlns.com/foaf/0.1/name",
             "@type" "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property",
             "http://www.w3.org/2000/01/rdf-schema#range"
             {"@id" "http://www.w3.org/2001/XMLSchema#string"},
             "http://www.w3.org/ns/hydra/core#supportedOperation" []},
            "http://www.w3.org/ns/hydra/core#required" true}
           {"@type" "http://www.w3.org/ns/hydra/core#SupportedProperty",
            "http://www.w3.org/ns/hydra/core#property"
            {"@id" "http://xmlns.com/foaf/0.1/age",
             "@type" "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property",
             "http://www.w3.org/2000/01/rdf-schema#range"
             {"@id" "http://www.w3.org/2001/XMLSchema#decimal"},
             "http://www.w3.org/ns/hydra/core#supportedOperation" []}}],
          "http://www.w3.org/ns/hydra/core#supportedOperation"
          [{"@type" "http://www.w3.org/ns/hydra/core#Operation",
            "http://www.w3.org/ns/hydra/core#method" "DELETE",
            "http://www.w3.org/ns/hydra/core#title"
            "Destroys a MyClass instance"}],
          "@id" "http://test.com#MyClass",
          "http://www.w3.org/ns/hydra/core#title" "MyClass",
          "http://www.w3.org/ns/hydra/core#description" "Test class"}
         (hydra/->jsonld test-class))))

(deftest collection-jsonld-tests
  (let [jsonld (hydra/->jsonld (hydra/collection {::hydra/id "http://test.com#MyCollection"
                                                  ::hydra/title "My Collection"
                                                  ::hydra/description "A test collection"
                                                  ::hydra/is-paginated false
                                                  ::hydra/member-class "http://test.com#Class"}))]
    (is (= "http://test.com#MyCollection" (get jsonld "@id")))
    (is (= "My Collection") (get jsonld "http://www.w3.org/ns/hydra/core#title"))
    (is (= "A test collection" (get jsonld "http://www.w3.org/ns/hydra/core#description")))
    (is (= #{"http://www.w3.org/ns/hydra/core#Class" "http://www.w3.org/ns/hydra/core#Collection"} (into #{} (get jsonld "@type"))))
    (is (= "http://test.com#Class" (get jsonld "lvz:memberClass")))))

(deftest api-documentation-jsonld-tests
  (let [jsonld (hydra/->jsonld (hydra/api {::hydra/id "http://test.com#MyApi"
                                           ::hydra/entrypoint "/entrypoint"
                                           ::hydra/entrypoint-class (-> test-class
                                                                        :common-props
                                                                        ::hydra/id)
                                           ::hydra/title "My Api"
                                           ::hydra/description "Test API"
                                           ::hydra/supported-classes [test-class]}))]
    (is (= "http://test.com#MyApi" (get jsonld "@id")))
    (is (= "/entrypoint" (get jsonld "http://www.w3.org/ns/hydra/core#entrypoint")))
    (is (= (-> test-class :common-props ::hydra/id)
           (get jsonld "lvz:entrypointClass")))
    (is (some? (get jsonld "lvz:entrypointClass")))
    (is (= "My Api" (get jsonld "http://www.w3.org/ns/hydra/core#title")))
    (is (= "Test API" (get jsonld "http://www.w3.org/ns/hydra/core#description")))
    (is (= "http://www.w3.org/ns/hydra/core#ApiDocumentation" (get jsonld "@type")))
    (is (= 1 (count (get jsonld "http://www.w3.org/ns/hydra/core#supportedClass"))))
    (is (= "http://test.com#MyClass" (-> jsonld (get "http://www.w3.org/ns/hydra/core#supportedClass") first (get "@id"))))))
