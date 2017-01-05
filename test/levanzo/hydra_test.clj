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
         "http://www.w3.org/ns/hydra/core#expects" expects
         "http://www.w3.org/ns/hydra/core#returns" returns}
        (filter (fn [[k v]] (not (nil? v))))
        (into {})))
  ([] (operation-jsonld "GET"))
  ([method] (operation-jsonld method nil nil)))

(deftest operation-test
  (let [op (hydra/->Operation "http://www.w3.org/ns/hydra/core#Operation"
                              {}
                              {::hydra/method "GET"
                               ::hydra/expects "http://test.com#expects"
                               ::hydra/returns "http://test.com#returns"}
                              (fn [args body request]
                                "operation handler"))]
    (is (s/valid? ::hydra/Operation op))
    (is (= "GET" (-> op :operation-props ::hydra/method)))
    (is (= "http://www.w3.org/ns/hydra/core#Operation" (:uri op)))
    (is (= "operation handler" ((:handler op) nil nil nil)))
    (is (= "http://test.com#expects" (-> op :operation-props ::hydra/expects)))
    (is (= "http://test.com#returns" (-> op :operation-props ::hydra/returns)))
    (is (= (operation-jsonld "GET" "http://test.com#expects" "http://test.com#returns")
           (hydra/->jsonld op)))))

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
        options {::hydra/readonly true
                 ::hydra/writeonly false
                 ::hydra/required true
                 ::hydra/domain "http://test.com#Domain"
                 ::hydra/range "http://test.com#Range"
                 ::hydra/operations [null-operation]}

        prop (hydra/->jsonld (hydra/property (assoc options ::hydra/property "http://test.com#prop")))
        link (hydra/->jsonld (hydra/link (assoc options ::hydra/property "http://test.com#link")))
        template (hydra/->jsonld (hydra/template-link (assoc options ::hydra/property "http://test.com#template")))]

    (is (= "http://www.w3.org/ns/hydra/core#Link" (-> link (get "http://www.w3.org/ns/hydra/core#property") (get "@type"))))
    (is (= "http://www.w3.org/ns/hydra/core#TemplatedLink" (-> template (get "http://www.w3.org/ns/hydra/core#property") (get "@type"))))
    (is (= "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property" (-> prop (get "http://www.w3.org/ns/hydra/core#property") (get "@type"))))

    (doseq [supported [prop link template]]
      (is (= true (-> supported (get "http://www.w3.org/ns/hydra/core#required"))))
      (is (= true (-> supported (get "http://www.w3.org/ns/hydra/core#readonly"))))
      (is (= false (-> supported (get "http://www.w3.org/ns/hydra/core#writeonly"))))
      (is (= "http://test.com#Domain" (-> supported (get "http://www.w3.org/ns/hydra/core#property") (get "http://www.w3.org/2000/01/rdf-schema#domain"))))
      (is (= "http://test.com#Range" (-> supported (get "http://www.w3.org/ns/hydra/core#property") (get "http://www.w3.org/2000/01/rdf-schema#range")))))))

(def test-class (hydra/class {::hydra/id "http://test.com#MyClass"
                              ::hydra/title "MyClass"
                              ::hydra/description "Test class"
                              ::hydra/operations [(hydra/delete-operation {::hydra/title "Destroys a MyClass instance"
                                                                           ::hydra/handler (fn [_ _ _] "Destroyed")})]
                              ::hydra/supported-properties [(hydra/property {::hydra/property "http://xmlns.com/foaf/0.1/name"
                                                                             ::hydra/required true
                                                                             ::hydra/range "http://www.w3.org/2001/XMLSchema#string"})
                                                            (hydra/property {::hydra/property "http://xmlns.com/foaf/0.1/age"
                                                                             ::hydra/range "http://www.w3.org/2001/XMLSchema#decimal"})]}))
(deftest class-jsonld-tests
  (is (= {"@id" "http://test.com#MyClass"
          "@type" "http://www.w3.org/ns/hydra/core#Class"
          "http://www.w3.org/ns/hydra/core#title" "MyClass"
          "http://www.w3.org/ns/hydra/core#description" "Test class"
          "http://www.w3.org/ns/hydra/core#supportedProperty"
          [{"@type" "http://www.w3.org/ns/hydra/core#SupportedProperty"
            "http://www.w3.org/ns/hydra/core#property"
            {"@id" "http://xmlns.com/foaf/0.1/name"
             "@type" "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property"
             "http://www.w3.org/2000/01/rdf-schema#range" "http://www.w3.org/2001/XMLSchema#string"
             "http://www.w3.org/ns/hydra/core#supportedOperation" []}
            "http://www.w3.org/ns/hydra/core#required" true}
           {"@type" "http://www.w3.org/ns/hydra/core#SupportedProperty"
            "http://www.w3.org/ns/hydra/core#property"
            {"@id" "http://xmlns.com/foaf/0.1/age"
             "@type" "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property"
             "http://www.w3.org/2000/01/rdf-schema#range" "http://www.w3.org/2001/XMLSchema#decimal"
             "http://www.w3.org/ns/hydra/core#supportedOperation" []}}]
          "http://www.w3.org/ns/hydra/core#supportedOperation"
          [{"@type" "http://www.w3.org/ns/hydra/core#Operation"
            "http://www.w3.org/ns/hydra/core#method" "DELETE"
            "http://www.w3.org/ns/hydra/core#title" "Destroys a MyClass instance"}]}
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
