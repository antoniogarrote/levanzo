(ns levanzo.hydra-test
  (:require [clojure.spec :as s]
            [clojure.test :refer :all]
            [clojure.spec.gen :as gen]
            [clojure.spec.test :as stest]
            [levanzo.hydra :as hydra]))

(deftest uri-test
  (is (s/valid? ::hydra/uri "http://test.com/something"))
  (is (s/valid? ::hydra/uri "https://test.com/something"))
  (is (s/valid? ::hydra/uri "file://192.168.40.10/other/thing"))
  (is (not (s/valid? ::hydra/uri "this is not a URI"))))

(deftest curie-test
  (is (s/valid? ::hydra/curie "test:com"))
  (is (s/valid? ::hydra/curie ":name"))
  (is (not (s/valid? ::hydra/uri "this is not a CURIE")))
  (is (not (s/valid? ::hydra/uri ":"))))

;;(gen/generate (s/gen ::hydra/operation))

(deftest handler-test
  (let [handler (fn [args body request] {})]
    (is (s/valid? ::hydra/handler handler))))

(defn operation-jsonld
  ([method expects returns]
   (->> {"@type" "hydra:Operation"
         "hydra:method" method
         "hydra:expects" expects
         "hydra:returns" returns}
        (filter (fn [[k v]] (not (nil? v))))
        (into {})))
  ([] (operation-jsonld "GET"))
  ([method] (operation-jsonld method nil nil)))

(deftest operation-test
  (let [op (hydra/->Operation "hydra:Operation"
                              "GET"
                              "test:expects"
                              "test:returns"
                              (fn [args body request]
                                "operation handler"))]
    (is (s/valid? ::hydra/Operation op))
    (s/exercise ::hydra/Operation)
    (is (= "GET" (:method op)))
    (is (= "hydra:Operation" (:term op)))
    (is (= "operation handler" ((:handler op) nil nil nil)))
    (is (= "test:expects" (:expects op)))
    (is (= "test:returns" (:returns op)))
    (is (= (operation-jsonld "GET" "test:expects" "test:returns")
           (hydra/->jsonld op)))))


(deftest operation-helper-tests
  (let [null-handler (fn [_ _ _] true)
        expected {"GET" hydra/get-operation
                  "POST" hydra/post-operation
                  "PATCH" hydra/patch-operation
                  "PUT" hydra/put-operation
                  "DELETE" hydra/delete-operation}]
    (doseq [[expected-method operation-fn] expected]
      (let [operation (operation-fn null-handler)]
        (is (= "hydra:Operation" (:term operation)))
        (is (= expected-method (:method operation)))
        (is ((:handler operation) nil nil nil))
        (is (= (operation-jsonld expected-method) (hydra/->jsonld operation)))))))

(deftest property-jsonld-tests
  (let [options {::hydra/readonly true
                 ::hydra/writeonly false
                 ::hydra/required true
                 ::hydra/domain "test:Domain"
                 ::hydra/range "test:Range"}
        null-operation (hydra/get-operation (fn [_ _ _] true))
        prop (hydra/->jsonld (hydra/property "test:prop" options))
        link (hydra/->jsonld (hydra/link "test:link" options [null-operation]))
        template (hydra/->jsonld (hydra/template-link "test:template" options [null-operation]))]

    (is (= "hydra:Link" (-> link (get "hydra:property") (get "@type"))))
    (is (= "hydra:TemplatedLink" (-> template (get "hydra:property") (get "@type"))))
    (is (= "rdf:Property" (-> prop (get "hydra:property") (get "@type"))))

    (doseq [supported [prop link template]]
      (is (= true (-> supported (get "hydra:required"))))
      (is (= true (-> supported (get "hydra:readonly"))))
      (is (= false (-> supported (get "hydra:writeonly"))))
      (is (= "test:Domain" (-> supported (get "hydra:property") (get "rdfs:domain"))))
      (is (= "test:Range" (-> supported (get "hydra:property") (get "rdfs:range")))))))

(deftest test-checkable-syms
  (let [{:keys [total check-passed] :as results}
        (-> (stest/check (stest/checkable-syms) {:clojure.spec.test.check/opts {:num-tests 5}})
            stest/summarize-results)]
    (prn results)
    (is (= total check-passed))))

(comment


  (hydra/link "test:hey" {} [])

  (def s (gen/sample (s/gen `hydra/link)))

  (s/exercise-fn `hydra/link)

  (-> (stest/check [`hydra/link `hydra/get-operation] {:clojure.spec.test.check/opts {:num-tests 2}})
      stest/summarize-results
      )

  (gen/generate (s/gen ::hydra/operations))

  (def args (gen/generate (s/gen (s/cat :property ::hydra/property
                                        :hydra-property-options ::hydra/hydra-property-options
                                        :operations (s/coll-of ::hydra/Operation)))))

  args
  (s/conform ::hydra/operations [(hydra/->Operation "a:blah" "GET" nil nil (fn [a b c] c))])
  (count args)
  (nth args 3)
  (first s)
  (gen/sample (s/gen ::hydra/Operation))

  (gen/generate (s/gen ::hydra/term))

  (def t (gen/generate (s/gen ::hydra/handler)))

  (def t (gen/generate (s/gen (s/cat :options (s/keys :opt [::hydra/expects ::hydra/returns])
                                     :handler ::hydra/handler))))

  (def g (gen/generate (s/gen `hydra/get-operation)))
  g

  ((:handler (g {} (fn [_ _ _] true))) {} nil nil)

  (gen/sample (s/gen (s/or :a int?
                           :b string?)))


  (prn )
  (prn ())
  )
