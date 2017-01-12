(ns levanzo.routing
  (:require [levanzo.hydra :as hydra]
            [levanzo.namespaces :as lns]
            [levanzo.spec.jsonld :as jsonld-spec]
            [clojure.string :as string]
            [clojure.spec :as s]
            [bidi.bidi :as bidi]
            [bidi.verbose :refer [branch param leaf]]
            [clojure.test.check.generators :as tg]))

(s/def ::path (s/or
               :simple-path ::jsonld-spec/path
               :complex-path (s/coll-of (s/or :path ::jsonld-spec/path
                                              :var  keyword?))))


(s/def ::model (s/or :class ::hydra/SupportedClass
                     :collection ::hydra/Collection
                     :id ::hydra/id))

(s/def ::handlers (s/map-of #{:get :put :post :delete :patch}
                            ::hydra/handler))

(s/def ::nested (s/with-gen (s/coll-of ::route-input :gen-max 0)
                  #(tg/return [])))

(s/def ::route-input (s/with-gen (s/keys :req-un [::path
                                                  ::model
                                                  ::handlers]
                                         :opt-un [::nested])
                       #(tg/recursive-gen
                         (fn [g]
                           (tg/fmap (fn [[els nested]]
                                      ;; we build the nest route or the top level route
                                      (let [[path model handlers _] els]
                                        {:path path
                                         :model model
                                         :handlers handlers
                                         :nested nested}))
                                    (tg/tuple
                                     ;; the potential top level route
                                     (tg/tuple
                                      (s/gen ::path)
                                      (s/gen ::hydra/id)
                                      (tg/return {:get (fn [r h b] b)})
                                      (tg/return []))
                                     ;; the list of nested routes
                                     (tg/bind
                                      (tg/vector g 1 2)
                                      (fn [recurred]
                                        (tg/return (mapv (fn [el]
                                                           (if (map? el)
                                                             el
                                                             (let [[path model handlers nested] el] {:path path
                                                                                                     :model model
                                                                                                     :handlers handlers
                                                                                                     :nested nested})))
                                                         recurred)))))))
                         ;; recursion base case, empty nested routes
                         (tg/tuple
                          (s/gen ::path)
                          (s/gen ::hydra/id)
                          (tg/return {:get (fn [r h b] b)})
                          (tg/return [])))))

(def ^:dynamic *routes-register* (atom {}))
(def ^:dynamic *routes* (atom []))

(defn model-or-uri [model]
  (if (string? model)
    (keyword model)
    (-> model :common-props ::hydra/id keyword)))

(defn process-routes* [routes]
  (mapv (fn [route]
          (let [{:keys [path model nested]} route
                model (model-or-uri model)]
            (swap! *routes-register* (fn [acc] (assoc acc model route)))
            (if (or (nil? nested) (empty? nested))
              (let [leafed (leaf path model)]
                leafed)
              (let [this-leaf (leaf "" model)
                    branched (process-routes* nested)
                    args (concat [path this-leaf] branched)]
                (apply branch args)))))
        routes))

(s/def ::bidi-leaf-route (s/tuple (s/or :empty-path #(= % "")
                                        :path ::path) keyword))
(s/def ::bidi-branch-route (s/tuple ::path (s/coll-of (s/or :leaf ::bidi-leaf-route
                                                            :branch ::bidi-branch-route))))
(s/fdef process-routes
        :args (s/cat :routes ::route-input)
        :ret ::bidi-branch-route)
(defn process-routes
  "Process the routes for an API populating the routes register and building a new set of bidi routes"
  [routes]
  (s/assert ::route-input routes)
  (let [routes (first (process-routes* [routes]))]
    (reset! *routes* routes)))


(s/fdef link-for
        :args (s/cat :model ::hydra/id
                     :link-args (s/coll-of (s/or :keys keyword?
                                                 :val any?)))
        :ret (s/or :id ::hydra/id
                   :path ::jsonld-spec/path))
(defn link-for
  "Mints a link for a provided set of routes, model URI and args "
  [model & args]
  (let [args (concat [@*routes* (keyword (model-or-uri model))] args)]
    (if (some? (get @*routes-register* (keyword (model-or-uri model))))
      (try (apply bidi/path-for args)
           (catch Exception ex
             (throw (Exception. (str "Error generating link for " (model-or-uri model)) ex))))
      (throw (throw (Exception. (str "Unknown model URI " (model-or-uri model))))))))


(defn clear!
  "Cleans the routes information"
  []
  (reset! *routes-register* {})
  (reset! *routes* []))
