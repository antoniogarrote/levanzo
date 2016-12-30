(ns levanzo.core-test
  (:require [clojure.test :refer :all]
            [levanzo.core :refer :all]
            [levanzo.spec.utils :as spec-utils]))

(deftest test-checkable-syms
  (spec-utils/is-checked-syms))
