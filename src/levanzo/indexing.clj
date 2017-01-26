(ns levanzo.indexing
  (:require [clojure.spec :as s]
            [clojure.string :as string]
            [levanzo.hydra :as hydra]
            [levanzo.payload :refer [u d l] :as payload]
            [levanzo.routing :as routing]
            [levanzo.spec.jsonld :as jsonld-spec]
            [taoensso.timbre :as log]
            [cemerick.url :refer [url-encode] :as url]))

;; Structure required to index an API so we can satisfy BGP queries

;;; * SPO (?, ?, ?), (s, ?, ?)
;;; * P   (?, p, ?)
;;; * OS  (?, ?, o), (s, ?, o)
;;; * PO  (?, p, ?), (?, p, o)
;;; * SP  (s, ?, ?), (s, p, ?)


(s/def ::s (s/nilable ::hydra/id))
(s/def ::p (s/nilable ::hydra/id))
(s/def ::o (s/nilable any?))

(s/def ::Pattern (s/keys :req-un [::s
                                  ::p
                                  ::o]))

(s/def ::Solution (s/and (s/keys :req-un [::s
                                          ::p
                                          ::o])
                         #(not (nil? (:s %)))
                         #(not (nil? (:p %)))
                         #(not (nil? (:o %)))))

(s/def ::solutions (s/coll-of ::Solution))

;; Indexing interface

;; A request coming from the HTTP layer,
;; it can be used by the indexing handler to
;; deal with authentication
(s/def ::request (s/map-of keyword? any?))

;; Pagination, everything is based on integers rather
;; than strings
(s/def ::page integer?)
(s/def ::per-page integer?)
(s/def ::pagination (s/keys :req-un [::page
                                     ::per-page]))

;; Indexing request, for classes asking to filter values
;; based on a property or property-value combination
(s/def ::predicate ::hydra/id)
(s/def ::object (s/nilable ::jsonld-spec/expanded-jsonld))
(s/def ::index-request (s/keys :req-un [::predicate
                                        ::object
                                        ::pagination
                                        ::request]))
(s/def ::index-fn fn?)

;; Lookup request, for classes asking to find a particular
;; resource of the class
(s/def ::subject ::hydra/id)
(s/def ::lookup-request (s/keys :req-un [::subject
                                         ::request]))
(s/def ::lookup-fn fn?)


;; Join request, for a link to a collection, asking to
;; check which object URIs are in the collection
(s/def ::join-request (s/keys :req-un [::subject
                                       ::object
                                       ::pagination
                                       ::request]))
(s/def ::join-fn fn?)

;; Index data structure

;; Aliases for the indexing functions
(s/def ::index ::index-fn)
(s/def ::reverse-index ::index-fn)
(s/def ::resource-lookup ::lookup-fn)

;; A URI or anything that can be addressed by URI
(s/def ::uri-or-model (s/or :uri ::hydra/id
                            :model (s/and (s/keys :req-un [::hydra/common-props])
                                          #(some? (-> % :common-props ::hydra/id)))) )

;; in
(s/def ::properties (s/map-of ::uri-or-model (s/keys :req-un [::index]
                                                     :opt-un [::reverse-index])))
;; out
(s/def ::properties-map (s/map-of ::hydra/id (s/keys :req-un [::index]
                                                     :opt-un [::reverse-index])))

;; in
(s/def ::links (s/map-of ::uri-or-model ::join-fn))

;; out
(s/def ::links-map (s/map-of ::hydra/id ::join-fn))

;; in
(s/def ::ApiIndexArgs (s/map-of ::uri-or-model
                                (s/keys :opt-un [::resource
                                                 ::links
                                                 ::properties])))
;; out
(s/def ::ApiIndex (s/map-of ::hydra/id (s/keys :opt-un [::resource
                                                        ::links-map
                                                        ::properties-map])))

(s/fdef api-index
        :args (s/cat :indices ::ApiIndexArgs)
        :ret  ::ApiIndex)
(defn api-index [class-indices]
  (s/assert ::ApiIndexArgs class-indices)
  (let [index-map (->> class-indices
                       (mapv (fn [[model-or-uri class-index]]
                               (let [properties (:properties class-index)
                                     properties (->> properties
                                                     (mapv (fn [[model-or-uri v]]
                                                             [(hydra/model-or-uri model-or-uri) v]))
                                                     (into {}))
                                     links (:links class-index)
                                     links (->> links
                                                (mapv (fn [[model-or-uri v]]
                                                        [(hydra/model-or-uri model-or-uri) v]))
                                                      (into {}))]
                                 [(hydra/model-or-uri model-or-uri) (-> class-index
                                                                        (dissoc :properties)
                                                                        (dissoc :links)
                                                                        (assoc :properties-map properties)
                                                                        (assoc :links-map links))])))
                       (into {}))]
    index-map))


(defn group-handlers [handlers]
  (->> handlers
       (reduce (fn [acc {:keys [model kind] :as handler}]
                 (let [uri (-> model :common-props ::hydra/id)
                       handlers (get acc uri {})
                       handlers-kind (get handlers kind [])
                       handlers-kind (conj handlers-kind handler)
                       handlers (assoc handlers kind handlers-kind)]
                   (assoc acc uri handlers)))
               {})
       (map (fn [[uri info]]
              (assoc info :uri uri)))
       (sort #(compare (:uri %1) (:uri %2)))))

(defn select-handler [group-handlers pattern]
  (let [{:keys [resource join property]} group-handlers]
    (cond
      (and (some? resource)
           (or (some? property)
               (some? join)))  (do
                                 (log/debug (str "Group includes resource, properties and join handlers"))
                                 (->> (concat (or join [])
                                              (or property [])
                                              (or resource []))
                                     (filter #((:pattern %) pattern))
                                     first))
      (or (some? property)
          (some? join))        (do
                                 (log/debug (str "Group includes properties and join handlers"))
                                 (->> (concat
                                       (or join [])
                                       (or property []))
                                      (filter #((:pattern %) pattern))
                                      first))
      :else                   (do
                                (log/debug (str "Group includes only resource handlers"))
                                (->> resource
                                     (filter #((:pattern %) pattern))
                                     first)))))

(defn apply-resource-handler [handler {:keys [s p o] :as pattern} {:keys [page per-page]} request]
  (when (nil? s)
    (throw (ex-info "Cannot apply resource handler for nil subject triple" pattern)))
  (log/debug "Applying resource handler")
  (let [{:keys [results count]} ((:handler handler) {:subject (get s "@id")
                                                     :predicate (get p "@id")
                                                     :object o
                                                     :request request})]

    {:results (if (some? results)
                (let [triples (flatten (map payload/->triples (flatten [results])))
                      filtered-triples (payload/filter-triples pattern triples)
                      selected-triples (->> filtered-triples
                                            (drop (* (dec page) per-page))
                                            (take per-page))]
                  selected-triples)
                [])
     :count (or count 1)}))

(defn apply-property-handler [handler {:keys [s p o] :as pattern} pagination request]
  (when (nil? p)
    (throw (ex-info "Cannot apply property handler for nil predicate triple" pattern)))
  (log/debug "Applying property handler")
  (let [{:keys [results count]} ((:handler handler) {:subject (get s "@id")
                                                     :predicate (get p "@id")
                                                     :object o
                                                     :pagination pagination
                                                     :request request})]
    (log/debug "Got " (clojure.core/count results) " results")
    {:results (if (some? results)
                (let [triples (->> results (map payload/->triples ) flatten)
                      filtered-triples (payload/filter-triples pattern triples)]
                  filtered-triples)
                [])
     :count count}))

(defn apply-join-handler [handler {:keys [s p o] :as pattern} pagination request]
  (log/debug "Applying join handler")
  (let [{:keys [results count]} ((:handler handler) {:subject  (get s "@id")
                                                     :preicate (get p "@id")
                                                     :object   (get o "@id")
                                                     :pagination pagination
                                                     :request request})]
    {:results (if (some? results)
                (map (fn [{:keys [subject object]}]
                       {:s {"@id" (get subject "@id")} :p p :o {"@id" (get object "@id")}})
                     results)
                [])
     :count count}))

(defn paginate-handlers [pattern-handlers
                         pattern
                         pagination
                         request]
  (log/debug (str "Found  " (count pattern-handlers) " to paginate"))
  (log/debug "Pattern:")
  (log/debug pattern)
  (let [grouped-handlers (group-handlers pattern-handlers)
        selected-handlers (->> grouped-handlers
                               (map #(select-handler % pattern))
                               (filter some?))
        {:keys [page group per-page]} pagination]
    (log/debug (str "Selected " (count selected-handlers) " handlers"))
    (if (empty? selected-handlers)
      {:results []
       :page nil
       :group nil
       :count nil}
      (loop [group group]
        (let [selected-handler (nth selected-handlers group)
              _ (log/debug (str "Selected handler kind " (:kind selected-handler)))
              {:keys [results count]} (condp = (:kind selected-handler)
                                        :resource  (apply-resource-handler selected-handler pattern pagination request)
                                        :property  (apply-property-handler selected-handler pattern pagination request)
                                        :join      (apply-join-handler selected-handler pattern pagination request)
                                        (throw (ex-info (str "Unknown handler kind" (:kind selected-handler))
                                                        selected-handler)))]
          (if (empty? results)
            (let [next-group (inc group)
                  next-page 1]
              (if (< next-group (clojure.core/count selected-handlers))
                (recur next-group)
                {:results []
                 :page nil
                 :group nil
                 :count nil}))
            {:results results
             :page (inc page)
             :group group
             :count count}))))))

(defn link-matching-model?
  "Checks if a link URI has a defined route and the
   model for the route matches the provided model, whether
   it is a class or a supported-property link"
  [uri model]
  (log/debug "Checking if URI " uri " matches model " (-> model :common-props ::hydra/id))
  (let [match (routing/match-uri uri)]
    (log/debug (some? match))
    (if (some? match)
      (let [matched-model (:model match)
            matched-model-uri (hydra/model-or-uri matched-model)]
        (cond
          (hydra/class-model? model) (= matched-model-uri (-> model :common-props ::hydra/id))
          (hydra/supported-property? model) (or (= matched-model-uri (-> model :property :common-props ::hydra/id))
                                                (= matched-model-uri (-> model :common-props ::hydra/id)))
          :else false))
      false)))

(defn accessible-property?
  "Checks if a property, identified by the provided URI is
   defined and not write-only for a given class"
  [uri class-model]
  (let [property (->> class-model
                      :supported-properties
                      (map (fn [{:keys [property]}]
                             (-> property :common-props ::hydra/id)))
                      (filter #(= % uri))
                      first)]
    (if (some? property)
      (not (-> property :property-props ::writeonly))
      false)))

(defn generate-class-pattern-handler [model {:keys [resource properties-map links-map] :as indices} api]
  (let [resource-lookup-handler (if (some? resource)
                                  [{:pattern #(do
                                                (log/debug "Resource pattern: checking if " (-> % :s (get "@id")) " and " (-> % :p (get "@id"))" matches " (-> model :common-props ::hydra/id))
                                                (and (some? (:s %))
                                                     (link-matching-model? (-> % :s (get "@id")) model)
                                                     (or (nil? (:p %))
                                                         (accessible-property? (-> % :p (get "@id")) model))))
                                    :model model
                                    :kind :resource
                                    :handler resource}]
                                  [])
        properties-handlers (->> (or properties-map {})
                                 (mapv (fn [[property handlers-map]]
                                         {:pattern #(do
                                                      (log/debug "Property pattern: checking if " (-> % :s (get "@id")) " and " (-> % :p (get "@id"))" matches " property)
                                                      (and
                                                       (or (-> % :s nil?)
                                                           (link-matching-model? (-> % :s (get "@id")) model))
                                                       (= (-> % :p (get "@id")) property)))
                                          :handler (:index handlers-map)
                                          :kind :property
                                          :property-model (hydra/find-model api property)
                                          :model model})))
        join-handlers (->> (or links-map {})
                           (mapv (fn [[link handler]]
                                   (let [link-model (hydra/find-model api link)
                                         property (-> link-model :property :common-props ::hydra/id)]
                                     {:pattern #(do
                                                  (log/debug "Join pattern: checking if " (-> % :p (get "@id")) " matches " property)
                                                  (= (-> % :p (get "@id")) property))
                                      :handler handler
                                      :kind :join
                                      :link-model link-model
                                      :model model}))))]
    {:resource-handlers resource-lookup-handler
     :properties-handlers properties-handlers
     :join-handlers join-handlers}))

(defn build-pattern-handlers [api api-index]
  (->> api-index
       (mapv (fn [[uri indices]]
               (let [model (hydra/find-model api uri)]
                 (generate-class-pattern-handler model indices api))))
       flatten
       (reduce (fn [acc {:keys [resource-handlers
                               properties-handlers
                               join-handlers]}]
                 (-> acc
                     (assoc :resource-handlers (concat (or resource-handlers [])
                                                       (:resource-handlers acc)))
                     (assoc :properties-handlers (concat (or properties-handlers [])
                                                         (:properties-handlers acc)))
                     (assoc :join-handlers (concat (or join-handlers [])
                                                   (:join-handlers acc)))))
               {:resource-handlers []
                :properties-handlers []
                :join-handlers []})))

(defn make-indexer [api api-index]
  (let [pattern-handlers (build-pattern-handlers api api-index)]
    (fn [{:keys [s p o] :as pattern} pagination request]
      (log/debug "Checking index for pattern " pattern " and pagination " pagination)
      (let [compatible-handlers (cond
                                  ;; no inverse lookups allowed for now
                                  ;; (? ? o)
                                  (and (nil? s) (nil? p))   []

                                  ;; only p -> properties + join lookups
                                  ;; (? p ?) (? p o)
                                  (and (nil? s) (some? p))  (concat (->>
                                                                     pattern-handlers
                                                                     :properties-handlers)
                                                                    (->>
                                                                     pattern-handlers
                                                                     :join-handlers))
                                  ;; some s and p -> first property indices, fallback resource handlers
                                  ;; (s p ?) (s p o)
                                  (and (some? s) (some? p)) (concat (->>
                                                                     pattern-handlers
                                                                     :properties-handlers)
                                                                    (->>
                                                                     pattern-handlers
                                                                     :join-handlers)
                                                                    (->>
                                                                     pattern-handlers
                                                                     :resource-handlers))
                                  ;; only s -> only resource handlers
                                  ;; (s ? ?) (s ? o)
                                  (and (some? s) (nil? p))  (->> pattern-handlers
                                                                 :resource-handlers)
                                  ;; remaining patterns
                                  ;; (? ? ?)
                                  :else [])

            pattern-handlers (->> compatible-handlers
                                  (filter (fn [pattern-handler]
                                            (payload/triple-match? (:pattern pattern-handler) pattern))))]
        (log/debug "Found " (count compatible-handlers) " compatible handlers")
        (log/debug "Found " (count pattern-handlers) " pattern compatible handlers")
        (if (empty? pattern-handlers)
          []
          (paginate-handlers pattern-handlers
                             pattern
                             pagination
                             request))))))

(defn index-response [params-map {:keys [results page group per-page count] :as output} {:keys [server-port server-name uri query-string scheme] :as request}]
  (let [base-uri  (reduce (fn [acc k]
                            (if (some? (get params-map k))
                              (str acc
                                   (if (string/ends-with? acc "?") "" "&")
                                   (name k) "=" (get params-map k))
                              acc))
                          (str (name scheme) "://" server-name (if (some? server-port) (str ":" server-port) "") uri "?")
                          [:subject :predicate :object])
        base-uri (if (string/ends-with? base-uri "?") (string/replace base-uri "?" "") base-uri)
        this-uri (str (name scheme) "://" server-name (if (some? server-port) (str ":" server-port) "") uri (if (some? query-string) "?" "") query-string)
        base (str (name scheme) "://" server-name (if (some? server-port) (str ":" server-port) ""))
        metadata-uri (str this-uri "#metadata")
        dataset-uri (str this-uri "#dataset")

        response (payload/->trig
                  ;; the context for the response data
                  (payload/context)
                  ;; the meta data of the ldf
                  [[
                    (u metadata-uri)
                    [
                     [(u metadata-uri) "foaf:primaryTopic" (u this-uri)]
                     [(u dataset-uri) "hydra:member" (u dataset-uri)]
                     [(u dataset-uri) "a" "void:Dataset, hydra:Collection"]
                     [(u dataset-uri) "void:subset" (u base-uri)]
                     [(u dataset-uri) "void:uriLookupEndpoint" (l (str base (:uri request) "{?subject,predicate,object}"))]
                     [(u dataset-uri) "hydra:search" "_:triplePattern"]
                     ["_:triplePattern" "hydra:template" (l (str base (:uri request) "{?subject,predicate,object}"))]
                     ["_:triplePattern" "hydra:variableRepresentation" "hydra:ExplicitRepresentation"]
                     ["_:triplePattern" "hydra:mapping" "_:subject"]
                     ["_:triplePattern" "hydra:mapping" "_:predicate"]
                     ["_:triplePattern" "hydra:mapping" "_:object"]
                     ["_:subject" "hydra:variable" (l "subject")]
                     ["_:subject" "hydra:property" "rdf:subject"]
                     ["_:predicate" "hydra:variable" (l "predicate")]
                     ["_:predicate" "hydra:property" "rdf:predicate"]
                     ["_:object" "hydra:variable" (l "object")]
                     ["_:object" "hydra:property" "rdf:object"]

                     [(u this-uri) "void:subset" (u base-uri)]
                     (if (some? results)
                       [(u this-uri) "a" "hydra:PartialCollectionView"]
                       [(u this-uri) "a" "hydra:Collection"])
                     [(u this-uri) "dcterms:source" (u dataset-uri)]
                     (if (and (some? results) (some? count))
                       [(u this-uri) "hydra:totalItems" (d (or count 0) "xsd:integer")]
                       [(u this-uri) "hydra:totalItems" (d 10000 "xsd:integer")])
                     (if (and (some? results) (some? count))
                       [(u this-uri) "void:triples" (d (or count 0) "xsd:integer")]
                       [(u this-uri) "void:triples" (d 10000 "xsd:integer")])
                     (if (some? results)
                       [(u this-uri) "hydra:first" (u (str base-uri (if (string/index-of base-uri "?") "&" "?") "page=" 1 "&group=" 0 "&per-page=" (or per-page 50)))]
                       nil)
                     (if (some? page)
                       [(u this-uri) "hydra:next" (u (str base-uri (if (string/index-of base-uri "?") "&" "?") "page=" (or page 1) "&group=" (or group 0) "&per-page=" (or per-page 50)))]
                       nil)
                     ]
                    ]
                   ;; the actual data in the fragment
                   ["" (map (fn [{:keys [s p o]}]
                              [(u (get s "@id")) (u (get p "@id")) (if (some? (get o "@id"))
                                                                     (u (get o "@id"))
                                                                     (if (some? (get o "@type"))
                                                                       (d (get o "@value") (get o "@type"))
                                                                       (l (get o "@value"))))])
                            results)]
                   ])]
    {:body response :headers {"Content-Type" "application/trig;charset=utf-8"} :status 200}))
