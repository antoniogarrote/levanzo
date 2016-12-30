(ns levanzo.jsonld-test
  (:require [clojure.test :refer :all]
            [levanzo.spec.utils :as spec-utils]
            [levanzo.jsonld :as jsonld]))

(deftest assoc-if-some-test
  (is (= {"@id" ["ho" "http://test.com/hey"]} (jsonld/assoc-if-some :id "@id" {:id "http://test.com/hey"} {"@id" "ho"})))
  (is (= {"@id" "http://test.com/hey"} (jsonld/assoc-if-some :id "@id" {:id "http://test.com/hey"} {})))
  (is (= {"@id" ["hey" "ho" "http://test.com/hey" ]} (jsonld/assoc-if-some :id "@id" {:id "http://test.com/hey"} {"@id" ["hey" "ho"]})))
  (is (= {"@id" ["hey" "http://test.com/hey" ]} (jsonld/assoc-if-some :id "@id" {:id "http://test.com/hey"} {"@id" ["hey" "http://test.com/hey"]}))))
