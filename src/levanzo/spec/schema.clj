(ns levanzo.spec.schema
  "Functions providing generators for API components and JSON-LD payloads"
  (:require [levanzo.schema :as schema]
            [levanzo.namespaces :refer [xsd resolve]]
            [levanzo.hydra :as hydra]
            [levanzo.spec.jsonld :as jsonld-spec]
            [levanzo.utils :as utils]
            [clojure.spec :as s]
            [clojure.spec.gen :as gen]
            [clojure.spec.test :as stest]
            [clojure.test.check.generators :as tg]))

(defn make-xsd-type-gen [type]
  (condp = type
    (xsd "string") (tg/fmap (fn [v] {"@value" v
                                    "@type" (xsd "string")})
                            (s/gen string?))
    (xsd "float")  (tg/fmap (fn [v] {"@value" v
                                    "@type" (xsd "float")})
                            (s/gen float?))
    (xsd "decimal") (tg/fmap
                     (fn [v] {"@value" v
                             "@type" (xsd "decimal")})
                     (s/gen integer?))
    (xsd "boolean") (tg/fmap
                     (fn [v] {"@value" v
                             "@type" (xsd "boolean")})
                     (s/gen boolean?))
    (first (s/gen (s/or
                   :string string?
                   :number number?
                   :boolean boolean?)))))

(defn valid-for-mode? [mode property-props]
  (let [readonly (or (-> property-props ::hydra/readonly) false)
        writeonly (or (-> property-props ::hydra/writeonly) false)]
    (condp = mode
      :read (not writeonly)
      :write (not readonly)
      :update (and (not writeonly) (not readonly)))))

(defn valid-for-cardinality? [value property-props]
  (let [min-count (or (-> property-props ::hydra/min-count) nil)
        max-count(or (-> property-props ::hydra/max-count) nil)
        valid-min (if (some? min-count)
                    (>= (count value) min-count)
                    true)
        valid-max (if (some? max-count)
                    (<= (count value) max-count)
                    true)]
    (and valid-max valid-min)))

(defn optional-gen [g]
  (tg/one-of [g
              (tg/return nil)]))

(declare make-payload-gen)

(defn gen-for-property
  ([mode property api] (gen-for-property mode property api {}))
  ([mode {:keys [property-props property] :as supported-property} api options]

   (let [property-id (-> property :common-props ::hydra/id)
         min-count (-> property-props ::hydra/min-count)
         max-count (-> property-props ::hydra/max-count)
         is-link (-> property :is-link)
         range (-> property :rdf-props ::hydra/range)
         required (::hydra/required property-props)
         property-gen (if is-link
                        (cond
                          (some? (get options property-id)) (get options property-id)
                          :else (s/gen ::jsonld-spec/uri))
                        (cond (some? (get options property-id)) (get options property-id)
                              (schema/xsd-uri? range)           (make-xsd-type-gen range)
                              :else (let [class (hydra/find-class api range)]
                                      ;; Careful! this will explode with cyclic APIs
                                      (do
                                        (make-payload-gen mode class api)))))]
     (tg/tuple
      (tg/return property-id)
      (tg/vector property-gen (or min-count 1) (or max-count 1))))))

(defn with-gen-id [g options]
  (tg/bind g
           (fn [jsonld]
             (tg/fmap (fn [uri]
                        (assoc jsonld "@id" uri))
                      (if (get options "@id")
                        (get options "@id")
                        (s/gen ::jsonld-spec/uri))))))

(defn make-collection-gen [mode class api options]
  (let [member-class (:member-class class)
        member-gen (if member-class
                     (make-payload-gen mode (hydra/find-model api member-class) api options)
                     (tg/fmap (fn [uri] {"@id" uri})
                              (s/gen ::jsonld-spec/uri)))]
    (with-gen-id
      (tg/fmap (fn [elements]
                 {"@type" (-> class :common-props ::hydra/id)
                  (resolve "hydra:member") elements})
               (tg/vector member-gen 1 5))
      options)))

