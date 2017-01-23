(ns levanzo.indexing-test
  (:require [clojure.test :refer :all]
            [levanzo.hydra :as hydra]
            [levanzo.namespaces :refer [xsd] :as lns]
            [levanzo.payload :as payload]
            [levanzo.routing :as routing]
            [levanzo.indexing :as indexing]
            [clojure.string :as string]))

(def base "http://test.com/")

(defn vocab [x] (str base "vocab#" x))

(payload/context {:vocab (vocab "")
                  :base base})

(def name-prop (hydra/property {::hydra/id (vocab "name")
                                ::hydra/range (xsd "string")}))

(def email-prop (hydra/property {::hydra/id (vocab "email")
                                 ::hydra/range (xsd "string")}))

(def street-prop (hydra/property {::hydra/id (vocab "street")
                                  ::hydra/range (xsd "string")}))

(def postcode-prop (hydra/property {::hydra/id (vocab "postcode")
                                    ::hydra/range (xsd "string")}))

(def address-prop (hydra/link {::hydra/id (vocab "address")
                               ::hydra/range (vocab "AddressCollection")}))

(def Person (hydra/class {::hydra/id (vocab "Person")
                          ::hydra/supported-properties
                          [(hydra/supported-property {::hydra/property name-prop
                                                      ::hydra/required true})
                           (hydra/supported-property {::hydra/property email-prop
                                                      ::hydra/required true})
                           (hydra/supported-property {::hydra/id (vocab "address-link")
                                                      ::hydra/property address-prop
                                                      ::hydra/operations
                                                      [(hydra/get-operation {::hydra/returns (hydra/id Person)})]})]}))


(def Address (hydra/class {::hydra/id (vocab "Address")
                           ::hydra/supported-properties
                           [(hydra/supported-property {::hydra/property address-prop
                                                       ::hydra/required true})
                            (hydra/supported-property {::hydra/property postcode-prop
                                                       ::hydra/required true})]}))

(def AddressesCollection (hydra/collection  {::hydra/id (vocab "AddressesCollection")
                                             ::hydra/member-class (hydra/id Address)
                                             ::hydra/is-paginated false}))

(def PeopleCollection (hydra/collection  {::hydra/id (vocab "PeopleCollection")
                                          ::hydra/member-class (hydra/id Person)
                                          ::hydra/is-paginated false
                                          ::hydra/operations
                                          [(hydra/get-operation {::hydra/returns (hydra/id PeopleCollection)})]}))

(def API (hydra/api {::hydra/entrypoint "/people"
                     ::hydra/supported-classes [PeopleCollection
                                                AddressesCollection
                                                Person
                                                Address]}))

(declare get-people)
(declare get-person)
(declare addresses-for-person)
(declare get-address)

(def routes (routing/process-routes {:path ["people"]
                                     :model PeopleCollection
                                     :handlers {:get get-people}
                                     :nested [{:path ["/" :person-id]
                                               :model Person
                                               :handlers {:get get-person}
                                               :nested [{:path ["/address"]
                                                         :model (vocab "address-link")
                                                         :handlers {:get addresses-for-person}
                                                         :nested [{:path ["/" :address-id]
                                                                   :model Address
                                                                   :handlers {:get get-address}}]}]}]}))

(def address-instances (->> [(payload/jsonld
                              (payload/id {:model Address
                                           :args {:person-id 1
                                                  :address-id 1}})
                              (payload/supported-property {:property street-prop
                                                           :value "Old St. 17b"})
                              (payload/supported-property {:property postcode-prop
                                                           :value "NW1 PJJ"}))
                             (payload/jsonld
                              (payload/id {:model Address
                                           :args {:person-id 1
                                                  :address-id 2}})
                              (payload/supported-property {:property street-prop
                                                           :value "New St. 45"})
                              (payload/supported-property {:property postcode-prop
                                                           :value "NW3 BBa"}))
                             (payload/jsonld
                              (payload/id {:model Address
                                           :args {:person-id 2
                                                  :address-id 3}})
                              (payload/supported-property {:property street-prop
                                                           :value "South St. 1"})
                              (payload/supported-property {:property postcode-prop
                                                           :value "S1 8NZ"}))]
                            (mapv payload/expand)))

