(ns levanzo.hydra
  (:require [clojure.spec :as s]
            [clojure.spec.gen :as gen]
            [clojure.test.check.generators :as tg]
            [clojure.string :as string]
            [levanzo.utils :refer [clean-nils]]
            [levanzo.jsonld :refer [add-not-dup assoc-if-some set-if-some]]))

(defprotocol JSONLDSerialisable
  "Protocol that must be implemented by implemented by elements of the model that can
   be serialised as JSON-LD documents"
  (->jsonld [this]))


;; URI string
(s/def ::uri (s/with-gen
               (s/and string? #(re-matches #"^([a-z0-9+.-]+):(?://(?:((?:[a-z0-9-._~!$&'()*+,;=:]|%[0-9A-F]{2})*)@)?((?:[a-z0-9-._~!$&'()*+,;=]|%[0-9A-F]{2})*)(?::(\d*))?(/(?:[a-z0-9-._~!$&'()*+,;=:@/]|%[0-9A-F]{2})*)?|(/?(?:[a-z0-9-._~!$&'()*+,;=:@]|%[0-9A-F]{2})+(?:[a-z0-9-._~!$&'()*+,;=:@/]|%[0-9A-F]{2})*)?)(?:\?((?:[a-z0-9-._~!$&'()*+,;=:/?@]|%[0-9A-F]{2})*))?(?:#((?:[a-z0-9-._~!$&'()*+,;=:/?@]|%[0-9A-F]{2})*))?$" %))
               #(s/gen #{"http://test.com/some/url"
                         "https://192.168.10.10/path#with-fragment"
                         "ftp://100.10.10.10/directory"
                         "http://test.com/path?a=123"})))
