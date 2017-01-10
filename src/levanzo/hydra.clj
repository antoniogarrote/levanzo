(ns levanzo.hydra
  (:require [clojure.spec :as s]
            [clojure.spec.gen :as gen]
            [clojure.test.check.generators :as tg]
            [clojure.string :as string]
            [levanzo.namespaces :refer [resolve]]
            [levanzo.spec.jsonld :as jsonld-spec]
            [levanzo.utils :refer [clean-nils]]
            [levanzo.jsonld :refer [add-not-dup assoc-if-some set-if-some]]))

(defprotocol JSONLDSerialisable
  "Protocol that must be implemented by implemented by elements of the model that can
   be serialised as JSON-LD documents"
  (->jsonld [this]))

;; Common JSON-LD options

;; A JSON-LD @id for a model element
(s/def ::id ::jsonld-spec/uri)
;; A JSON-LD @type for a model element
(s/def ::type ::jsonld-spec/uri)
;; Hydra title for a model element
(s/def ::title string?)
;; Hydra description for a model element
(s/def ::description string?)
;; Hydra common props for all Hydra model elements
(s/def ::common-props (s/keys :opt [::id ::type ::title ::description]))

(defn generic->jsonld
  "Sets common RDF properties for all Hydra elements"
  [element jsonld]
  (->> jsonld
       (set-if-some (::id element) "@id")
       (assoc-if-some ::type "@type" element)
       (set-if-some (::title element) (resolve "hydra:title"))
       (set-if-some (::description element) (resolve "hydra:description"))))

;; hydra routing options
(s/def ::route (s/coll-of (s/or :path ::jsonld-spec/path
                                :path-variable ::jsonld-spec/path-variable)
                          :gen-max 3))

;; hydra:Operation properties

;; Handler function for a hydra:Operation
(s/def ::handler (s/nilable (s/fspec :args (s/cat :args (s/map-of string? string?)
                                                  :body (s/nilable any?)
                                                  :request any?)
                                     :ret any?)))