(defn make-payload-gen
  ([mode class api] (make-payload-gen mode class api {}))
  ([mode class api options]
   (if (hydra/collection-model? class)
     (make-collection-gen mode class api options)
     (with-gen-id
       (tg/fmap (fn [properties]
                  (let [m (->> properties
                               ;; remove properties not valid for the read mode, they
                               ;; have been marked as nil
                               (filter some?)
                               ;; build the JSON-LD values
                               (mapv (fn [[k vs]]
                                       [k (->> vs
                                               (filter some?)
                                               (mapv (fn [v]
                                                       (if (string? v) {"@id" v} v))))]))
                               ;; collect the JSON-LD object
                               (into {}))]
                    (-> m
                        (assoc "@id" "http://test.com/generated")
                        (assoc "@type" [(-> class :common-props ::hydra/id)]))))
                (tg/bind
                 (tg/return (-> class :supported-properties))
                 (fn [properties]
                   (let [generators (->> properties
                                         (mapv (fn [property]
                                                 (if (valid-for-mode? mode (:property-props property))
                                                   (let [gen-prop (gen-for-property mode property api options)]
                                                     (if (-> property :property-props ::hydra/required)
                                                       gen-prop
                                                       (tg/one-of [(tg/return nil) gen-prop])))
                                                   (tg/return nil)))))]
                     (apply tg/tuple generators)))))
       options))))

(defn make-property-gen [type]
  (tg/fmap (fn [uri]
             (hydra/property {::hydra/id uri
                              ::hydra/range type}))
           (s/gen ::jsonld-spec/uri)))

