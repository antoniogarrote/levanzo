(ns levanzo.spec.utils
  (:require [clojure.test :refer :all]
            [clojure.spec.test :as stest]))

(def num-tests 5)

(defn is-checked-syms
  "Checks all public symbols in the library"
  []
  (let [{:keys [total check-passed] :as results}
        (-> (stest/check (stest/checkable-syms) {:clojure.spec.test.check/opts {:num-tests num-tests}})
            stest/summarize-results)]
    (prn results)
    (is (= total check-passed))))
