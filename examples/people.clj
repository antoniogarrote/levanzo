(ns examples.people)

(require '[clojure.string :as string])

;; let's check the structure of the arguments
(clojure.spec/check-asserts true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 1. Setting up your API namespace ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(require '[levanzo.namespaces :as lns]
         '[clojure.test :refer [is]])

;; base URL where the API will be served
(def base (or (System/getenv "BASE_URL") "http://localhost:8080/"))

;; registering the namespace for our vocabulary at /vocab#
(lns/define-rdf-ns vocab (str base "vocab#"))

;; registering schema org vocabulary
(lns/define-rdf-ns sorg "http://schema.org/")

;; tests
(is (= "http://localhost:8080/vocab#Test") (vocab "Test"))
(is (= "http://schema.org/Person") (sorg "Person"))
(is (= "http://schema.org/Person") (lns/resolve "sorg:Person"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 2. Describing you API            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(require '[levanzo.hydra :as hydra])
(require '[levanzo.xsd :as xsd])

;; declaring properties
(def sorg-street-address (hydra/property {::hydra/id (sorg "streetAddress")
                                          ::hydra/title "streetAddress"
                                          ::hydra/description "The street address"
                                          ::hydra/range xsd/string}))

(def sorg-postal-code (hydra/property {::hydra/id (sorg "postalCode")
                                       ::hydra/title "postalCode"
                                       ::hydra/description "The postal code"
                                       ::hydra/range xsd/string}))

;; The PostalAddress class
(def sorg-PostalAddress (hydra/class {::hydra/id (sorg "PostalAddress")
                                      ::hydra/title "PostalAddress"
                                      ::hydra/description "The mailing address"
                                      ::hydra/supported-properties
                                      [(hydra/supported-property
                                        {::hydra/property sorg-street-address
                                         ::hydra/required true})
                                       (hydra/supported-property
                                        {::hydra/property sorg-postal-code})]}))

;; People -> address -> PostalAddress link
(def sorg-address (hydra/link {::hydra/id (sorg "address")
                               ::hydra/title "address"
                               ::hydra/description "Physical address of the resource"
                               ::hydra/range (hydra/id sorg-PostalAddress)}))


;; Properties for People
(def sorg-name (hydra/property {::hydra/id (sorg "name")
                                ::hydra/title "name"
                                ::hydra/description "The name of the resource"
                                ::hydra/range xsd/string}))


(def sorg-email (hydra/property {::hydra/id (sorg "email")
                                 ::hydra/title "email"
                                 ::hydra/description "Email address"
                                 ::hydra/range xsd/string}))

(def vocab-password (hydra/property {::hydra/id (vocab "password")
                                     ::hydra/title "password"
                                     ::hydra/description "Secret passworld"
                                     ::hydra/range xsd/string}))

;; The Person class
(def person-address-link (hydra/supported-property {::hydra/id (vocab "address-link")
                                                    ::hydra/property sorg-address
                                                    ::hydra/readonly true
                                                    ::hydra/operations
                                                    [(hydra/get-operation
                                                      {::hydra/returns (hydra/id sorg-PostalAddress)})
                                                     (hydra/post-operation
                                                      {::hydra/expects (hydra/id sorg-PostalAddress)
                                                       ::hydra/returns (hydra/id sorg-PostalAddress)})
                                                     (hydra/delete-operation {})]}))

(def sorg-Person (hydra/class {::hydra/id (sorg "Person")
                               ::hydra/title "Person"
                               ::hydra/description "A person"
                               ::hydra/supported-properties
                               [(hydra/supported-property
                                 {::hydra/property sorg-name})
                                (hydra/supported-property
                                 {::hydra/property sorg-email
                                  ::hydra/required true})
                                (hydra/supported-property
                                 {::hydra/property vocab-password
                                  ::hydra/required true
                                  ::hydra/writeonly true})
                                person-address-link]
                               ::hydra/operations
                               [(hydra/get-operation {::hydra/returns (hydra/id sorg-Person)})
                                (hydra/delete-operation {})]}))

;;(clojure.pprint/pprint (hydra/->jsonld person-address-link))
;;(:operations sorg-Person)
;; Working with payloads

(require '[levanzo.payload :as payload])

(def address (payload/jsonld
              ["@type" (hydra/id sorg-PostalAddress)]
              [(hydra/id sorg-street-address) {"@value" "Finchley Road 523"}]
              [(hydra/id sorg-postal-code)    {"@value" "NW3 7PB"}]))

(def address-alt (payload/instance
                  sorg-PostalAddress
                  (payload/supported-property {:property sorg-street-address
                                               :value "Finchley Road 523"})
                  (payload/supported-property {:property sorg-postal-code
                                               :value "NW3 7PB"})))
(is (= address address-alt))

(require '[levanzo.schema :as schema])

;; valid
(schema/valid-instance? :read
                        (payload/instance
                         sorg-PostalAddress
                         (payload/supported-property {:property sorg-street-address
                                                      :value "Finchley Road 523"})
                         (payload/supported-property {:property sorg-postal-code
                                                      :value "NW3 7PB"}))
                        {:supported-classes [sorg-PostalAddress]})

;; valid, postal code is optional on read
(schema/valid-instance? :read
                        (payload/instance
                         sorg-PostalAddress
                         (payload/supported-property {:property sorg-street-address
                                                      :value "Finchley Road 523"}))
                        {:supported-classes [sorg-PostalAddress]})

;; invalid, streeet addres has range string
(schema/valid-instance? :read
                        (payload/instance
                         sorg-PostalAddress
                         (payload/supported-property {:property sorg-street-address
                                                      :value 523}))
                        {:supported-classes [sorg-PostalAddress]})

;; invalid, streeet addres is mandatory on read
(schema/valid-instance? :read
                        (payload/instance
                         sorg-PostalAddress
                         (payload/supported-property {:property sorg-postal-code
                                                      :value "NW3 7PB"}))
                        {:supported-classes [sorg-PostalAddress]})

;; valid
(schema/valid-instance? :read
                        (payload/instance
                         sorg-Person
                         (payload/supported-property {:property sorg-name
                                                      :value "Tim"})
                         (payload/supported-property {:property sorg-email
                                                      :value "timbl@w3.org"}))
                        {:supported-classes [sorg-Person]})

;; invalid, password is mandatory on write
(schema/valid-instance? :write
                        (payload/instance
                         sorg-Person
                         (payload/supported-property {:property sorg-name
                                                      :value "Tim"})
                         (payload/supported-property {:property sorg-email
                                                      :value "timbl@w3.org"}))
                        {:supported-classes [sorg-Person]})


;; valid
(schema/valid-instance? :write
                        (payload/instance
                         sorg-Person
                         (payload/supported-property {:property sorg-name
                                                      :value "Tim"})
                         (payload/supported-property {:property sorg-email
                                                      :value "timbl@w3.org"})
                         (payload/supported-property {:property vocab-password
                                                      :value "~asd332fnxzz"}))
                        {:supported-classes [sorg-Person]})

;; invalid, password is writeonly
(schema/valid-instance? :read
                        (payload/instance
                         sorg-Person
                         (payload/supported-property {:property sorg-name
                                                      :value "Tim"})
                         (payload/supported-property {:property sorg-email
                                                      :value "timbl@w3.org"})
                         (payload/supported-property {:property vocab-password
                                                      :value "~asd332fnxzz"}))
                        {:supported-classes [sorg-Person]})

;; Generating instances
(require '[clojure.spec.gen :as gen] :reload
         '[levanzo.spec.schema :as schema-spec] :reload)


(clojure.pprint/pprint (last (gen/sample (schema-spec/make-payload-gen :read sorg-Person {:supported-classes [sorg-Person]}) 100)))
;; {"http://schema.org/email"
;;  [{"@value" "p6a2b11qHl4NH47On1xyf5KR4onN1zyb68",
;;    "@type" "http://www.w3.org/2001/XMLSchema#string"}],
;;  "http://schema.org/address"
;;  [{"@id"
;;    "https://15.165.15.23/1Xq35/zCm1b/70TBU/4EYLx/S3DB4/1aVcM/x29f5/n6844"}],
;;  "@id" "https://0.1.1.19/66xvB/x6tET/B90U9/U1sx0/cqjCc/RhLJg/h/3f2rP/8HfJ1",
;;  "@type" ["http://schema.org/Person"]}


;; Overwriting generators
(require '[clojure.test.check.generators :as tg])

(clojure.pprint/pprint (last (gen/sample (schema-spec/make-payload-gen
                                          :read
                                          sorg-Person
                                          {:supported-classes [sorg-Person]}
                                          {(hydra/id sorg-email) (tg/return {"@value" "test@test.com"})
                                           (hydra/id sorg-name)  (tg/return {"@value" "Constant Name"})
                                           (hydra/id sorg-address) (tg/return {"@id" "http://test.com/constant_address"})
                                           "@id" (tg/return "http://test.com/generated")}) 100)))
;; {"http://schema.org/name" [{"@value" "Constant Name"}],
;; "http://schema.org/email" [{"@value" "test@test.com"}],
;; "http://schema.org/address" [{"@id" "http://test.com/constant_address"}],
;; "@id" "http://test.com/generated",
;; "@type" ["http://schema.org/Person"]}

;; Collections

(def vocab-PeopleCollection (hydra/collection {::hydra/id (vocab "PeopleCollection")
                                                ::hydra/title "People Collection"
                                                ::hydra/description "Collection of all people in the API"
                                                ::hydra/member-class (hydra/id sorg-Person)
                                                ::hydra/is-paginated false
                                                ::hydra/operations
                                                [(hydra/get-operation {::hydra/returns (hydra/id vocab-PeopleCollection)})
                                                 (hydra/post-operation {::hydra/expects (hydra/id sorg-Person)
                                                                        ::hydra/returns (hydra/id sorg-Person)})]}))

(def people (payload/instance
             vocab-PeopleCollection
             (payload/members [(payload/instance
                                sorg-Person
                                (payload/supported-property {:property sorg-name
                                                             :value "Tim"})
                                (payload/supported-property {:property sorg-email
                                                             :value "timbl@w3.org"}))
                               (payload/instance
                                sorg-Person
                                (payload/supported-property {:property sorg-name
                                                             :value "bob"})
                                (payload/supported-property {:property sorg-email
                                                             :value "bob@w3.org"}))])))

(def people-instance (last (gen/sample (schema-spec/make-payload-gen :read vocab-PeopleCollection
                                                                     {:supported-classes [sorg-Person vocab-PeopleCollection]}
                                                                     100))))

(schema/valid-instance? :read (payload/expand people-instance) {:supported-classes [vocab-PeopleCollection
                                                                   sorg-Person]})

(payload/context)
(schema/valid-instance? :read (payload/expand (payload/compact people)) {:supported-classes [vocab-PeopleCollection
                                                          sorg-Person]})

;; Working with the context
(payload/context {:base base
                  :vocab (vocab)
                  :ns ["vocab"]
                  "id" "@id"
                  "type" "@type"
                  "Person" {"@id" (sorg "Person")}
                  "name" {"@id" (sorg "name")}
                  "email" {"@id" (sorg "email")}})

;; expansion and compaction of JSON-LD documents
(def tim (payload/instance
          sorg-Person
          ["@id" (str base "tim")]
          (payload/supported-property {:property sorg-name
                                       :value "Tim"})
          (payload/supported-property {:property sorg-email
                                       :value "timbl@w3.org"})))
(clojure.pprint/pprint (-> tim payload/compact (dissoc "@context")))
;;{"id" "tim",
;; "type" "Person",
;; "email" "timbl@w3.org",
;; "name" "Tim"}

(clojure.pprint/pprint (payload/expand tim))
;; {"@id" "http://localhost:8080/tim",
;;  "@type" ["http://schema.org/Person"],
;;  "http://schema.org/email" [{"@value" "timbl@w3.org"}],

(is (= (payload/expand tim) (-> tim payload/compact payload/expand)))

;; More explicit context so Markus Hydra console will work correctly
(payload/context {:base base
                  :vocab (vocab)
                  :ns ["vocab"]
                  "Person" {"@id" (sorg "Person")}
                  "name" {"@id" (sorg "name")}
                  "email" {"@id" (sorg "email")}
                  "address" {"@id" (sorg "address")}})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 3. Routes and links              ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; API definition
(def API (hydra/api {::hydra/id "people"
                     ::hydra/title "People Example API"
                     ::hydra/description "A toy API to demonstrate how to use Levanzo and Hydra"
                     ::hydra/entrypoint "/people"
                     ::hydra/entrypoint-class (hydra/id vocab-PeopleCollection)
                     ::hydra/supported-classes [vocab-PeopleCollection
                                                sorg-Person
                                                sorg-PostalAddress]}))


(require '[levanzo.http :as http] :reload)

;; let's check the structure of the arguments to middleware
(clojure.spec/check-asserts true)

(def people-db (atom {"http://localhost:8080/people/1"
                      {"@id" "http://localhost:8080/people/1",
                       "@type" ["http://schema.org/Person"],
                       "http://schema.org/address"
                       [{"@id" "http://localhost:8080/people/1/address"}],
                       "http://schema.org/email" [{"@value" "blah@test.com"}],
                       "http://schema.org/name" [{"@value" "Person1"}]}
                      "http://localhost:8080/people/2"
                      {"@id" "http://localhost:8080/people/2",
                       "@type" ["http://schema.org/Person"],
                       "http://schema.org/address"
                       [{"@id" "http://localhost:8080/people/2/address"}],
                       "http://schema.org/email" [{"@value" "bloh@test.com"}],
                       "http://schema.org/name" [{"@value" "Person2"}]}}))
(def addresses-db (atom {"http://localhost:8080/people/1/address"
                         {"@id" "http://localhost:8080/people/1/address",
                          "@type" ["http://schema.org/PostalAddress"],
                          "http://schema.org/postalCode" [{"@value" "NW3 7PB"}],
                          "http://schema.org/streetAddress" [{"@value" "Finchley Road 523"}]}}))

(defn get-people [args body request]
  (payload/instance
   vocab-PeopleCollection
   (payload/id {:model vocab-PeopleCollection
                :base base})
   (payload/members (vals @people-db))))

(defn post-person [args body request]
  (swap! people-db
         #(let [id (inc (count %))
                new-person (-> body
                               ;; passwords are writeonly, we don't store them
                               (dissoc (hydra/id vocab-password))
                               (merge (payload/id
                                       {:model sorg-Person
                                        :args {:person-id id}
                                        :base base}))
                               (merge (payload/supported-link
                                       {:property sorg-address
                                        :model person-address-link
                                        :args {:person-id id}
                                        :base base})))]
            (assoc % (get new-person "@id") (payload/expand new-person)))))

(defn get-person [args body request]
  (let [person (get @people-db
                    (payload/link-for {:model sorg-Person
                                       :args args
                                       :base base}))]
    (or person {:status 404 :body "Cannot find resource"})))

(defn delete-person [args body request]
  (swap! people-db #(dissoc % (payload/link-for {:model sorg-Person
                                                 :args args
                                                 :base base}))))

(defn get-address [args body request]
  (let [address (get @addresses-db
                     (payload/link-for {:model person-address-link
                                        :args args
                                        :base base}))]
    (or address {:status 404 :body "Cannot find resource"})))

(defn post-address [args body request]
  (swap! addresses-db
         #(let [new-address-id (payload/link-for {:model person-address-link
                                                  :args args
                                                  :base base})
                new-address (assoc body "@id" new-address-id)]
            (assoc % new-address-id (payload/expand new-address)))))

(defn delete-address [args body request]
  (swap! addresses-db #(dissoc % (payload/link-for {:model person-address-link
                                                    :args args
                                                    :base base}))))

(require '[levanzo.routing :as routing])

(def api-routes (routing/api-routes {:path ["people"]
                                     :model vocab-PeopleCollection
                                     :handlers {:get get-people
                                                :post post-person}
                                     :nested [{:path ["/" :person-id]
                                               :model sorg-Person
                                               :handlers {:get get-person
                                                          :delete delete-person}
                                               :nested [{:path ["/address"]
                                                         :model person-address-link
                                                         :handlers {:get get-address
                                                                    :post post-address
                                                                    :delete delete-address}}]}]}))

(def api-handler (http/middleware {:api API
                                   :mount-path "/"
                                   :routes api-routes
                                   :documentation-path "/vocab"}))

(taoensso.timbre/set-level! :debug)
(http/set-debug-errors! true)

(require '[org.httpkit.server :as http-kit])

;;;;;;;;;;;;;;;;;;;;;
;;(def stop-api (http-kit/run-server api-handler {:port 8080}))
;; to stop the server
;; (stop-api)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 4. Indexing and graph queries    ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def christian (payload/instance sorg-Person
                                 (payload/id {:model sorg-Person
                                              :args {:person-id 1}
                                              :base base})
                                 (payload/supported-property {:property sorg-name
                                                              :value "Christian"})))
(clojure.pprint/pprint (payload/->triples christian))
;;({:s {"@id" "http://localhost:8080/people/1"},
;;  :p {"@id" "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"},
;;  :o {"@id" "http://schema.org/Person"}}
;; {:s {"@id" "http://localhost:8080/people/1"},
;;  :p {"@id" "http://schema.org/name"},
;;  :o {"@value" "Christian"}})

;; API Indexing

(defn paginate-values [values {:keys [page per-page]}]
  (->> values
       (drop (* (dec page) per-page))
       (take (* page per-page))))

(defn index-property [collection]
  (fn [{:keys [predicate object pagination]}]
    (let [{:keys [page per-page]} pagination
          values (if (some? object)
                   (->> (deref collection)
                        vals
                        (filter #(= object (-> % (get predicate) first (get "@value")))))
                   (->> (deref collection)
                        vals
                        (filter #(-> % (get predicate) first some?))))]
      {:results (paginate-values values pagination)
       :count (count values)})))

(defn person-address-link-join [{:keys [subject object pagination]}]
  (let [subject (->> @people-db
                     vals
                     (filter #(= (get % "@id") subject))
                     first)]
    {:results (if (and (some? subject)
                       (= (:page pagination) 1))
                (let [joined-address (-> subject (get (hydra/id sorg-address)) first (get "@id"))
                      address (->> @addresses-db
                                   vals
                                   (filter #(= (get % "@id") joined-address))
                                   first)]
                  (if (some? object)
                    (if (= (get object "@id") (get address "@id"))
                      [{:subject subject
                        :object address}]
                      [])
                    [{:subject subject
                      :address address}]))
                (->> @addresses-db
                     (map (fn [address]
                            {:subject {"@id" (string/replace (get address "@id") "/address" "")}
                             :object address}))))
     :count 1}))

(defn class-lookup [collection]
  (fn [{:keys [subject]}] (if-let [result (get (deref collection) subject)]
                           {:results [result] :count 1}
                           {:results [] :count 1})))

(require '[levanzo.indexing :as indexing])

(def indices (indexing/api-index
              {sorg-Person
               {:resource (class-lookup people-db)
                :properties {sorg-name  {:index (index-property people-db)}
                             sorg-email {:index (index-property people-db)}}
                :links {person-address-link person-address-link-join}}

               sorg-PostalAddress
               {:properties {sorg-address {:index (index-property addresses-db)}
                             sorg-postal-code {:index (index-property addresses-db)}}}

               person-address-link
               {:resource (class-lookup addresses-db)}}))

(def api-handler (http/middleware {:api API
                                   :index indices
                                   :routes api-routes
                                   :mount-path "/"
                                   :documentation-path "/vocab"
                                   :fragments-path "/index"}))

(taoensso.timbre/set-level! :debug)
(http/set-debug-errors! true)

(require '[org.httpkit.server :as http-kit])

(defn cors-enabled [middleware]
  (fn [request]
    (let [response (middleware request)
          headers (:headers response)
          headers (assoc headers "Access-Control-Allow-Origin" "*")
          response (assoc response :headers headers)]
      response)))

(def stop-api (http-kit/run-server (cors-enabled api-handler) {:port 8080}))

;;  to stop the server
;; (stop-api)

(comment
  ((index-property people-db) {:predicate (hydra/id sorg-name) :pagination {:page 1 :per-page 5}} )
  ((index-property addresses-db) {:predicate (hydra/id sorg-name) :pagination {:page 1 :per-page 5}})

  ((class-lookup addresses-db) {:subject (payload/link-for {:model person-address-link :args {:person-id 1} :base base})})

  (person-address-link-join  {:subject (payload/link-for {:model sorg-Person
                                                          :args {:person-id 1}
                                                          :base base})
                              :object nil})

  (person-address-link-join  {:subject (payload/link-for {:model sorg-Person
                                                          :args {:person-id 1}
                                                          :base base})
                              :object [(payload/link-for {:model person-address-link
                                                          :args {:person-id 1}
                                                          :base base})]
                              :pagination {:page 1 :per-page 5}})
  )
