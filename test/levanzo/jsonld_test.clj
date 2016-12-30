(ns levanzo.jsonld-test
  (:require [clojure.test :refer :all]
            [levanzo.spec.utils :as spec-utils]
            [levanzo.jsonld :as jsonld]))

(deftest assoc-if-some-test
  (is (= {"@id" ["ho" "http://test.com/hey"]} (jsonld/assoc-if-some :id "@id" {:id "http://test.com/hey"} {"@id" "ho"})))
  (is (= {"@id" "http://test.com/hey"} (jsonld/assoc-if-some :id "@id" {:id "http://test.com/hey"} {})))
  (is (= {"@id" ["hey" "ho" "http://test.com/hey" ]} (jsonld/assoc-if-some :id "@id" {:id "http://test.com/hey"} {"@id" ["hey" "ho"]})))
  (is (= {"@id" ["hey" "http://test.com/hey" ]} (jsonld/assoc-if-some :id "@id" {:id "http://test.com/hey"} {"@id" ["hey" "http://test.com/hey"]}))))

(deftest generic->jsonld-test
  (is (= {"@id" "test"} (jsonld/generic->jsonld {:id "test"} {})))
  (is (= {"hydra:title" "test"} (jsonld/generic->jsonld {:title "test"} {})))
  (is (= {"hydra:title" "test"} (jsonld/generic->jsonld {:title "test"} {"hydra:title" "other"})))
  (is (= {"@type" "test"} (jsonld/generic->jsonld {:type "test"} {})))
  (is (= {"@type" ["other" "test"]} (jsonld/generic->jsonld {:type "test"} {"@type" "other"}))))
