(ns levanzo.payload
  (:require [levanzo.routing :as routing]
            [levanzo.namespaces :as lns]
            [levanzo.hydra :as hydra]
            [levanzo.jsonld :as jsonld]
            [levanzo.spec.jsonld :as jsonld-spec]
            [clojure.spec :as s]
            [clojure.string :as string]
            [clojure.test.check.generators :as tg]
            [cemerick.url :refer [url-encode] :as url]))

(s/def ::common-props (s/with-gen (s/keys :req [::hydra/id])
                        #(tg/fmap (fn [uri]
                                    {::hydra/id uri})
                                  (s/gen ::hydra/id))))
(s/def ::uri-or-model-args (s/and (s/or :uri ::hydra/id
                                        :with-uri (s/and
                                                   (s/keys :req-un [::common-props])
                                                   #(some? (-> % (get-in [:common-props ::hydra/id])))))))

(s/def ::model ::uri-or-model-args)
(s/def ::property ::uri-or-model-args)
(s/def ::args (s/map-of keyword? any?))
(s/def ::value any?)

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
  ([{:keys [vocab base ns] :as context}]
   (let [context (-> context
                     (dissoc :vocab)
                     (dissoc :base)
                     (dissoc :ns))
         prefixes (->> (or ns [])
                       (map (fn [ns] [(name ns) (lns/prefix-for-ns (name ns))]))
                       (filter (fn [[ns uri]] (some? uri)))
                       (into {}))
         vocab (if (lns/default-ns?)
                 (or vocab (lns/default-ns))
                 vocab)]
     (reset! *context*
             (->> context
                  (merge {"hydra" (lns/hydra)
                          "rdfs" (lns/rdfs)})
                  (merge prefixes)
                  (merge {"@vocab" vocab
                          "@base" base})
                  (filter (fn [[k v]] (some? v)))
                  (into {})))))
  ([] (merge @*context*
             {"hydra" (lns/hydra)
              "rdfs" (lns/rdfs)})))

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
  ([json-ld options]
   (let [with-context (:context options)
         res (jsonld/compact-json-ld json-ld (context))
         res (if (map? res) res (first res))]
     (if with-context res (dissoc res "@context"))))
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

(s/def ::args (s/* (s/or :keys keyword?
                         :val any?)))
(s/def ::model ::uri-or-model-args)
(s/def ::base ::jsonld-spec/uri)

(s/fdef link-for
        :args (s/cat :args-map (s/keys :req-un [::model]
                                       :opt-un [::args
                                                ::base]))
        :ret ::hydra/id)
(defn link-for [{:keys[model args base]
                 :as link-args
                 :or {args []}}]
  (s/assert (s/keys :req-un [::model]
                    :opt-un [::args
                             ::base])
            link-args)
  (let [args (concat [(lns/resolve (uri-or-model model))]
                     (->> args (into []) flatten))]
    (let [link (apply routing/link-for args)]
      (if (string/index-of link "://")
        link
        (str (or base "") link)))))


(s/fdef jsonld
        :args (s/with-gen
                (s/cat :props (s/+ (s/or :map-tuple   (s/tuple string? any?)
                                         :jsondl-pair (s/map-of string? any?))))
                #(tg/fmap (fn [vals]
                            (->> vals
                                 (map (fn [[k v]]
                                        (if (string? v)
                                          [k {"@id" v}]
                                          [k {"@value" v}])))
                                 (into {})))
                          (tg/vector
                           (tg/tuple (s/gen ::jsonld-spec/uri)
                                     (tg/one-of [(s/gen ::jsonld-spec/uri)
                                                 tg/pos-int])))))
        :ret ::jsonld-spec/expanded-jsonld)
(defn jsonld
  "Builds a new JSON-LD object"
  [& props]
  (s/assert (s/coll-of (s/or :jsondl-pair (s/map-of string? any?)
                             :map-tuple (s/tuple string? any?))) props)
  (let [json (into {} props)
        context (get json "@context" {})
        context (merge @*context* context)]
    (-> (if (not= {} context)
          (assoc json "@context" context)
          json)
        expand)))

(defn instance
  "Builds a new JSON-LD object that is an instance of the provided class"
  [class-model & props]
  (apply jsonld
         (concat [["@type" (hydra/id class-model)]] props)))


(defn merge-jsonld
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
  ([{:keys [model args base] :or {args {}} :as id-args}]
   {"@id" (link-for id-args)}))


(defn type
  "Generates a JSON-LD [@type URI] pair"
  ([model]
   {"@type" (uri-or-model model)}))

(s/fdef supported-property
        :args (s/cat :args (s/keys :req-un [::property
                                            ::value]))
        :ret (s/map-of string? any?))
(defn supported-property
  "Generates a JSON-LD [property literal-value] pair"
  ([{:keys [property value] :as args}]
   (s/assert (s/keys :req-un [::property ::value]) args)
   {(uri-or-model property) value}))

(s/fdef supported-link
        :args (s/cat :args (s/keys :req-un [::property
                                            ::model]
                                   :opt-un [::args
                                            ::base]))
        :ret (s/map-of string? any?))
(defn supported-link
  "Generates a JSON-LD [property {@id link}]  pair"
  ([{:keys[property model args base]}]
   (let [property-uri (uri-or-model property)]
     (when (nil? property-uri)
       (throw (ex-info (str "Cannot find link information for model " property) {:model property})))
     (let [id-args {:model model :args args :base base}
           id-args (->> id-args (filter (fn [[k v]] (some? v))) (into {}))]
       {property-uri (id id-args)}))))

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
        :args (s/cat :view-map (s/keys :req-un [::model
                                                ::pagination-param
                                                ::model
                                                ::args
                                                ::current]
                                       :opt-un [::args
                                                ::first
                                                ::last
                                                ::next
                                                ::previous]))
        :ret ::view)
(defn partial-view
  "Generates a collection partial view for a collection"
  [{:keys [model args current first last next previous pagination-param] :or {args {}}}]
  (let [collection-id (link-for {:model model :args args})]
    [(lns/hydra "view") (->> {"@id" (add-param collection-id pagination-param current)
                              "@type" (lns/hydra "PartialCollectionView")}
                             (jsonld/link-if-some (add-param collection-id pagination-param first)
                                                  (lns/hydra "first"))
                             (jsonld/link-if-some (add-param collection-id pagination-param last)
                                                  (lns/hydra "last"))
                             (jsonld/link-if-some (add-param collection-id pagination-param next)
                                                  (lns/hydra "next"))
                             (jsonld/link-if-some (add-param collection-id pagination-param previous)
                                                  (lns/hydra "previous")))]))

(s/def ::required boolean?)
(s/def ::variable keyword?)
(s/def ::range ::hydra/id)
(s/def ::template string?)
(s/def ::representation #{:basic :explicit})
(s/def ::mapping (s/coll-of (s/keys :req-un [::variable
                                             ::range
                                             ::required])
                            :min-count 1))
(s/def ::iri-template (s/keys :req-un [::property
                                       ::template
                                       ::mapping]
                              :opt-un [::representation]))

(s/fdef supported-template
        :args (s/cat :iri-template ::iri-template)
        :ret (s/tuple
              ::hydra/id
              (s/and map?
                     #(= (get % "@type") (lns/hydra "IriTemplate"))
                     #(some? (get % (lns/hydra "template")))
                     #(some? (get % (lns/hydra "variableRepresentation")))
                     #(some? (get % (lns/hydra "mapping"))))))
(defn supported-template
  "Generates a IRI template for a TemplatedLink property"
  [{:keys [property template representation mapping] :or {representation :basic} :as iri-template}]
  (s/assert ::iri-template iri-template)
  [(lns/resolve (uri-or-model property))
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

(defn- triple->jsonld [v]
  (let [type (get v "type")]
    (if (= type "literal")
      (let [literal {"@value" (get v "value")
                     "@type" (get v "datatype")}]
        (if (= "http://www.w3.org/2001/XMLSchema#string"
               (get literal "@type"))
          (dissoc literal "@type")
          literal))
      {"@id" (get v "value")})))


(defn ->triples
  "Transforms a JSON-LD document into a sequence of triples"
  [jsonld]
  (let [jsonld (if (nil? (get jsonld "@context"))
                 (assoc jsonld "@context" (context))
                 jsonld)]
    (->> jsonld
         expand
         jsonld/triples
         (map (fn [triple]
                (let [subject (get triple "subject")
                      predicate (get triple "predicate")
                      object (get triple "object")]
                  {:s (triple->jsonld subject)
                   :p (triple->jsonld predicate)
                   :o (triple->jsonld object)}))))))

(defn triple-match? [pattern {:keys [s p o] :as triple}]
  (let [ps (:s pattern)
        pp (:p pattern)
        po (:o pattern)]
    (and (if (nil? ps)
           true
           (= ps s))
         (if (nil? pp)
           true
           (= pp p))
         (if (nil? po)
           true
           (= po o)))))

(defn triple-fill [pattern {:keys [s p o] :as triple}]
  (let [ps (:s pattern)
        pp (:p pattern)
        po (:o pattern)]
    {:s (or ps s)
     :p (or pp p)
     :o (or po o)}))

(defn filter-triples
  [pattern triples]
  (->> triples
       (filter (fn [triple]
                 (triple-match? pattern triple)))))


(defn fill-pattern
  [pattern triples]
  (->> triples
       (map (fn [triple]
              (if (triple-match? pattern triple)
                (triple-fill pattern triple)
                nil)))
       (filter some?)))

(defn expand-uri [uri]
  (let [data {"@context" (merge (context)
                                @lns/*ns-register*)
              "@id" uri
              "http://test.com" "foo"}]
    (-> data
        expand
        (get "@id"))))

(defn expand-literal [val]
  (let [data {"@context" (merge (context)
                                @lns/*ns-register*)
              "@id" "http://test.com"
              "http://test.com/p" val}]
    (-> data
        expand
        (get "http://test.com/p")
        first)))

(defn compact-uri [uri]
  (let [data {"@context" (merge (context)
                                @lns/*ns-register*)
              "@id" "http://test.com/uri"
              uri {"@value" 2}}]
    (-> data
        (compact {:context false})
        keys
        (->> (filter #(not= \@ (first %))))
        first)))

(defn compact-literal [val]
  (let [data {"@context" (merge (context)
                                @lns/*ns-register*)
              "@id" "http://test.com"
              "http://test.com/p" val}]
    (-> data
        compact
        (get "http://test.com/p"))))
