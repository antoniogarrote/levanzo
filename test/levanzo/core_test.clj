(ns levanzo.core-test
  (:require [clojure.test :refer :all]
            [levanzo.spec.utils :as spec-utils]))

(deftest test-checkable-syms
  (spec-utils/trace-is-checked-syms))
