(ns levanzo.spec.utils
  (:require [clojure.test :refer :all]
            [clojure.spec.test :as stest]
            [clojure.string :as string]))

(def num-tests (Integer/parseInt (or (System/getenv "NUM_TESTS") "20")))

(defn is-checked-syms
  "Checks all public symbols in the library"
  []
  (let [{:keys [total check-passed] :as results}
        (-> (stest/checkable-syms)
            (->> (filter (fn [sym] (and (nil? (string/index-of (str sym) "routing"))
                                       (nil? (string/index-of (str sym) "schema"))
                                       (nil? (string/index-of (str sym) "payload"))
                                       (nil? (string/index-of (str sym) "indexing"))
                                       ))))
            (stest/check {:clojure.spec.test.check/opts {:num-tests num-tests}})
            stest/summarize-results)]
    (is (= total check-passed))))


(defn trace-is-checked-syms
  "Checks all public symbols in the library"
  []
  (let [symbols (->> (stest/checkable-syms)
                     (filter (fn [sym]
                               (and (nil? (string/index-of (str sym) "routing"))
                                    (nil? (string/index-of (str sym) "schema"))
                                    (nil? (string/index-of (str sym) "payload"))
                                    (nil? (string/index-of (str sym) "indexing"))
                                    ))))]
    (doseq [symbol symbols]
      (let [{:keys [total check-passed] :as results} (time (-> (stest/check symbol {:clojure.spec.test.check/opts {:num-tests num-tests}})
                                                               (stest/summarize-results)))]
        (is (= total check-passed))))))

(defn check-symbol
  [sym]
  (let [{:keys [total check-passed] :as results} (stest/summarize-results
                                                  (stest/check sym {:clojure.spec.test.check/opts {:num-tests num-tests}}))]
    (is (= total check-passed))))
