(ns levanzo.schema-test
  (:require [clojure.test :refer :all]
            [levanzo.schema :as schema]
            [levanzo.hydra :as hydra]
            [levanzo.spec.jsonld :as jsonld-spec]
            [levanzo.namespaces :refer [xsd]]
            [levanzo.spec.utils :as spec-utils]
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

(defn make-valid-payload-gen [class api]
  (tg/fmap (fn [properties]
             (let [m (->> properties
                          (map (fn [[k v]]
                                 (if (nil? v)
                                   [k []]
                                   (if (string? v)
                                     [k [{"@id" v}]]
                                     [k [v]]))))
                          (into {}))]
               (-> m
                   (assoc "@id" "http://test.com/generated")
                   (assoc "@type" [(-> class :common-props ::hydra/id)]))))
           (tg/bind
            (tg/return (-> class :supported-properties))
            (fn [properties]
              (let [generators (->> properties
                                    (mapv (fn [{:keys [property-props property is-link]}]
                                            (let [required (-> property-props ::hydra/required)
                                                  range (-> property-props ::hydra/range)]
                                              (tg/tuple (tg/return property)
                                                        (if required
                                                          (if is-link
                                                            (s/gen ::jsonld-spec/uri)
                                                            (if (schema/xsd-uri? range)
                                                              (make-xsd-type-gen range)
                                                              (let [class (hydra/find-class api range)]
                                                                ;; Careful! this will explode with cyclic APIs
                                                                (make-valid-payload-gen range api))))
                                                          (tg/return nil)))))))]
                (apply tg/tuple generators))))))

(defn make-literal-property-gen [type required]
  (tg/fmap (fn [property]
             (-> property
                 (assoc :operations [])
                 (assoc-in [:property-props ::hydra/required] required)
                 (assoc-in [:property-props ::hydra/range] type)
                 (assoc :is-link false)
                 (assoc :is-template false)))
           (s/gen ::hydra/SupportedProperty)))


(defn make-link-property-gen [target required]
  (tg/fmap (fn [[property operation]]
             (let [operation (-> operation
                                 (assoc-in [:operation-props ::hydra/method] "GET")
                                 (assoc-in [:operation-props ::hydra/returns] target))]
               (-> property
                   (assoc :operations [operation])
                   (assoc-in [:property-props ::hydra/required] required)
                   (assoc-in [:property-props ::hydra/range] target)
                   (assoc :is-link true)
                   (assoc :is-template false))))
           (tg/tuple
            (s/gen ::hydra/SupportedProperty)
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
                                              (tg/bind
                                               (tg/tuple
                                                (s/gen ::jsonld-spec/datatype)
                                                tg/boolean)
                                               (fn [[datatype required]]
                                                 (tg/tuple
                                                  (tg/return :literal)
                                                  (make-literal-property-gen datatype required))))
                                              ;; nested link
                                              (tg/bind
                                               (tg/tuple
                                                ;; nested class
                                                g
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
                                                      (tg/return class-map-or-uri))))))
                                              ])
                                            ;; min 1 link, max 3
                                            1 3))))
                     (s/gen ::jsonld-spec/uri))))

(deftest check-xsd-range-test
  (spec-utils/check-symbol `schema/check-xsd-range)
  (is (thrown? Exception (schema/check-xsd-range "http://test.com/foo" {"@value" "test"}))))

(deftest check-range-test
  (spec-utils/check-symbol `schema/check-range))

(deftest parse-supported-link-test
  (spec-utils/check-symbol `schema/parse-supported-link))

 (deftest parse-plain-property-test
   (spec-utils/check-symbol `schema/parse-plain-property))

(deftest parse-supported-property-test
   (spec-utils/check-symbol `schema/parse-supported-property))

(deftest parse-supported-class-test
  (let [klasses (take 3 (gen/sample (make-class-gen "http://test.com/Test" 15)))]
    (doseq [klass klasses]
      (doseq [instance (gen/sample (make-valid-payload-gen klass nil) 10)]
        (let [errors ((schema/parse-supported-class {} klass) {} instance)
              valid (nil? errors)]
          (when (not valid)
            (prn instance)
            (prn errors))
          (is valid))))))

(deftest parse-supported-class-api-test
  (println "Generating APIS...")
  (doseq [api (take-last 3 (gen/sample (make-api-tree-gen) 15))]
    (println "API!")
    (s/valid? ::hydra/ApiDocumentation api)
    (let [validations-map (schema/build-api-validations api)]
      (doseq [klass (:supported-classes api)]
        (println "CLASS!")
        (doseq [instance (gen/sample (make-valid-payload-gen klass api) 10)]
          (println "INSTANCE...")
          (let [validation (get validations-map (-> klass :common-props ::hydra/id))
                errors (validation validations-map instance)
                valid (nil? errors)]
            (when (not valid)
              (prn instance)
              (prn errors))
            (is valid)))))))

(comment


  (prn (last (take 50 (gen/sample (tg/such-that not-empty (tg/vector tg/int))))))
  (prn (last (take 50 (gen/sample (make-api-tree-gen)))))
  (def gapi (last (take 50 (gen/sample (make-api-tree-gen)))))

  (clojure.pprint/pprint gapi)
  (last (take 1000 (gen/sample (tg/recursive-gen gen/vector (tg/recursive-gen gen/vector tg/int)) 10000)))
  (def klass (first (gen/sample (make-class-gen "http://test.com/Test" 15))))

  (def instance (first (gen/sample (make-valid-payload-gen klass))))

  ((schema/parse-supported-class {} klass) instance)

  (doseq []
    (prn ))


  (spec-utils/check-symbol `schema/parse-supported-property)

  (gen/sample (tg/tuple (s/gen ::hydra/ApiDocumentation)
                        (s/gen ::jsonld-spec/datatype)
                        (s/gen ::jsonld-spec/jsonld-literal)) 1)
  )