(def person-instances (->> [(payload/jsonld
                             (payload/id {:model Person
                                          :args {:person-id 1}})
                             (payload/supported-property {:property name-prop
                                                          :value "Tim"})
                             (payload/supported-property {:property email-prop
                                                          :value "me@tim.com"})
                             (payload/supported-link {:property address-prop
                                                      :model (vocab "address-link")
                                                      :args {:person-id 1}}))
                            (payload/jsonld
                             (payload/id {:model Person
                                          :args {:person-id 2}})
                             (payload/supported-property {:property name-prop
                                                          :value "John"})
                             (payload/supported-property {:property email-prop
                                                          :value "me@john.com"})
                             (payload/supported-link {:property address-prop
                                                      :model (vocab "address-link")
                                                      :args {:person-id 2}}))]
                           (mapv payload/expand)))

;; handlers

(defn get-people [args body request]
  (payload/jsonld
   (payload/id {:model PeopleCollection})
   (payload/members person-instances)))


(defn get-person [args body request]
  (->> person-instances
       (filter #(= (get % "@id") (payload/link-for {:model Person
                                                    :args args
                                                    :base base})))
       first))

(defn addresses-for-person [args body request]
  (let [person-id (payload/link-for {:model Person :args args :base base})
        addresses (->> address-instances
                       (filter #(string/starts-with? (get % "@id") person-id)))]
    (payload/jsonld
     (payload/id {:model (vocab "address-link")
                  :args args})
     (payload/members addresses))))

(defn get-address [args body request]
  (->> address-instances
       (filter #(= (get % "@id") (payload/link-for {:model Address :args args :base base})))
       first))

;; filters

(defn filter-collection [c]
  (fn [{:keys [subject]}]
    {:results (->> c (filter #(= (get % "@id") subject)) first)}))

(defn filter-predicate [c]
  (fn [{:keys [predicate object]}]
    {:results (->> c
                   (filter #(if (nil? object)
                              (some? (get % predicate))
                              (some (fn [obj] (= obj object)) (get % predicate)))))}))

(defn addresses-for-person-join [{:keys [subject object]}]
  (let [addresses (filter #(string/starts-with? (get % "@id") subject)
                          address-instances)
        object-set (set (or (mapv #(get % "@id") addresses) []))]
    {:results (if (nil? object)
                addresses
                (filter #(some? (object-set (get % "@id"))) object))}))


(def indices {Person {:resource (filter-collection person-instances)
                      :properties {email-prop {:index (filter-predicate person-instances)}}
                      :links {(vocab "address-link") addresses-for-person-join}}

              Address {:resource (filter-collection address-instances)
                       :properties {postcode-prop {:index (filter-predicate address-instances)}}}})

;; tests

(deftest get-person-test
  (is (= "http://test.com/people/1" (get (get-person {:person-id 1} {} {}) "@id"))))


(deftest addresses-for-person-test
  (is (= 1 (count (get (addresses-for-person {:person-id 2} {} {}) (lns/hydra "member")))))
  (is (= 0 (count (get (addresses-for-person {:person-id 5} {} {}) (lns/hydra "member"))))))

(deftest get-address-test
  (is (some? (get-address {:person-id 1 :address-id 1} {} {})))
  (is (nil? (get-address {:person-id 1 :address-id 6} {} {}))))

(deftest person-lookup
  (let [id (payload/link-for {:model Person
                              :args {:person-id 1}
                              :base base})
        result (:results ((-> indices (get Person) :resource) {:subject id}))]
    (is (= id (get result "@id")))))

(deftest email-prop-index
  (let [index (-> indices (get Person) :properties (get email-prop) :index)]
    (is (= 2 (count (:results (index {:predicate (hydra/id email-prop)})))))
    (is (= 1 (count (:results (index {:predicate (hydra/id email-prop)
                                      :object {"@value" "me@tim.com"}})))))))

(deftest address-lookup
  (let [id (payload/link-for {:model Address
                              :args {:person-id 1
                                     :address-id 1}
                              :base base})
        result (:results ((-> indices (get Address) :resource) {:subject id}))]
    (is (= id (get result "@id")))))

(deftest postcode-prop-index
  (let [index (-> indices (get Address) :properties (get postcode-prop) :index)]
    (is (= 3 (count (:results (index {:predicate (hydra/id postcode-prop)})))))))

(deftest person-address-index
  (let [index (-> indices (get Person) :links (get (vocab "address-link")))]
    (is (= 2 (count (:results (index {:subject (payload/link-for {:model Person
                                                                  :args  {:person-id 1}
                                                                  :base base})})))))
    (is (= 2 (count (:results (index {:subject (payload/link-for {:model Person
                                                                  :args  {:person-id 1}
                                                                  :base base})
                                      :object [(payload/id {:model Address
                                                            :args  {:person-id 1
                                                                    :address-id 1}
                                                            :base base})
                                               (payload/id {:model Address
                                                            :args  {:person-id 1
                                                                    :address-id 2}
                                                            :base base})
                                               (payload/id {:model Address
                                                            :args  {:person-id 1
                                                                    :address-id 5}
                                                            :base base})]})))))))

(deftest make-handlers-test
  (do (clojure.spec/check-asserts true)
      (let [index (indexing/api-index indices)
            {:keys [resource-handlers
                    properties-handlers
                    join-handlers]} (indexing/build-pattern-handlers API index)]
        (is (= 2 (count resource-handlers)))
        (= [(vocab "Address") (vocab "Person")]
           (->> resource-handlers
                (map (fn [h] (-> h :model :common-props ::hydra/id)))))
        (= [(vocab "Address") (vocab "Person")]
           (->> properties-handlers
                (map (fn [h] (-> h :model :common-props ::hydra/id)))))
        (= [(vocab "postcode") (vocab "email")]
           (->> properties-handlers
                (map (fn [h] (-> h :property-model :common-props ::hydra/id))))))))

(deftest indexing-test
  (do (clojure.spec/check-asserts true)
      (let [index (indexing/api-index indices)
            indexer (indexing/make-indexer API index)
            response (indexer {:s {"@id" (payload/link-for {:model Person
                                                           :args {:person-id 1}
                                                           :base base})}
                              :p nil
                              :o nil}
                             {:page 1
                              :per-page 5
                              :group 0}
                             {})]
        (is (= 3 (-> response :results count)))
        (is (= 2 (-> response :page)))
        (is (= 0 (-> response :group)))
        (let [response (indexer {:s {"@id" (payload/link-for {:model Person
                                                              :args {:person-id 1}
                                                              :base base})}
                                 :p nil
                                 :o nil}
                                {:page 2
                                 :per-page 5
                                 :group 0}
                                {})]
          (is (= 0 (-> response :results count)))
          (is (= nil (-> response :page)))
          (is (= nil (-> response :group)))))))


(deftest predicate-indexing-test
  (do (clojure.spec/check-asserts true)
      (let [index (indexing/api-index indices)
            indexer (indexing/make-indexer API index)
            response (indexer {:s nil
                               :p {"@id" (hydra/id email-prop)}
                               :o nil}
                              {:page 1
                               :per-page 5
                               :group 0}
                              {})
            response2 (indexer {:s nil
                                :p {"@id" (hydra/id email-prop)}
                                :o {"@value" "me@john.com"}}
                              {:page 1
                               :per-page 5
                               :group 0}
                              {})]
        (is (= 2 (count (:results response))))
        (is (= ["http://test.com/people/1" "http://test.com/people/2"]
               (->> response
                    :results
                    (map #(get-in % [:s "@id"])))))
        (is (= ["me@tim.com" "me@john.com"]
               (->> response
                    :results
                    (map #(get-in % [:o "@value"])))))
        (is (= ["me@john.com"]
               (->> response2
                    :results
                    (map #(get-in % [:o "@value"]))))))))


(deftest join-indexing-test
  (do (clojure.spec/check-asserts true)
      (let [index (indexing/api-index indices)
            indexer (indexing/make-indexer API index)
            response (indexer {:s (payload/id {:model Person
                                               :args {:person-id 1}
                                               :base base})
                               :p {"@id" (hydra/id address-prop)}
                               :o nil}
                              {:page 1
                               :per-page 5
                               :group 0}
                              {})
            response2 (indexer {:s (payload/id {:model Person
                                                :args {:person-id 1}
                                                :base base})
                                :p {"@id" (hydra/id address-prop)}
                                :o (payload/id {:model Address
                                                :args {:person-id 1
                                                       :address-id 1}
                                                :base base})}
                               {:page 1
                                :per-page 5
                                :group 0}
                               {})]
        (is (= 2 (count (:results response))))
        (is (= 1 (count (:results response2)))))))
