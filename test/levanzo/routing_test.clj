(ns levanzo.routing-test
  (:require [clojure.test :refer :all]
            [levanzo.routing :as routing]
            [clojure.spec :as s]))

(deftest absolute-path?-test
  (let [absolute-path ["/users" :user-id]
        relative-path ["users" :user-id]]
    (is (s/valid? ::routing/path absolute-path))
    (is (s/valid? ::routing/path relative-path))
    (is (routing/absolute-path? absolute-path))
    (is (not (routing/absolute-path? relative-path)))))


(deftest concat-path-test
  (let [root-path ["/users" :user-id]
        nested-tickets-path ["tickets" :ticket-id]
        events-path ["/events"]]
    (is (s/valid? ::routing/path root-path))
    (is (s/valid? ::routing/path nested-tickets-path))
    (is (s/valid? ::routing/path events-path))
    (is (= ["/users" :user-id "tickets" :ticket-id]
           (routing/concat-path root-path nested-tickets-path)))
    (is (= ["/events"]
           (routing/concat-path root-path events-path)))))