(defn make-literal-property-gen [type required]
  (tg/fmap (fn [supported-property]
             (let [property (:property supported-property)
                   property (-> property
                                (assoc-in [:rdf-props ::hydra/range] type))
                   min-count (get-in supported-property [::property-props ::hydra/min-count])
                   max-count (get-in supported-property [::property-props ::hydra/max-count])
                   min-count (if required
                               (if (> (or min-count 0) 1) min-count 1)
                               0)
                   max-count (if required
                               (if (> (or max-count 0) min-count) max-count min-count)
                               max-count)
                   property-props (-> (get supported-property :property-props)
                                      (assoc ::hydra/required required)
                                      (assoc ::hydra/max-count max-count)
                                      (assoc ::hydra/min-count min-count)
                                      utils/clean-nils)]
               (-> supported-property
                   (assoc :property property)
                   (assoc :property-props property-props))))
           (s/gen (s/and ::hydra/SupportedProperty
                         #(= (resolve "rdf:Property") (-> % :property :uri))))))

(defn make-link-property-gen [target required]
  (tg/fmap (fn [[supported-property operation]]
             (let [operation (-> operation
                                 (assoc-in [:operation-props ::hydra/method] "GET")
                                 (assoc-in [:operation-props ::hydra/returns] target))
                   min-count (get-in supported-property [::property-props ::hydra/min-count])
                   max-count (get-in supported-property [::property-props ::hydra/max-count])
                   min-count (if required
                               (if (> (or min-count 0) 1) min-count 1)
                               0)
                   max-count (if required
                               (if (> (or max-count 0) min-count) max-count min-count)
                               max-count)
                   property-props (-> (get supported-property :property-props)
                                      (assoc ::hydra/required required)
                                      (assoc ::hydra/max-count max-count)
                                      (assoc ::hydra/min-count min-count)
                                      utils/clean-nils)]
               (-> supported-property
                   (assoc :operations [operation])
                   (assoc :property-props property-props)
                   (assoc-in [:property :rdf-props ::hydra/range] target)
                   (assoc :is-link true)
                   (assoc :is-template false))))
           (tg/tuple
            (s/gen (s/and ::hydra/SupportedProperty
                          #(= (resolve "hydra:Link") (-> % :property :uri))))
            (s/gen ::hydra/Operation))))


(defn make-properties-map-gen [max-properties]
  (tg/vector
   (tg/bind (s/gen (s/tuple #{:literal :link}
                            boolean?))
            (fn [[kind required]]
              (condp = kind
                :literal (tg/bind (s/gen ::jsonld-spec/datatype)
                                  (fn [type]
                                    (make-literal-property-gen type required)))
                :link (tg/bind (s/gen ::jsonld-spec/uri)
                               (fn [uri]
                                 (make-link-property-gen uri required))))))
   max-properties))

(defn make-class-gen [uri max-properties]
  (tg/fmap
   (fn [[title description type properties]]
     (hydra/class {::hydra/id uri
                   ::hydra/type type
                   ::hydra/title title
                   ::hydra/description description
                   ::hydra/operations []
                   ::hydra/supported-properties properties}))
   (tg/tuple
            (s/gen ::hydra/title)
            (s/gen ::hydra/description)
            (s/gen ::hydra/type)
            (make-properties-map-gen max-properties))))

(defn make-api-nested-literal []
  (tg/bind
   (tg/tuple
    (s/gen ::jsonld-spec/datatype)
    tg/boolean)
   (fn [[datatype required]]
     (tg/tuple
      (tg/return :literal)
      (make-literal-property-gen datatype required)))))

(defn make-api-nested-link [next-class-generator]
  (tg/bind
   (tg/tuple
    ;; nested class
    next-class-generator
    ;; potential datatype in case we get properties back
    (s/gen ::jsonld-spec/datatype)
    ;; link or nested literal?
    tg/boolean
    ;; required link
    tg/boolean)
   (fn [[class-map-or-uri datatype is-link required]]
     (if (string? class-map-or-uri)
       ;; end of recursion we have a link, we generate
       ;; yet another literal property
       (tg/tuple
        (tg/return :literal)
        (make-literal-property-gen datatype required))
       (let [class-uri (:uri class-map-or-uri)]
         (tg/tuple
          (tg/return :nested)
          (if is-link
            (make-link-property-gen class-uri required)
            (make-literal-property-gen class-uri required))
          (tg/return class-map-or-uri)))))))

(defn make-api-tree-gen []
  (tg/fmap
   ;; we build the API now
   (fn [{:keys [uri acc]}]
     (hydra/api {::hydra/supported-classes acc
                 ::hydra/entrypoint "/"
                 ::hydra/entrypoint-class uri}))
   ;; we generate a recursive tree of classes
   (tg/recursive-gen (fn [g]
                       ;; we collect all the classes generated and we add the
                       ;; following class for this node
                       (tg/fmap (fn [[u vs]]
                                  (let [literals (->> vs
                                                      (filter (fn [v] (= :literal (first v))))
                                                      (map (fn [[_ l]] l)))
                                        classes-links (filter (fn [v] (= :nested (first v))) vs)
                                        links (map (fn [[_ link nested-class]] link) classes-links)
                                        nested-classes (map (fn [[_ link nested-class]] nested-class) classes-links)
                                        acc (->> classes-links
                                                 (map (fn [[_ _ nested-class]]
                                                        (:acc nested-class)))
                                                 (apply concat))
                                        class (hydra/class {::hydra/id u
                                                            ::hydra/operations []
                                                            ::hydra/supported-properties (concat literals links)})]
                                    {:uri u :acc (concat acc [class])}))
                                (tg/tuple
                                 ;; class URI
                                 (s/gen ::jsonld-spec/uri)
                                 ;; supported-properties, including links to more classes
                                 (tg/vector (tg/one-of
                                             [
                                              ;; nested literal
                                              (make-api-nested-literal)
                                              ;; nested link
                                              (make-api-nested-link g)
                                              ])
                                            ;; min 1 link, max 3
                                            1 3))))
                     ;; base case is just a URI
                     (s/gen ::jsonld-spec/uri))))
