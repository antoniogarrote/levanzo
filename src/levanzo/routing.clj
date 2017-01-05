(ns levanzo.routing
  (:require [levanzo.hydra :as hydra]
            [levanzo.namespaces :as lns]
            [levanzo.spec.jsonld :as jsonld-spec]
            [clojure.string :as string]
            [clojure.spec :as s]))

;; root of the route path
(s/def ::path (s/cat :base ::jsonld-spec/path
                     :rest (s/* (s/or :var ::jsonld-spec/path-variable
                                      :path ::jsonld-spec/path))))

;; sequence of RDF properties leading to a resource
(s/def ::property-path (s/coll-of (s/cat :class-term ::jsonld-spec/uri
                                         :property-term ::jsonld-spec/uri)))

(s/def ::route-link (s/nilable ::hydra/SupportedProperty))
(s/def ::link-route (s/keys :req [::route-link ;; link the route is generated from
                                  ::hydra/Operation ;; operation for the route handler / method
                                  ::path ;; computed path for the link
                                  ::property-path ;; property path to this route
                                  ::hydra/handler ;; handler for the route
                                  ]))

(s/def ::routes (s/coll-of ::link-route
                           :gen-max 3))

(s/def ::context (s/keys :req [::path
                               ::property-path
                               ::hydra/ApiDocumentation]))


(s/fdef absolute-path?
        :args (s/coll-of ::path :min-count 1 :max-count 1)
        :ret boolean?)
(defn absolute-path?
  "Is the provided path absolute?"
  [path]
  (let [elm (first path)]
    (and (string? elm)
         (string/starts-with? elm "/"))))

(s/fdef concat-path
        :args (s/coll-of ::path :min-count 2 :max-count 2)
        :ret ::path)
(defn concat-path
  "Concatenates paths, if the next path is absolute, it is returned
   else they are concatenated"
  [root next]
  (if (absolute-path? next)
    next
    (concat root next)))

(defn handler-for-class-method
  "Returns the handler in the operation for the provided class and method inside an API
   or nil if not matching operations is found"
  [api class-id method]
  (let [supported-class (->> api
                             :supported-classes
                             (filter (fn [supported-class]
                                       (= (-> supported-class :common-props ::hydra/id)
                                          class-id)))
                             first)]
    (if (some? supported-class)
      (->> supported-class
           :operations
           (filter (fn [operation]
                     (= (-> operation :operation-props ::hydra/method)
                        method)))
           (map (fn [operation]
                  (-> operation :handle)))
           first)
      nil)))

(s/fdef property-cycle?
        :args (s/cat :class-id ::jsonld-spec/uri
                     :property-id ::jsonld-spec/uri
                     :property-path ::property-path)
        :ret boolean?)
(defn property-cycle?
  "Checks if the a property path tuple creates a cycle in the property path"
  [class-id property-id property-path]
  (->> property-path
       (filter #(= % [class-id property-id]))
       first
       some?))


(s/fdef class-links
         :args (s/cat :target-class ::hydra/SupportedClass
                      :property-path ::property-path)
         :ret (s/coll-of (s/and ::hydra/SupportedProperty
                                (s/or :link #(-> % :is-link)
                                      :template #(-> % :is-template)))))
(defn class-links
  "Find all the property link/templates for a given class that will not generate cycles in a path"
  [target-class property-path]
  (let [class-id (-> target-class :common-props ::hydra/id)]
    (->> target-class
         :supported-properties
         ;; we find the links
         (filter (fn [property] (or (:is-link property)
                                   (:is-template property))))
         ;; makes sure we will not create cycles
         (filter (fn [property] (not (property-cycle? class-id
                                                     (:property property)
                                                     property-path)))))))

(declare parse-link-routes)
(declare parse-class-routes)

(defn parse-nested-routes
  "Parses routes nested in the provided route using the information
   from the API documentation passed as an argument"
  [{:keys [:levanzo.routing/route-link
           :levanzo.hydra/Operation]}
   {:keys [:levanzo.hydra/ApiDocumentation
           :levanzo.routing/path
           :levanzo.routing/property-path] :as context}]
  (let [returns-id (or (-> Operation :operation-props ::hydra/returns)
                       (-> route-link :property-props ::hydra/range))
        target-class (hydra/find-class ApiDocumentation returns-id)]
    (if (some? target-class)
      (if (hydra/collection? target-class)
        ;; if target class is a collection, we need to use the member class and add the
        ;; additional hydra:member property to the property path
        (let [member-class-id (-> target-class :member-class)
              member-class (hydra/find-class ApiDocumentation member-class-id)]
          (if (some? member-class)
            (let [collection-routes (parse-class-routes route-link target-class context)
                  member-route (-> target-class :member-route)
                  path (concat-path path member-route)
                  property-path (concat property-path [[member-class-id (lns/resolve "hydra:member")]])
                  member-context {::path path
                                  ::property-path property-path
                                  ::hydra/ApiDocumentation ApiDocumentation}
                  member-routes (parse-class-routes (hydra/link {::hydra/id (lns/resolve "lvz:member-link")
                                                                 ::hydra/property (lns/resolve "hydra:member")
                                                                 ::hydra/route member-route})
                                                    member-class
                                                    member-context)
                  nested (->> (class-links member-class property-path)
                              ;; compute nested routes
                              (mapv (fn [property]
                                      (parse-link-routes member-class-id property member-context))))]
              ;; return a single collection of nested routes
              (concat collection-routes
                      member-routes
                      (apply concat nested)))
            []))
        ;; we found a target class, compute links avoiding cycles
        (let [class-routes (parse-class-routes route-link target-class context)
              nested-context {::path path
                              ::property-path property-path
                              ::hydra/ApiDocumentation ApiDocumentation}
              nested (->> (class-links target-class property-path)
                          ;; compute nested routes
                          (mapv (fn [property]
                                  (parse-link-routes returns-id property nested-context))))]
          ;; return a single collection of nested routes
          (concat class-routes
                  (apply concat nested))))
      ;; cannot find target class, no nested routes
      [])))

(s/fdef parse-class-routes
        :args (s/cat :link ::route-link
                     :supported-class ::hydra/SupportedClass
                     :context ::context)
        :ret ::routes)
(defn parse-class-routes
  "Returns routes for the operations attached to a class provided a route to the class and a context"
  [route-link supported-class {:keys [:levanzo.routing/path
                                      :levanzo.routing/property-path]}]
  (let [operations (-> supported-class :operations)]
    (->> operations
         (map (fn [operation]
                {::route-link route-link
                 ::hydra/Operation operation
                 ::path path
                 ::property-path property-path
                 ::hydra/handler (:handler operation)})))))

(s/fdef parse-link-routes
        :args (s/and
               ;; argument has to be a supported property
               (s/cat :class-id ::jsonld-spec/uri
                      :property ::hydra/SupportedProperty
                      :context  ::path)
               ;; it has to be a link or template
               #(or (:is-link (:property %))
                    (:is-template (:property %))))
        ;; returns the routes generated for that link
        :ret ::routes)
(defn parse-link-routes
  "Parses all routes for a provided link and their nested resources"
  [class-id link {:keys [:levanzo.routing/path
                         :levanzo.routing/property-path
                         :levanzo.hydra/ApiDocumentation]}]
  (let [property (-> link :property)
        range (-> link :property-props ::hydra/range)
        route (-> link :property-props ::hydra/route)
        operations (if (empty? (:operations link))
                     (hydra/find-class-operations ApiDocumentation range)
                     (:operations link))
        resource-path (concat-path path route)
        property-path (concat property-path [[class-id property]])
        routes-with-handlers (->> operations
                                  ;; transform operations into routes
                                  (mapv (fn [operation]
                                          {::route-link link
                                           ::hydra/Operation operation
                                           ::path resource-path
                                           ::property-path property-path
                                           ::hydra/handler (:handler operation)}))
                                  ;; operations can have nil handlers, we look for those handlers in the classes
                                  (mapv (fn [route]
                                          (let [handler (::hydra/handler route)
                                                method (-> route ::hydra/Operation :operation-props ::hydra/method)]
                                            (if (some? handler)
                                              route
                                              (assoc route ::hydra/handler (handler-for-class-method ApiDocumentation class-id method))))))
                                  ;; filter routes without handler, Should we provided a default operation not supported handler?
                                  (filter #(some? (::hydra/handler %))))]
    ;; Now we compute routes for the nested resources
    (loop [routes-acc routes-with-handlers
           new-routes routes-with-handlers]
      (let [next-route (first new-routes)
            remaining-routes (rest new-routes)]
        (cond
          ;; finished iterating
          (empty? new-routes) routes-acc
          ;; "GET operation, follow links"
          (= "GET"
             (-> next-route ::hydra/Operation
                 :operation-props ::hydra/method)) (let [next-context {::path (::path next-route)
                                                                       ::property-path (::property-path next-route)
                                                                       ::hydra/ApiDocumentation ApiDocumentation}
                                                         nested-routes (parse-nested-routes next-route next-context)]
                                                     (recur (concat routes-acc nested-routes)
                                                            remaining-routes))
          ;; Not GET operation, keep on processing
          :else (recur routes-acc
                       remaining-routes))))))

(defn- remove-duplicated-rows
  "Remove duplicated routes matching path and method"
  [routes]
  (->> routes
       (reduce (fn [acc {:keys [:levanzo.routing/path
                               :levanzo.hydra/Operation] :as route}]
                 (let [method (->> Operation :operation-props ::hydra/method)
                       route-key [method path]]
                   (assoc acc route-key route)))
               {})
       vals))

(s/fdef parse-routes
        :args (s/cat :api ::hydra/ApiDocumentation)
        :ret ::routes)
(defn parse-routes
  "Parses all routes for a given API"
  [{:keys [:api-props
           :supported-classes] :as api}]
  (let [{:keys [:levanzo.hydra/entrypoint-class
                :levanzo.hydra/entrypoint]} api-props
        entrypoint-class-id entrypoint-class
        entrypoint-class (->> supported-classes
                              (filter (fn [supported-class]
                                        (= (-> supported-class
                                               :common-props
                                               ::hydra/id)
                                           entrypoint-class)))
                              first)]
    (if (nil? entrypoint-class)
      (throw (Exception. "Cannot find entrypoint class"))
      (let [context {::path [entrypoint]
                     ::property-path []
                     ::hydra/ApiDocumentation api}
            class-routes (parse-class-routes nil  entrypoint-class context)
            routes (->> (class-links entrypoint-class [])
                        ;; compute nested routes
                        (mapv (fn [property]
                                (parse-link-routes entrypoint-class-id property context))))
            routes (concat class-routes
                           (apply concat routes))]
        (remove-duplicated-rows routes)))))
