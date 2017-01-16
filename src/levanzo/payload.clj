(ns levanzo.payload
  (:require [levanzo.routing :as routing]
            [levanzo.namespaces :as lns]
            [levanzo.hydra :as hydra]
            [levanzo.jsonld :as jsonld]
            [levanzo.spec.jsonld :as jsonld-spec]
            [clojure.spec :as s]
            [clojure.string :as string]
            [clojure.test.check.generators :as tg]
            [cemerick.url :refer [url-encode]]))

(s/def ::common-props (s/with-gen (s/keys :req [::hydra/id])
                        #(tg/fmap (fn [uri]
                                    {::hydra/id uri})
                                  (s/gen ::hydra/id))))
(s/def ::uri-or-model-args (s/or :uri ::hydra/id
                                 :with-uri (s/keys :req-un [::common-props])))

(def ^:dynamic *context* (atom {}))

(s/def ::vocab ::hydra/id)
(s/def ::base ::hydra/id)
(s/def ::ns (s/coll-of (s/or :string-ns string?
                             :kw-ns keyword?)))
(s/fdef context
        :args (s/cat :options (s/keys :opt-un [::vocab
                                               ::base
                                               ::ns]))
        :ret (s/map-of string? any?))
(defn context
  "Set-ups a new JSON-LD context that will be used
   by all the functions manipulating JSON-LD documents in this ns.
   keys are:
      vocab: the @vocab value for the context
      base:  the @base value for the context
      ns:    list of namespace aliases that will be added to the context"
  ([{:keys [vocab base ns]}]
   (let [prefixes (->> (or ns [])
                       (map (fn [ns] [(name ns) (lns/prefix-for-ns (name ns))]))
                       (filter (fn [[ns uri]] (some? uri)))
                       (into {}))
         prefixes (merge prefixes {"hydra" (lns/hydra)
                                   "rdfs" (lns/rdfs)})
         vocab (if (lns/default-ns?)
                 (or vocab (lns/default-ns))
                 vocab)]
     (reset! *context*
             (->> prefixes
                  (merge {"@vocab" vocab
                          "@base" base})
                  (filter (fn [[k v]] (some? v)))
                  (into {})))))
  ([] @*context*))

(s/def ::context boolean?)
(s/fdef compact
        :args (s/or :2-arg (s/cat :json-ld (s/map-of string? any?)
                                  :options (s/keys :opt-un [::context]))
                    :1-arg (s/cat :json-ld (s/map-of string? any?)))
        :ret (s/map-of string? any?))
(defn compact
  "Applies the compact JSON-LD algorithm to the provided JSON-LD
   document using the namespace @context.
   If the option {:context false} is passed the context will be
   removed from the compacted document."
  ([json-ld {:keys [context]}]
   (let [res (jsonld/compact-json-ld json-ld @*context*)
         res (if (map? res) res (first res))]
     (if context res (dissoc res "@context"))))
  ([json-ld] (compact json-ld {:context true})))

(s/fdef expand
        :args (s/cat :jsonld (s/map-of string? any?))
        :ret ::jsonld-spec/expanded-jsonld)
(defn expand
  "Expands the provided JSON-LD document"
  [json-ld]
  (let [res (jsonld/expand-json-ld json-ld)]
    (if (map? res) res (or (first res) {}))))

(defn normalize
  "Expands and then flatten a JSON-LD document"
  [json-ld]
  (-> json-ld
      expand
      jsonld/flatten-json-ld))


(s/fdef uri-or-model
        :args (s/cat :object ::uri-or-model-args)
        :ret ::hydra/id)
(defn uri-or-model [object]
  (s/assert ::uri-or-model-args object)
  (let [uri (if (string? object)
              object
              (-> object :common-props ::hydra/id))]
    (if (string? uri)
      uri
      (throw (Exception. (str "Cannot obtain URI from " object))))))

(s/fdef link-for
        :args (s/cat :model ::uri-or-model-args
                     :args (s/map-of keyword? any?))
        :ret (s/or :id ::hydra/id
                   :path ::jsonld-spec/path))
(defn link-for [model args]
  (s/assert ::uri-or-model-args model)
  (s/assert (s/map-of keyword? any?) args)
  (let [args (concat [(lns/resolve (uri-or-model model))]
                     (->> args (into []) flatten))]
    (apply routing/link-for args)))