;; method "HTTP method for this operations"
(s/def ::method (s/with-gen
                  (s/and string?
                         #(re-matches #"[A-Z]+" %))
                  #(s/gen #{"GET" "POST" "PUT" "PATCH" "DELETE" "OPTIONS" "HEAD"})))
;; hydra:expects URI of the data expected by a hydra:operation
(s/def ::expects (s/nilable ::jsonld-spec/uri))
;; hydra:returns URI for the data returned by a hydra:operation
(s/def ::returns (s/nilable ::jsonld-spec/uri))
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
  (s/keys :req-un [::jsonld-spec/uri
                   ::common-props
                   ::operation-props]
          :opt-un [::handler]))
(defrecord Operation [uri
                      common-props
                      operation-props
                      handler])

;; Operations can be serialised as JSON-LD objects
(extend-protocol JSONLDSerialisable levanzo.hydra.Operation
                 (->jsonld [this]
                   (let [jsonld {"@type" (resolve "hydra:Operation")
                                 (resolve "hydra:method") (-> this :operation-props ::method)}]
                     (->> jsonld
                          (set-if-some (-> this :operation-props ::expects) (resolve "hydra:expects"))
                          (set-if-some (-> this :operation-props ::returns) (resolve "hydra:returns"))
                          (generic->jsonld (:common-props this))))))

(s/fdef operation
        :args (s/cat :operations-args ::operation-args)
        :ret (s/and ::Operation
                    #(= (-> % :uri) (resolve "hydra:Operation"))))
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
   (map->Operation (clean-nils {:uri (resolve "hydra:Operation")
                                :common-props (clean-nils {::id id
                                                           ::title title
                                                           ::description description
                                                           ::type type})
                                :operation-props (clean-nils {::method (or method "GET")
                                                              ::expects expects
                                                              ::returns returns})
                                :handler handler}))))


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
                    #(= (-> % :uri) (resolve "hydra:Operation"))
                    #(= (-> % :operation-props ::method) "GET")))
(defn get-operation
  "Defines a new Hydra GET operation"
  ([opts] (operation (assoc opts ::method "GET"))))


(s/fdef post-operation
        :args (s/cat :method-operation-args ::operation-args)
        :ret (s/and ::Operation
                    #(= (-> % :uri) (resolve "hydra:Operation"))
                    #(= (-> % :operation-props ::method) "POST")))
(defn post-operation
  "Defines a new Hydra POST operation"
  ([opts] (operation (assoc opts ::method "POST"))))


(s/fdef put-operation
        :args (s/cat :method-operation-args ::operation-args)
        :ret (s/and ::Operation
                    #(= (-> % :uri) (resolve "hydra:Operation"))
                    #(= (-> % :operation-props ::method) "PUT")))
(defn put-operation
  "Defines a new Hydra PUT operation"
  ([opts]
   (operation (assoc opts ::method "PUT"))))


(s/fdef patch-operation
        :args (s/cat :method-operation-args ::operation-args)
        :ret (s/and ::Operation
                    #(= (-> % :uri) (resolve "hydra:Operation"))
                    #(= (-> % :operation-props ::method) "PATCH")))
(defn patch-operation
  "Defines a new Hydra PATCH operation"
  ([opts] (operation (assoc opts ::method "PATCH"))))


(s/fdef delete-operation
        :args (s/cat :method-operation-args ::operation-args)
        :ret (s/and ::Operation
                    #(= (-> % :uri) (resolve "hydra:Operation"))
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
(s/def ::domain ::jsonld-spec/uri)
;; Hydra range property
(s/def ::range ::jsonld-spec/uri)
;; Is this supported property a link?
(s/def ::is-link boolean?)
;; Is this supported property a template?
(s/def ::is-template boolean?)


(s/def ::rdf-props (s/keys ::domain ::range))

(defrecord Property [uri
                     is-link
                     is-template
                     common-props
                     rdf-props])

;; Properties can be serialised as JSON-LD objects
(extend-protocol JSONLDSerialisable levanzo.hydra.Property
                 (->jsonld [this]
                   (let [rdf-type (cond (:is-link this) (resolve "hydra:Link")
                                        (:is-template this) (resolve "hydra:TemplatedLink")
                                        :else (resolve "rdf:Property"))]
                     (->> {"@id" (-> this :common-props ::id)
                           "@type" rdf-type}
                          (set-if-some (-> this :rdf-props ::domain) (resolve "rdfs:domain"))
                          (set-if-some (-> this :rdf-props ::range) (resolve "rdfs:range"))))))

(s/def ::Property (s/with-gen
                    (s/and (s/keys :req-un [::jsonld-spec/uri
                                            ::is-link
                                            ::is-template
                                            ::common-props
                                            ::rdf-props])
                           ;; ID is mandatory
                           #(some? (-> % :common-props ::id))
                           ;; The URI is set correctly according to the kind of property
                           #(cond
                              (:is-link %)     (= (:uri %) (resolve "hydra:Link"))
                              (:is-template %) (= (:uri %) (resolve "hydra:TemplatedLink"))
                              :else            (= (:uri %) (resolve "rdf:Property"))))
                    #(tg/fmap (fn [[id is-link is-template common-props rdf-props]]
                                (let [common-props (assoc common-props ::id id)
                                      uri (cond
                                            is-link     (resolve "hydra:Link")
                                            is-template (resolve "hydra:TemplatedLink")
                                            :else       (resolve "rdf:Property"))]
                                  (->Property uri is-link is-template common-props rdf-props)))
                              (tg/tuple (s/gen ::jsonld-spec/uri)
                                        tg/boolean
                                        tg/boolean
                                        (s/gen ::common-props)
                                        (s/gen ::rdf-props)))))

;; Map of options used to create a rdf property / hydra link / hydra template link
(s/def ::property-args (s/keys :req [::id]
                               :opt [::type
                                     ::title
                                     ::description
                                     ::domain
                                     ::range]))

(s/fdef link
        :args (s/cat :property-args ::property-args)
        :ret (s/and
              ::Property
              #(some? (-> % :common-props ::id))
              #(= (:is-link %) true)
              #(= (:is-template %) false)
              #(= (:uri %) (resolve "hydra:Link"))))
(defn link
  "Builds a Hydra link from a certain RDF property"
  [{:keys [:levanzo.hydra/id
           :levanzo.hydra/type
           :levanzo.hydra/title
           :levanzo.hydra/description
           :levanzo.hydra/domain
           :levanzo.hydra/range]}]
  (->Property (resolve "hydra:Link")
              true
              false
              (clean-nils {::id id
                           ::title title
                           ::description description
                           ::type type})
              (clean-nils {::domain domain
                           ::range range})))


(s/fdef templated-link
        :args (s/cat :property-args ::property-args)
        :ret (s/and
              ::Property
              #(some? (-> % :common-props ::id))
              #(= (:is-link %) false)
              #(= (:is-template %) true)
              #(= (:uri %) (resolve "hydra:TemplatedLink"))))
(defn templated-link
  "Builds a Hydra link from a certain RDF property"
  [{:keys [:levanzo.hydra/id
           :levanzo.hydra/type
           :levanzo.hydra/title
           :levanzo.hydra/description
           :levanzo.hydra/domain
           :levanzo.hydra/range]}]
  (->Property (resolve "hydra:TemplatedLink")
              false
              true
              (clean-nils {::id id
                           ::title title
                           ::description description
                           ::type type})
              (clean-nils {::domain domain
                           ::range range})))

(s/fdef property
        :args (s/cat :property-args ::property-args)
        :ret (s/and
              ::Property
              #(some? (-> % :common-props ::id))
              #(= (:is-link %) false)
              #(= (:is-template %) false)
              #(= (:uri %) (resolve "rdf:Property"))))
(defn property
  "Builds a Hydra link from a certain RDF property"
  [{:keys [:levanzo.hydra/id
           :levanzo.hydra/type
           :levanzo.hydra/title
           :levanzo.hydra/description
           :levanzo.hydra/domain
           :levanzo.hydra/range]}]
  (->Property (resolve "rdf:Property")
              false
              false
              (clean-nils {::id id
                           ::title title
                           ::description description
                           ::type type})
              (clean-nils {::domain domain
                           ::range range})))


;; Hydra/RDF properties options, hydra:required, hydra:writeonly, hydra:readonly
;; id type title and description
(s/def ::property-props (s/keys :opt [::required ::writeonly ::readonly  ::route]))
;; RDF property
(s/def ::property ::Property)
;; List of operations associated to a link/template
(s/def ::operations (s/coll-of ::Operation :gen-max 2))

;; A Hydra Link property
;; A Hydra supported property
(defrecord SupportedProperty [uri
                              property
                              common-props
                              property-props
                              operations])

(s/def ::SupportedProperty
  (s/with-gen (s/and (s/keys :req-un [::property
                                      ::common-props
                                      ::property-props
                                      ::operations])
                     #(= (resolve "hydra:SupportedProperty") (:uri %))
                     ;; no link/templates cannot have operations
                     #(if (not (or (-> % :property :is-link) (-> % :property :is-template)))
                        (empty? (:operations %))
                        true))
    #(tg/fmap (fn [[property common-props property-props operations]]
                (if (or (:is-link property)
                        (:is-template property))
                  (let [route (or (-> property-props ::route) ["/prop"])
                        property-props (assoc property-props ::route route)]
                    (->SupportedProperty (resolve "hydra:SupportedProperty") property common-props property-props operations))
                  (->SupportedProperty (resolve "hydra:SupportedProperty") property common-props property-props [])))
              (tg/tuple
               (s/gen ::Property)
               (s/gen ::common-props)
               (s/gen ::property-props)
               (s/gen (s/coll-of ::Operation :max-count 1 :min-count 1))))))

;; Operations can be serialised as JSON-LD objects
(extend-protocol JSONLDSerialisable levanzo.hydra.SupportedProperty
                 (->jsonld [this]
                   (let [rdf-property (->jsonld (:property this))
                         supported-property (->> {"@type" (resolve "hydra:SupportedProperty")
                                                  (resolve "hydra:property") rdf-property}
                                                 (set-if-some (-> this :property-props ::required) (resolve "hydra:required"))
                                                 (set-if-some (-> this :property-props ::readonly) (resolve "hydra:readonly"))
                                                 (set-if-some (-> this :property-props ::writeonly) (resolve "hydra:writeonly"))
                                                 (generic->jsonld (:common-props this)))]
                     (if-let [operations (:operations this)]
                       (assoc supported-property (resolve "hydra:supportedOperation") (mapv ->jsonld operations))
                       supported-property))))

;; Map of options used to create a supported property
(s/def ::supported-property-args (s/with-gen (s/keys :req [::property]
                                                     :opt [::id
                                                           ::type
                                                           ::title
                                                           ::description
                                                           ::required
                                                           ::readonly
                                                           ::writeonly
                                                           ::route
                                                           ::operations])
                                   #(tg/fmap (fn [[property id type title description required readonly writeonly route operations]]
                                               (if (or (:is-link property)
                                                       (:is-template property))
                                                 {::property property ::id id ::type type ::title title ::description description
                                                  ::required required ::readonly readonly ::writeonly writeonly ::route route ::operations operations}
                                                 {::property property ::id id ::type type ::title title ::description description
                                                  ::required required ::readonly readonly ::writeonly writeonly ::route route :operations []}))
                                             (tg/tuple (s/gen ::property)
                                                       (s/gen ::id)
                                                       (s/gen ::type)
                                                       (s/gen ::title)
                                                       (s/gen ::description)
                                                       (s/gen ::required)
                                                       (s/gen ::readonly)
                                                       (s/gen ::writeonly)
                                                       (s/gen ::route)
                                                       (s/gen ::operations)))))

(s/fdef supported-property
        :args (s/and (s/cat :supported-property-args ::supported-property-args
                            )
                     ;; no link/templates cannot have operations
                     (fn [{:keys [property operations]}]
                       (if (not (or (:is-link property) (:is-template property)))
                         (empty? operations)
                         true)))
        :ret (s/and
              ::SupportedProperty
              #(= (:uri %) (resolve "hydra:SupportedProperty"))))
(defn supported-property
  "Builds a Hydra SupportedProperty from a certain RDF property"
  [{:keys [:levanzo.hydra/property
           :levanzo.hydra/operations
           :levanzo.hydra/id
           :levanzo.hydra/type
           :levanzo.hydra/title
           :levanzo.hydra/description
           :levanzo.hydra/required
           :levanzo.hydra/readonly
           :levanzo.hydra/writeonly
           :levanzo.hydra/route]}]
  (->SupportedProperty (resolve "hydra:SupportedProperty")
                       property
                       (clean-nils {::id id
                                    ::type type
                                    ::title title
                                    ::description description})
                       (clean-nils {::required required
                                    ::readonly readonly
                                    ::writeonly writeonly
                                    ::route route})
                       (or operations [])))


;; Hydra Class

;; Supported properties by a Hydra Class
(s/def ::supported-properties (s/coll-of ::SupportedProperty :gen-max 2))

;; A Hydra supported class
(s/def ::SupportedClass
  (s/keys :req-un [::jsonld-spec/uri
                   ::common-props
                   ::supported-properties
                   ::operations]))

(s/def ::class-args (s/keys :req [::id
                                  ::operations
                                  ::supported-properties]
                            :opt [::type
                                  ::title
                                  ::description]))

(defrecord SupportedClass [uri
                           common-props
                           supported-properties
                           operations])

;; Classes can be serialised as JSON-LD objects
(extend-protocol JSONLDSerialisable levanzo.hydra.SupportedClass
                 (->jsonld [this]
                   (let [jsonld {"@type" (resolve "hydra:Class")
                                 (resolve "hydra:supportedProperty") (mapv ->jsonld (-> this :supported-properties))
                                 (resolve "hydra:supportedOperation") (mapv ->jsonld (-> this :operations))}]
                     (->> jsonld
                          clean-nils
                          (generic->jsonld (:common-props this))))))

(s/fdef class
        :args (s/cat :class-args ::class-args)
        :ret (s/and
              ::SupportedClass
              #(= (:uri %) (resolve "hydra:Class"))
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
  (->SupportedClass (resolve "hydra:Class")
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
(s/def ::member-class ::jsonld-spec/uri)
;; Route for the collection resources
(s/def ::member-route ::route)

(s/def ::Collection
  (s/keys :req-un [::jsonld-spec/uri
                   ::common-props
                   ::is-paginated
                   ::member-class
                   ::member-route
                   ::operations]))

(s/def ::collection-args (s/keys :req [::id
                                       ::operations
                                       ::is-paginated
                                       ::member-class
                                       ::member-route]
                                 :un [::type
                                      ::title
                                      ::description]))

(defrecord Collection [uri
                       is-paginated
                       member-class
                       member-route
                       common-props
                       operations])

;; Collections can be serialised as JSON-LD objects
(extend-protocol JSONLDSerialisable levanzo.hydra.Collection
                 (->jsonld [this]
                   (let [type (if (:is-paginated this)
                                [(resolve "hydra:Class") (resolve "hydra:PagedCollection") (resolve "hydra:Collection")]
                                [(resolve "hydra:Class") (resolve "hydra:Collection")])
                         jsonld {(resolve "hydra:supportedOperation") (mapv ->jsonld (-> this :operations))
                                 "lvz:memberClass" (:member-class this)
                                 "@type" type}]
                     (->> jsonld
                          clean-nils
                          (generic->jsonld (:common-props this))))))

(s/fdef collection
        :args (s/cat :collection-args ::collection-args)
        :ret (s/and
              ::Collection
              #(= (:uri %) (resolve "hydra:Collection"))
              #(not (nil? (-> % :member-class)))
              #(not (nil? (-> % :member-route)))
              #(not (nil? (-> % :is-paginated))))
        :fn (s/and
             #(= (-> % :ret :operations count) (-> % :args :collection-args ::operations count))))
(defn collection [{:keys [:levanzo.hydra/is-paginated
                          :levanzo.hydra/member-class
                          :levanzo.hydra/member-route
                          :levanzo.hydra/operations
                          :levanzo.hydra/id
                          :levanzo.hydra/type
                          :levanzo.hydra/title
                          :levanzo.hydra/description]}]
  (->Collection (resolve "hydra:Collection")
                is-paginated
                member-class
                member-route
                (clean-nils {::id id
                             ::title title
                             ::description description
                             ::type type})
                (or operations [])))

(defn collection?
  "Is this model element a hydra:Collection?"
  [element]
  (and (map? element)
       (= (:uri element)
          (resolve "hydra:Collection"))))


;; Hydra ApiDocumentation

;; Supported classes by a Hydra ApiDocumentation
(s/def ::supported-classes (s/coll-of (s/or
                                       :hydra-class ::SupportedClass
                                       :hydra-collection ::Collection)
                                      :min-count 1
                                      :gen-max 2))

;; entrypoint path for this API
(s/def ::entrypoint ::jsonld-spec/absolute-path)
;; URI of the class for the entrypoint resource
(s/def ::entrypoint-class ::jsonld-spec/uri)
;; api documentation specific props
(s/def ::api-props (s/keys :req [::entrypoint ::entrypoint-class]))

(s/def ::ApiDocumentation
  (s/keys :req-un [::jsonld-spec/uri
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
                    #(tg/fmap (fn [[args-coll entrypoint type title description id]]
                                (let [classes (map (fn [args]
                                                     (if (some? (::member-class args))
                                                       (collection args)
                                                       (class args)))
                                                   args-coll)]
                                  {::supported-classes classes
                                   ::entrypoint entrypoint
                                   ::entrypoint-class (-> classes first :common-props ::id)
                                   ::type type
                                   ::title title
                                   ::description description
                                   ::id id}))
                              (tg/tuple
                               (s/gen (s/coll-of (s/or
                                                  :class-args ::class-args
                                                  :colleciton-args ::collection-args)
                                                 :min-count 1 :max-count 2))
                               (s/gen ::jsonld-spec/absolute-path)
                               (s/gen ::type)
                               (s/gen ::title)
                               (s/gen ::description)
                               (s/gen ::id)))))

(defrecord ApiDocumentation [uri
                             common-props
                             api-props
                             supported-classes])

;; ApiDocumentations can be serialised as JSON-LD objects
(extend-protocol JSONLDSerialisable levanzo.hydra.ApiDocumentation
                 (->jsonld [this]
                   (let [jsonld {"@type" (resolve "hydra:ApiDocumentation")
                                 (resolve "hydra:entrypoint") (-> this :api-props ::entrypoint)
                                 "lvz:entrypointClass" (-> this :api-props ::entrypoint-class)
                                 (resolve "hydra:supportedClass") (mapv ->jsonld (-> this :supported-classes))}]
                     (->> jsonld
                          (generic->jsonld (:common-props this))))))

(s/fdef api
        :args (s/cat :api-args ::api-args)
        :ret (s/and
              ::ApiDocumentation
              #(= (:uri %) (resolve "hydra:ApiDocumentation")))
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
  (->ApiDocumentation (resolve "hydra:ApiDocumentation")
                      (clean-nils {::id id
                                   ::title title
                                   ::description description
                                   ::type type})
                      {::entrypoint entrypoint
                       ::entrypoint-class entrypoint-class}
                      supported-classes))

(defn find-class
  "Finds a class in the API by ID"
  [api class-id]
  (->> api
       :supported-classes
       (filter (fn [supported-class]
                 (= (-> supported-class :common-props ::id)
                    class-id)))
       first))


(defn find-class-operations
  "Finds a class in the API by ID"
  [api class-id]
  (let [class (find-class api class-id)]
    (-> class :operations)))