;; CURIE string
(s/def ::curie (s/with-gen
                 (s/and string? #(re-matches #".*\:.+" %))
                 #(s/gen #{"hydra:Class" "foaf:name" "xsd:string" "sorg:country" ":test"})))
;; Hydra vocabulary term for this element in the model
(s/def ::term (s/or
               :uri ::uri
               :curie ::curie))

;; URI path definitions
(s/def ::relative-path (s/with-gen
                         (s/and string?
                                #(re-matches #"^([a-zA-Z0-9-._~!$&'()*+,;=:]|%[0-9A-F]{2})+(\/?([a-zA-Z0-9-._~!$&'()*+,;=:]|%[0-9A-F]{2})+)*$" %))
                         #(tg/fmap (fn [xs] (string/join "/" xs))
                                   (tg/vector tg/string-alphanumeric 1 5))))

(s/def ::absolute-path (s/with-gen
                         (s/and string?
                                #(re-matches #"^\/([a-zA-Z0-9-._~!$&'()*+,;=:]|%[0-9A-F]{2})+(\/?([a-zA-Z0-9-._~!$&'()*+,;=:]|%[0-9A-F]{2})+)*$" %))
                         #(tg/fmap (fn [xs] (str "/" (string/join "/" xs)))
                                   (tg/vector tg/string-alphanumeric 1 5))))

(s/def ::path (s/or
               :relative-path ::relative-path
               :absolute-path ::absolute-path))

;; Common JSON-LD options

;; A JSON-LD @id for a model element
(s/def ::id ::term)
;; A JSON-LD @type for a model element
(s/def ::type ::term)
;; Hydra title for a model element
(s/def ::title string?)
;; Hydra description for a model element
(s/def ::description string?)
;; Hydra common props for all Hydra model elements
(s/def ::common-props (s/keys :opt [::id ::type ::title ::description]))

(defn generic->jsonld
  "Sets common RDF proeprties for all Hydra elements"
  [element jsonld]
  (->> jsonld
       (set-if-some (::id element) "@id")
       (assoc-if-some ::type "@type" element)
       (set-if-some (::title element) "hydra:title")
       (set-if-some (::description element) "hydra:description")))

;; hydra routing options
(s/def ::path-variable (s/with-gen keyword?
                         #(s/gen #{:user_id :ticket_id :order_id :event_id})))
(s/def ::route (s/coll-of (s/or :path ::path
                                :path-variable ::path-variable)))

;; hydra:Operation properties

;; Handler function for a hydra:Operation
(s/def ::handler (s/nilable (s/fspec :args (s/cat :args (s/map-of string? string?)
                                                  :body (s/nilable any?)
                                                  :request any?)
                                     :ret any?)))
;; method "HTTP method for this operations"
(s/def ::method (s/with-gen
                  string?
                  #(s/gen #{"GET" "POST" "PUT" "PATCH" "DELETE" "OPTIONS" "HEAD"})))
;; hydra:expects URI of the data expected by a hydra:operation
(s/def ::expects (s/nilable ::term))
;; hydra:returns URI for the data returned by a hydra:operation
(s/def ::returns (s/nilable ::term))
(s/def ::operation-props (s/keys :req [::method]
                                 :opt [::expects ::returns]))

;; Map of options used to create an operation
(s/def ::operation-args (s/keys :req [::method]
                                :opt [::handler
                                      ::id
                                      ::type
                                      ::title
                                      ::description
                                      ::expects
                                      ::returns]))


;; An Hydra operation that can be associated two any hypermedia link
(s/def ::Operation
  (s/keys :req-un [::term
                   ::common-props
                   ::operation-props
                   ::handler]))
(defrecord Operation [term
                      common-props
                      operation-props
                      handler])

;; Operations can be serialised as JSON-LD objects
(extend-protocol JSONLDSerialisable levanzo.hydra.Operation
                 (->jsonld [this]
                   (let [jsonld {"@type" "hydra:Operation"
                                 "hydra:method" (-> this :operation-props ::method)}]
                     (->> jsonld
                          (set-if-some (-> this :operation-props ::expects) "hydra:expects")
                          (set-if-some (-> this :operation-props ::returns) "hydra:returns")
                          (generic->jsonld (:common-props this))))))

(s/fdef operation
        :args (s/cat :operations-args ::operation-args)
        :ret (s/and ::Operation
                    #(= (-> % :term) [:curie "hydra:Operation"])))
(defn operation
  "Defines a new Hydra operation"
  ([{:keys [:levanzo.hydra/handler
            :levanzo.hydra/method
            :levanzo.hydra/id
            :levanzo.hydra/type
            :levanzo.hydra/title
            :levanzo.hydra/description
            :levanzo.hydra/expects
            :levanzo.hydra/returns] :as opts}]
   (->Operation "hydra:Operation"
                (clean-nils {::id id
                             ::title title
                             ::description description
                             ::type type})
                (clean-nils {::method (or method "GET")
                             ::expects expects
                             ::returns returns})
                handler)))

(s/def ::method-operation-args (s/keys :opt [::handler
                                             ::id
                                             ::type
                                             ::title
                                             ::description
                                             ::expects
                                             ::returns]))


(s/fdef get-operation
        :args (s/cat :method-operation-args ::operation-args)
        :ret (s/and ::Operation
                    #(= (-> % :term) [:curie "hydra:Operation"])
                    #(= (-> % :operation-props ::method) "GET")))
(defn get-operation
  "Defines a new Hydra GET operation"
  ([opts] (operation (assoc opts ::method "GET"))))


(s/fdef post-operation
        :args (s/cat :method-operation-args ::operation-args)
        :ret (s/and ::Operation
                    #(= (-> % :term last) "hydra:Operation")
                    #(= (-> % :operation-props ::method) "POST")))
(defn post-operation
  "Defines a new Hydra POST operation"
  ([opts] (operation (assoc opts ::method "POST"))))


(s/fdef put-operation
        :args (s/cat :method-operation-args ::operation-args)
        :ret (s/and ::Operation
                    #(= (-> % :term) [:curie "hydra:Operation"])
                    #(= (-> % :operation-props ::method) "PUT")))
(defn put-operation
  "Defines a new Hydra PUT operation"
  ([opts]
   (operation (assoc opts ::method "PUT"))))


(s/fdef patch-operation
        :args (s/cat :method-operation-args ::operation-args)
        :ret (s/and ::Operation
                    #(= (-> % :term) [:curie "hydra:Operation"])
                    #(= (-> % :operation-props ::method) "PATCH")))
(defn patch-operation
  "Defines a new Hydra PATCH operation"
  ([opts] (operation (assoc opts ::method "PATCH"))))


(s/fdef delete-operation
        :args (s/cat :method-operation-args ::operation-args)
        :ret (s/and ::Operation
                    #(= (-> % :term) [:curie "hydra:Operation"])
                    #(= (-> % :operation-props ::method) "DELETE")))
(defn delete-operation
  "Defines a new Hydra DELETE operation"
  ([opts] (operation (assoc opts ::method "DELETE"))))

;; hydra::SupportedProperty properties

;; Hydra required property
(s/def ::required boolean?)
;; Hydra readonly property
(s/def ::readonly boolean?)
;; Hydra writeonly property
(s/def ::writeonly boolean?)
;; Hydra domain property
(s/def ::domain ::term)
;; Hydra range property
(s/def ::range ::term)
;; Hydra/RDF properties options, hydra:required, hydra:writeonly, hydra:readonly
;; id type title and description
(s/def ::property-props (s/keys :opt [::required ::writeonly ::readonly ::domain ::range ::route]))
;; RDF property
(s/def ::property ::term)
;; Is this supported property a link?
(s/def ::is-link boolean?)
;; Is this supported property a template?
(s/def ::is-template boolean?)
;; List of operations associated to a link/template
(s/def ::operations (s/coll-of ::Operation))

;; A Hydra Link property
(s/def ::SupportedProperty
  (s/keys :req-un [::term
                   ::is-link
                   ::is-template
                   ::property
                   ::common-props
                   ::property-props
                   ::operations]))

;; Map of options used to create a supported property
(s/def ::property-args (s/keys :req [::id
                                     ::property]
                               :opt [::type
                                     ::title
                                     ::description
                                     ::required
                                     ::readonly
                                     ::writeonly
                                     ::domain
                                     ::range
                                     ::route
                                     ::operations]))


;; A Hydra supported property
(defrecord SupportedProperty [term
                              is-link
                              is-template
                              property
                              common-props
                              property-props
                              operations])

;; Operations can be serialised as JSON-LD objects
(extend-protocol JSONLDSerialisable levanzo.hydra.SupportedProperty
                 (->jsonld [this]
                   (let [rdf-type (cond (:is-link this) "hydra:Link"
                                        (:is-template this) "hydra:TemplatedLink"
                                        :else "rdf:Property")
                         rdf-property (->> {"@id" (:property this)
                                            "@type" rdf-type}
                                           (set-if-some (-> this :property-props ::domain) "rdfs:domain")
                                           (set-if-some (-> this :property-props ::range) "rdfs:range"))
                         rdf-property (if-let [operations (:operations this)]
                                        (assoc rdf-property "hydra:supportedOperation" (mapv ->jsonld operations))
                                        rdf-property)]
                     (->> {"@type" "hydra:SupportedProperty"
                           "hydra:property" rdf-property}
                          (set-if-some (-> this :property-props ::required) "hydra:required")
                          (set-if-some (-> this :property-props ::readonly) "hydra:readonly")
                          (set-if-some (-> this :property-props ::writeonly) "hydra:writeonly")
                          (generic->jsonld (:common-props this))))))

(s/fdef supported-property
        :args (s/cat :is-link ::is-link
                     :is-template ::is-template
                     :property-args ::property-args)
        :ret (s/and
              ::SupportedProperty
              #(= (:term %) [:curie "hydra:SupportedProperty"]))
        :fn (s/and
             #(= (-> % :ret :is-link) (-> % :args :is-link))
             #(= (-> % :ret :is-template) (-> % :args :is-template))
             #(= (-> % :ret :property) (-> % :args :property-args ::property))
             #(if (some? (-> % :args :property-args ::operations))
                (= (-> % :ret :operations count) (-> % :args :property-args ::operations count))
                (= (-> % :ret :operations count) 0))))
(defn supported-property
  "Builds a Hydra SupportedProperty from a certain RDF property"
  [is-link is-template
   {:keys [:levanzo.hydra/property
           :levanzo.hydra/operations
           :levanzo.hydra/id
           :levanzo.hydra/type
           :levanzo.hydra/title
           :levanzo.hydra/description
           :levanzo.hydra/required
           :levanzo.hydra/readonly
           :levanzo.hydra/writeonly
           :levanzo.hydra/domain
           :levanzo.hydra/range
           :levanzo.hydra/route]}]
  (->SupportedProperty "hydra:SupportedProperty"
                       is-link
                       is-template
                       property
                       (clean-nils {::id id
                                    ::type type
                                    ::title title
                                    ::description description})
                       (clean-nils {::required required
                                    ::readonly readonly
                                    ::writeonly writeonly
                                    ::domain domain
                                    ::range range
                                    ::route route})
                       (or operations [])))

;; Map of options used to create a supported property link
(s/def ::link-args (s/keys :req [::property
                                 ::operations
                                 ::route]
                           :opt [::id
                                 ::type
                                 ::title
                                 ::description
                                 ::required
                                 ::readonly
                                 ::writeonly
                                 ::domain
                                 ::range]))


(s/fdef link
        :args (s/cat :link-args ::link-args)
        :ret (s/and
              ::SupportedProperty
              #(= (:is-link %) true)
              #(= (:is-template %) false)
              #(= (:term %) [:curie "hydra:SupportedProperty"])))
(defn link
  "Builds a Hydra link from a certain RDF property"
  [args]
  (supported-property true false args))


(s/fdef template-link
        :args (s/cat :link-args ::link-args)
        :ret (s/and
              ::SupportedProperty
              #(= (:is-link %) false)
              #(= (:is-template %) true)
              #(= (:term %) [:curie "hydra:SupportedProperty"])))
(defn template-link
  "Builds a Hydra templated link from a certain RDF property"
  [args]
  (supported-property false true args))


(s/fdef property
        :args (s/cat :property-args ::property-args)
        :ret (s/and
              ::SupportedProperty
              #(= (:is-link %) false)
              #(= (:is-template %) false)
              #(= (:term %) [:curie "hydra:SupportedProperty"])
              #(empty? (:operations %))))
(defn property
  "Builds a Hydra property from a certain RDF property"
  [args]
  (supported-property false false (assoc args ::operations [])))


;; Hydra Class

;; Supported properties by a Hydra Class
(s/def ::supported-properties (s/coll-of ::SupportedProperty :gen-max 2))

;; A Hydra supported class
(s/def ::SupportedClass
  (s/keys :req-un [::term
                   ::common-props
                   ::supported-properties
                   ::operations]))

(s/def ::class-args (s/keys :req [::id
                                  ::operations
                                  ::supported-properties]
                            :un [::type
                                 ::title
                                 ::description]))

(defrecord SupportedClass [term
                           common-props
                           supported-properties
                           operations])

;; Classes can be serialised as JSON-LD objects
(extend-protocol JSONLDSerialisable levanzo.hydra.SupportedClass
                 (->jsonld [this]
                   (let [jsonld {"@type" "hydra:Class"
                                 "hydra:supportedProperty" (mapv ->jsonld (-> this :supported-properties))
                                 "hydra:supportedOperation" (mapv ->jsonld (-> this :operations))}]
                     (->> jsonld
                          clean-nils
                          (generic->jsonld (:common-props this))))))

(s/fdef class
        :args (s/cat :class-args ::class-args)
        :ret (s/and
              ::SupportedClass
              #(= (:term %) [:curie "hydra:Class"])
              #(not (nil? (-> % :common-props ::id))))
        :fn (s/and
             #(= (-> % :ret :operations count) (-> % :args :class-args ::operations count))
             #(= (-> % :ret :supported-properties count) (-> % :args :class-args ::supported-properties count))))

(defn class [{:keys [:levanzo.hydra/id
                     :levanzo.hydra/operations
                     :levanzo.hydra/supported-properties
                     :levanzo.hydra/type
                     :levanzo.hydra/title
                     :levanzo.hydra/description]}]
  (->SupportedClass "hydra:Class"
                    (clean-nils {::id id
                                 ::title title
                                 ::description description
                                 ::type type})
                    supported-properties
                    operations))

;; Hydra Collection

;; Is this collection paginated?
(s/def ::is-paginated boolean?)
;; Class of the collection members
(s/def ::member-class ::term)

(s/def ::Collection
  (s/keys :req-un [::term
                   ::common-props
                   ::is-paginated
                   ::member-class
                   ::operations]))

(s/def ::collection-args (s/keys :req [::id
                                       ::operations
                                       ::is-paginated
                                       ::member-class]
                                 :un [::type
                                      ::title
                                      ::description]))

(defrecord Collection [term
                       is-paginated
                       member-class
                       common-props
                       operations])

;; Collections can be serialised as JSON-LD objects
(extend-protocol JSONLDSerialisable levanzo.hydra.Collection
                 (->jsonld [this]
                   (let [type (if (:is-paginated this)
                                ["hydra:Class" "hydra:PagedCollection" "hydra:Collection"]
                                ["hydra:Class" "hydra:Collection"])
                         jsonld {"hydra:supportedOperation" (mapv ->jsonld (-> this :operations))
                                 "lvz:memberClass" (:member-class this)
                                 "@type" type}]
                     (->> jsonld
                          clean-nils
                          (generic->jsonld (:common-props this))))))

(s/fdef collection
        :args (s/cat :collection-args ::collection-args)
        :ret (s/and
              ::Collection
              #(= (:term %) [:curie "hydra:Collection"])
              #(not (nil? (-> % :member-class)))
              #(not (nil? (-> % :is-paginated))))
        :fn (s/and
             #(= (-> % :ret :operations count) (-> % :args :collection-args ::operations count))))

(defn collection [{:keys [:levanzo.hydra/is-paginated
                          :levanzo.hydra/member-class
                          :levanzo.hydra/operations
                          :levanzo.hydra/id
                          :levanzo.hydra/type
                          :levanzo.hydra/title
                          :levanzo.hydra/description]}]
  (->Collection "hydra:Collection"
                is-paginated
                member-class
                (clean-nils {::id id
                             ::title title
                             ::description description
                             ::type type})
                operations))




;; Hydra ApiDocumentation

;; Supported classes by a Hydra ApiDocumentation
(s/def ::supported-classes (s/coll-of (s/or
                                       :hydra-class ::SupportedClass
                                       :hydra-collection ::Collection)
                                      :min-count 1
                                      :gen-max 2))

;; entrypoint path for this API
(s/def ::entrypoint ::absolute-path)
;; URI of the class for the entrypoint resource
(s/def ::entrypoint-class ::term)
;; api documentation specific props
(s/def ::api-props (s/keys :req [::entrypoint ::entrypoint-class]))

(s/def ::ApiDocumentation
  (s/keys :req-un [::term
                   ::common-props
                   ::api-props
                   ::supported-classes]))

(s/def ::api-args (s/with-gen (s/keys :req [::supported-classes
                                            ::entrypoint
                                            ::entrypoint-class]
                                      :un [::type
                                           ::title
                                           ::description
                                           ::id])
                    #(tg/fmap (fn [classes]
                                {::supported-classes classes
                                 ::entrypoint (first (gen/sample (s/gen ::entrypoint)))
                                 ::entrypoint-class (-> classes first :common-props ::id)
                                 ::type (first (gen/sample (s/gen ::type)))
                                 ::title (first (gen/sample (s/gen ::title)))
                                 ::description (first (gen/sample (s/gen ::description)))
                                 ::id (first (gen/sample (s/gen ::id)))})
                              (s/gen ::supported-classes))))

(defrecord ApiDocumentation [term
                             common-props
                             api-props
                             supported-classes])

;; ApiDocumentations can be serialised as JSON-LD objects
(extend-protocol JSONLDSerialisable levanzo.hydra.ApiDocumentation
                 (->jsonld [this]
                   (let [jsonld {"@type" "hydra:ApiDocumentation"
                                 "hydra:entrypoint" (-> this :api-props ::entrypoint)
                                 "lvz:entrypointClass" (-> this :api-props ::entrypoint-class)
                                 "hydra:supportedClass" (mapv ->jsonld (-> this :supported-classes))}]
                     (->> jsonld
                          (generic->jsonld (:common-props this))))))

(s/fdef api
        :args (s/cat :api-args ::api-args)
        :ret (s/and
              ::ApiDocumentation
              #(= (:term %) [:curie "hydra:ApiDocumentation"]))
        :fn (s/and
             ;; number of supported classes in built ApiDocumentation matches the number of classes
             ;; passed in the arguments
             #(= (-> % :ret :supported-classes count) (-> % :args :api-args ::supported-classes count))
             ;; The URI for the entrypoint-class matches the URI of one of the supported-classes
             #(not (empty?
                    (filter (fn [[type supported-class-or-collection]]
                              (= (-> supported-class-or-collection :common-props ::id)
                                 (-> % :ret :api-props ::entrypoint-class)))
                            (-> % :args :api-args ::supported-classes))))))
(defn api
  "Defines a Hydra ApiDocumentation element"
  [{:keys [:levanzo.hydra/supported-classes
           :levanzo.hydra/entrypoint
           :levanzo.hydra/entrypoint-class
           :levanzo.hydra/type
           :levanzo.hydra/title
           :levanzo.hydra/description
           :levanzo.hydra/id]}]
  (->ApiDocumentation "hydra:ApiDocumentation"
                      (clean-nils {::id id
                                   ::title title
                                   ::description description
                                   ::type type})
                      {::entrypoint entrypoint
                       ::entrypoint-class entrypoint-class}
                      supported-classes))