(s/fdef json-ld
        :args (s/cat :props (s/* (s/or :tuple       (s/tuple string? any?)
                                       :jsondl-pair (s/map-of string? any?))))
        :ret ::jsonld-spec/expanded-jsonld)
(defn json-ld
  "Builds a new JSON-LD object"
  [& props]
  (s/assert (s/coll-of (s/or :map-tuple (s/map-of string? any?)
                             :tupe (s/tuple string? any?))) props)
  (let [json (into {} props)
        context (get json "@context" {})
        context (merge @*context* context)]
    (-> (if (not= {} context)
          (assoc json "@context" context)
          json)
        expand)))

(defn merge
  "Merges two json-ld documents for the same ID"
  ([a b]
   (let [a (expand a)
         b (expand b)]
     (clojure.core/merge a b))))

(defn title
  [title]
  ["hydra:title" title])

(defn description
  [description]
  ["hydra:description" description])

(defn total-items
  [count]
  ["hydra:totalItems" count])

(defn members
  "Generates a Hydra [hydra-member instances] tupes"
  [members]
  ["hydra:member" members])

(defn total-items
  [count]
  ["hydra:totalItems" count])

(defn id
  "Generates a JSON-LD [@id TYPE] pair"
  ([model args]
   {"@id" (link-for model args)})
  ([model] (id model {})))


(defn type
  "Generates a JSON-LD [@type URI] pair"
  ([model]
   {"@type" (uri-or-model model)}))

(s/fdef supported-property
        :args (s/cat :property ::uri-or-model-args
                     :value any?)
        :ret (s/map-of string? any?))
(defn supported-property
  "Generates a JSON-LD [property literal-value] pair"
  ([property value]
   (s/assert ::uri-or-model-args property )
   {(uri-or-model property) value}))

(s/def ::supported-property-args (s/or
                                  :1-arg (s/cat :property ::uri-or-model-args)
                                  :2-arg (s/or :model-model (s/cat :property-model ::uri-or-model-args
                                                                   :target-model ::uri-or-model-args)
                                               :model-args  (s/cat :model ::uri-or-model-args
                                                                   :args  (s/map-of keyword? any?)))
                                  :3-arg (s/cat :property-model ::uri-or-model-args
                                                :target-model ::uri-or-model-args
                                                :args (s/map-of keyword? any?))))
(s/fdef supported-link
        :args ::supported-property-args
        :ret (s/map-of string? any?))
(defn supported-link
  "Generates a JSON-LD [property {@id link}]  pair"
  ([property-model target-model args]
   (let [uri (uri-or-model property-model)]
     (s/assert ::supported-property-args [property-model target-model args])
     {uri (id target-model args)}))
  ([arg1 arg2]
   (s/assert ::supported-property-args [arg1 arg2])
   (if (map? arg2)
     (supported-link arg1 arg1  arg2)
     (supported-link arg1 arg2  {})))
  ([model]
   (s/assert ::supported-property-args [model])
   (supported-link model model)))

(s/def ::current integer?)
(s/def ::first integer?)
(s/def ::last integer?)
(s/def ::next integer?)
(s/def ::previous integer?)
(s/def ::pagination-param string?)
(s/def ::view (s/tuple #{(lns/hydra "view")}
                       (s/every (s/or
                                 :id (s/tuple #{"@id"} string?)
                                 :type (s/tuple #{"@type"} string?)
                                 :first (s/tuple #{(lns/hydra "first")} ::jsonld-spec/link)
                                 :last (s/tuple #{(lns/hydra "last")} ::jsonld-spec/link)
                                 :previous (s/tuple #{(lns/hydra "previous")} ::jsonld-spec/link)
                                 :next (s/tuple #{(lns/hydra "next")} ::jsonld-spec/link))
                                :into {})))

(defn add-param [base-uri param value]
  (if (some? value)
    (if (string/index-of base-uri "?")
      (str base-uri "&" (url-encode param) "=" value)
      (str base-uri "?" (url-encode param) "=" value))
    nil))

(s/fdef partial-view
        :args (s/cat :collection-uri ::hydra/id
                     :view-map (s/keys :req-un [::pagination-param
                                                ::current]
                                       :opt-un [::first
                                                ::last
                                                ::next
                                                ::previous]))
        :ret ::view)
(defn partial-view
  "Generates a collection partial view for a collection"
  [collection-id {:keys [current first last next previous pagination-param]}]
  [(lns/hydra "view") (->> {"@id" (add-param collection-id pagination-param current)
                            "@type" (lns/hydra "PartialCollectionView")}
                           (jsonld/link-if-some (add-param collection-id pagination-param first)
                                                (lns/hydra "first"))
                           (jsonld/link-if-some (add-param collection-id pagination-param last)
                                                (lns/hydra "last"))
                           (jsonld/link-if-some (add-param collection-id pagination-param next)
                                                (lns/hydra "next"))
                           (jsonld/link-if-some (add-param collection-id pagination-param previous)
                                                (lns/hydra "previous")))])

(s/def ::required boolean?)
(s/def ::variable keyword?)
(s/def ::range ::hydra/id)
(s/def ::template string?)
(s/def ::representation #{:basic :explicit})
(s/def ::mapping (s/coll-of (s/keys :req-un [::variable
                                             ::range
                                             ::required])
                            :min-count 1))
(s/def ::iri-template (s/keys :req-un [::template
                                       ::mapping]
                              :opt-un [::representation]))

(s/fdef supported-template
        :args (s/cat :model ::uri-or-model-args
                     :iri-template ::iri-template)
        :ret (s/tuple
              ::hydra/id
              (s/and map?
                     #(= (get % "@type") (lns/hydra "IriTemplate"))
                     #(some? (get % (lns/hydra "template")))
                     #(some? (get % (lns/hydra "variableRepresentation")))
                     #(some? (get % (lns/hydra "mapping"))))))
(defn supported-template
  "Generates a IRI template for a TemplatedLink property"
  [model {:keys [template representation mapping] :or {representation :basic} :as iri-template}]
  (s/assert ::uri-or-model-args model)
  (s/assert ::iri-template iri-template)
  [(lns/resolve (uri-or-model model))
   {"@type" (lns/hydra "IriTemplate")
    (lns/hydra "template") template
    (lns/hydra "variableRepresentation") (if (= :basic representation) "BasicRepresentation" "ExplicitRepresentation")
    (lns/hydra "mapping") (->> mapping
                               (mapv (fn [{:keys [variable range required]}]
                                       {"@type" (lns/hydra "IriTemplateMapping")
                                        (lns/hydra "variable") (name variable)
                                        (lns/hydra "property") {"@type" (lns/rdfs "Property")
                                                                (lns/rdfs "range"){"@id" range}}
                                        (lns/hydra "required") required})))}])
