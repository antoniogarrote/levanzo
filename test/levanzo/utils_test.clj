(ns levanzo.utils-test
  (:require [clojure.test :refer :all]
            [levanzo.spec.utils :as spec-utils]
            [levanzo.utils :as utils]))

(deftest test-checkable-syms
  (spec-utils/is-checked-syms))
