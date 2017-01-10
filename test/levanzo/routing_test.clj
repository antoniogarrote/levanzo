(ns levanzo.routing-test
  (:require [clojure.test :refer :all]
            [levanzo.routing :as routing]
            [levanzo.spec.utils :as spec-utils]
            [clojure.spec :as s]
            [bidi.bidi :as bidi]))


(deftest process-routes-test
  (reset! routing/routes-register {})
  (is (spec-utils/check-symbol `routing/process-routes))
  (reset! routing/routes-register {})
  (let [handler (fn [a b c] b)
        routes (routing/process-routes {:path ["/"]
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
                                                  :handlers {:get handler}}]})]
    (is (= (keyword "/vocab#Class") (:handler (bidi/match-route routes "/"))))
    (is (= (keyword "/vocab#Nested1") (:handler (bidi/match-route routes "/nested1"))))
    (is (= (keyword "/vocab#Nested2") (:handler (bidi/match-route routes "/nested1/nested2/3"))))
    (is (= (keyword "/vocab#Nested3")) (:handler (bidi/match-route routes "/nested3/4")))))
