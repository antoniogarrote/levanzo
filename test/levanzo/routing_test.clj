(ns levanzo.routing-test
  (:require [clojure.test :refer :all]
            [levanzo.routing :as routing]
            [levanzo.spec.utils :as spec-utils]
            [clojure.spec :as s]
            [bidi.bidi :as bidi]))

(def handler (fn [a b c] b))
(def test-routes {:path ["/"]
                  :model "/vocab#Class"
                  :handlers {:get handler}
                  :nested [{:path ["nested1"]
                            :model "/vocab#Nested1"
                            :handlers {:get handler}
                            :nested [{:path ["/nested2/" :id]
                                      :model "/vocab#Nested2"
                                      :handlers {:get handler}}]}
                           {:path ["nested3/" :id]
                            :model "/vocab#Nested3"
                            :handlers {:get handler}}]})

(deftest process-routes-test
  (routing/clear!)
  (is (spec-utils/check-symbol `routing/process-routes))
  (routing/clear!)
  (let [routes (routing/process-routes test-routes)]
    (is (= (keyword "/vocab#Class") (:handler (bidi/match-route routes "/"))))
    (is (= (keyword "/vocab#Nested1") (:handler (bidi/match-route routes "/nested1"))))
    (is (= (keyword "/vocab#Nested2") (:handler (bidi/match-route routes "/nested1/nested2/3"))))
    (is (= (keyword "/vocab#Nested3")) (:handler (bidi/match-route routes "/nested3/4")))))

(deftest link-for-test
  (routing/clear!)
  (let [routes (routing/process-routes test-routes)]
    (is (= "/" (routing/link-for "/vocab#Class")))
    (is (= "/nested1/nested2/5" (routing/link-for "/vocab#Nested2" :id 5)))))
