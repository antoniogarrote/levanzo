(ns levanzo.hydra
  (:require [clojure.spec :as s]
            [clojure.string :as string]))

(defprotocol JSONLDSerialisable
  "Protocol that must be implemented by implemented by elements of the model that can
   be serialised as JSON-LD documents"
  (->jsonld [this]))


(s/def ::args (s/map-of string? string?))
(s/def ::body (s/nilable any?))
(s/def ::request any?)
(s/def ::response any?)
(s/def ::handler (s/fspec :args (s/cat :args ::args
                                       :body ::body
                                       :request ::request)
                          :ret ::response))

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
(s/def ::term (s/or :uri ::uri
                    :curie ::curie))
;; method "HTTP method for this operations"
(s/def ::method (s/with-gen
                  string?
                  #(s/gen #{"GET" "POST" "PUT" "PATCH" "DELETE" "OPTIONS" "HEAD"})))
;; hydra:expects URI of the data expected by a hydra:operation
(s/def ::expects (s/nilable ::term))
;; hydra:returns URI for the data returned by a hydra:operation
(s/def ::returns (s/nilable ::term))



;; An Hydra operation that can be associated to any hypermedia link
(s/def ::Operation
  (s/keys :req-un [::term
                   ::method
                   ::handler]
          :opt-un [::expects
                   ::returns]))


(defrecord Operation [term
                      method
                      expects
                      returns
                      handler])


;; Operations can be serialised as JSON-LD objects
(extend-protocol JSONLDSerialisable levanzo.hydra.Operation
                 (->jsonld [this]
                   (let [jsonld {"@type" "hydra:Operation"
                                 "hydra:method" (:method this)}
                         jsonld (if (some? (:expects this))
                                  (assoc jsonld "hydra:expects" (:expects this))
                                  jsonld)
                         jsonld (if (some? (:returns this))
                                  (assoc jsonld "hydra:returns" (:returns this))
                                  jsonld)]
                     jsonld)))


(s/def ::operation-args (s/cat :options (s/keys :opt [::expects ::returns])
                               :handler ::handler))
(s/fdef get-operation
        :args ::operation-args
        :ret (s/and ::Operation
                    #(= (-> % :term) [:curie "hydra:Operation"])
                    #(= (-> % :method) "GET")))
(defn get-operation
  "Defines a new Hydra GET operation"
  ([{:keys [:levanzo.hydra/expects :levanzo.hydra/returns] :as opts} handler]
   (->Operation "hydra:Operation" "GET" expects returns handler))
  ([handler]
   (get-operation {} handler)))


(s/fdef post-operation
        :args ::operation-args
        :ret (s/and ::Operation
                    ;;#(= (-> % :term last) "hydra:Operation")
                    #(= (-> % :method) "POST")))
(defn post-operation
  "Defines a new Hydra POST operation"
  ([{:keys [:levanzo.hydra/expects :levanzo.hydra/returns]} handler]
   (->Operation "hydra:Operation" "POST" expects returns handler))
  ([handler]
   (post-operation {} handler)))


(s/fdef put-operation
        :args ::operation-args
        :ret (s/and ::Operation
                    #(= (-> % :term) [:curie "hydra:Operation"])
                    #(= (-> % :method) "PUT")))
(defn put-operation
  "Defines a new Hydra PUT operation"
  ([{:keys [:levanzo.hydra/expects :levanzo.hydra/returns]} handler]
   (->Operation "hydra:Operation" "PUT" expects returns handler))
  ([handler]
   (put-operation {} handler)))


(s/fdef patch-operation
        :args ::operation-args
        :ret (s/and ::Operation
                    #(= (-> % :term) [:curie "hydra:Operation"])
                    #(= (-> % :method) "PATCH")))
(defn patch-operation
  "Defines a new Hydra PATCH operation"
  ([{:keys [:levanzo.hydra/expects :levanzo.hydra/returns]} handler]
   (->Operation "hydra:Operation" "PATCH" expects returns handler))
  ([handler]
   (patch-operation {} handler)))


(s/fdef delete-operation
        :args ::operation-args
        :ret (s/and ::Operation
                    #(= (-> % :term) [:curie "hydra:Operation"])
                    #(= (-> % :method) "DELETE")))
(defn delete-operation
  "Defines a new Hydra DELETE operation"
  ([{:keys [:levanzo.hydra/expects :levanzo.hydra/returns]} handler]
   (->Operation "hydra:Operation" "DELETE" expects returns handler))
  ([handler]
   (delete-operation {} handler)))


(s/def ::required boolean?)
(s/def ::readonly boolean?)
(s/def ::writeonly boolean?)
(s/def ::domain ::term)
(s/def ::range ::term)
;; Hydra/RDF properties options, hydra:required, hydra:writeonly, hydra:readonly
(s/def ::property-options (s/keys :opt [::required ::writeonly ::readonly ::domain ::range]))
;; RDF property
(s/def ::property ::term)
;; Is this supported property a link?
(s/def ::is-link boolean?)
;; Is this supported property a template?
(s/def ::is-template boolean?)
;; An Hydra Link property
(s/def ::SupportedProperty
  (s/keys :req-un [::term
                   ::property
                   ::is-link
                   ::is-template
                   ::property-options
                   ::operations]))


;; A Hydra supported property
(defrecord SupportedProperty [term
                              is-link
                              is-template
                              property
                              property-options
                              operations])

;; Operations can be serialised as JSON-LD objects
(extend-protocol JSONLDSerialisable levanzo.hydra.SupportedProperty
                 (->jsonld [this]
                   (let [rdf-type (cond (:is-link this) "hydra:Link"
                                        (:is-template this) "hydra:TemplatedLink"
                                        :else "rdf:Property")
                         rdf-property {"@id" (:property this)
                                       "@type" rdf-type}
                         rdf-property (if (some? (-> this :property-options ::domain))
                                        (let [domain (-> this :property-options ::domain)]
                                          (assoc rdf-property "rdfs:domain" domain))
                                        rdf-property)
                         rdf-property (if (some? (-> this :property-options ::range))
                                        (let [range (-> this :property-options ::range)]
                                          (assoc rdf-property "rdfs:range" range))
                                        rdf-property)
                         rdf-property (if-let [operations (:operations this)]
                                        (assoc rdf-property "hydra:supportedOperation" (mapv ->jsonld operations))
                                        rdf-property)
                         jsonld {"@type" "hydra:SupportedProperty"
                                 "hydra:property" rdf-property}
                         jsonld (if (some? (-> this :property-options ::required))
                                  (let [required (-> this :property-options ::required)]
                                    (assoc jsonld "hydra:required" required))
                                  jsonld)
                         jsonld (if (some? (-> this :property-options ::readonly))
                                  (let [readonly (-> this :property-options ::readonly)]
                                    (assoc jsonld "hydra:readonly" readonly))
                                  jsonld)
                         jsonld (if (some? (-> this :property-options ::writeonly))
                                  (let [writeonly (-> this :property-options ::writeonly)]
                                    (assoc jsonld "hydra:writeonly" writeonly))
                                  jsonld)]
                     jsonld)))

(s/fdef link
        :args (s/cat :property ::property
                     :property-options ::property-options
                     :operations (s/coll-of ::Operation))
        :ret (s/and
              ::SupportedProperty
              #(= (:is-link %) true)
              #(= (:is-template %) false)
              #(= (:term %) [:curie "hydra:SupportedProperty"])))
(defn link
  "Builds a Hydra link from a certain RDF property"
  [property property-options operations]
  (->SupportedProperty "hydra:SupportedProperty" true false property property-options operations))


(s/fdef template-link
        :args (s/cat :property ::property
                     :property-options ::property-options
                     :operations (s/coll-of ::Operation))
        :ret (s/and
              ::SupportedProperty
              #(= (:is-link %) false)
              #(= (:is-template %) true)
              #(= (:term %) [:curie "hydra:SupportedProperty"])))
(defn template-link
  "Builds a Hydra templated link from a certain RDF property"
  [property property-options operations]
  (->SupportedProperty "hydra:SupportedProperty" false true property property-options operations))


(s/fdef property
        :args (s/cat :property ::property
                     :property-options ::property-options)
        :ret (s/and
              ::SupportedProperty
              #(= (:is-link %) false)
              #(= (:is-template %) false)
              #(= (:term %) [:curie "hydra:SupportedProperty"])
              #(empty? (:operations %))))
(defn property
  "Builds a Hydra property from a certain RDF property"
  [property property-options]
  (->SupportedProperty "hydra:SupportedProperty" false false property property-options []))
