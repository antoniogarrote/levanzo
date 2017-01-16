(ns levanzo.http
  (:require [levanzo.payload :as payload]
            [levanzo.routing :as routing]
            [levanzo.schema :as schema]
            [levanzo.hydra :as hydra]
            [levanzo.namespaces :as lns]
            [clojure.string :as string]
            [cheshire.core :as json]
            [taoensso.timbre :as log]
            [cemerick.url :as url])
  (:import [java.io StringWriter PrintWriter]))

;; Should we return information about the stack trace of an error?
(def *debug-errors* (atom false))
(defn set-debug-errors! [enabled] (swap! *debug-errors* (fn [_] enabled)))

(def *compact-responses* (atom true))
(defn set-compact-responses! [enabled] (swap! *compact-responses* (fn [_] enabled)))

(def *validate-responses* (atom true))
(defn set-validate-responses! [enabled] (swap! *validate-responses* (fn [_] enabled)))

(def *validate-requests* (atom true))
(defn set-validate-requests! [enabled] (swap! *validate-requests* (fn [_] enabled)))

(defn ->404 [message]
  {:status 404
   :body   (json/generate-string {"@context" (lns/hydra "")
                                  "title" "404 Not Found"
                                  "description" message})})

(defn ->405 [message]
  {:status 405
   :body   (json/generate-string {"@context" (lns/hydra "")
                                  "title" "405 Method Not Allowed"
                                  "description" message})})

(defn ->422
  ([message errors]
   (if @*debug-errors*
     {:status 422
      :body (json/generate-string {"@context" (lns/hydra "")
                                   "title" "422 Unprocessable Entity"
                                   "description" (str errors)})}
     {:status 422
      :body (json/generate-string {"@context" (lns/hydra "")
                                   "title" "422 Unprocessable Entity"
                                   "description" message})}))
  ([message] (->422 message nil)))
(defn exception->string [ex]
  (let [sw (StringWriter.)
        pw (PrintWriter. sw)]
    (.printStackTrace ex pw)
    (.toString sw)))

(defn ->500 [ex]
  (if @*debug-errors*
    {:status 500
     :body   (json/generate-string {"@context" (lns/hydra "")
                                    "title" (str "500 " (.getMessage ex))
                                    "description" (exception->string ex)})}
    {:status 500
     :body   (json/generate-string {"@context" (lns/hydra "")
                                    "title" "500 Internal Server Error"})}))

(defn request->jsonld [{:keys [body]}]
  (if (some? body)
    (let [json (if (map? body)
                 body
                 (json/parse-stream body))]
      (payload/expand json))
    nil))

(defn response->jsonld [{:keys [body headers] :as response-map} {:keys [documentation-path base-url]}]
  (if (some? body)
    (let [body (if @*compact-responses*
                 (payload/compact body)
                 body)
          body (json/generate-string body)
          headers (merge headers
                         {"Content-Type" "application/ld+json"
                          "Link" (str "<" base-url documentation-path ">; rel=\"http://www.w3.org/ns/hydra/core#apiDocumentation\"")})
          headers (if (some? (get body "@id")) (assoc headers "Location" (get body "@id")) headers)]
      (-> response-map
          (assoc :body body)
          (assoc :headers headers)))
    response-map))

(defn validate-response [{:keys [body] :as response} validations-map mode]
  (if (and @*validate-responses* (not (nil? mode)))
    (let [types (get body "@type")
          predicates (->> types
                          (map #(get validations-map % []))
                          flatten)
          errors (->> predicates
                      (map (fn [predicate] (predicate mode validations-map body)))
                      (filter some?))]
      (if (empty? errors)
        response
        (->500 (Exception. (str "Invalid response payload " errors)))))
    response))

(defn validate-request [{:keys [body] :as request} validations-map mode continuation]
  (let [body (request->jsonld request)]
    (if (and @*validate-requests* (some? body))
      (let [types (get body "@type")
            predicates (->> types
                            (map #(get validations-map % []))
                            flatten)
            errors (->> predicates
                        (map (fn [predicate] (predicate mode validations-map body)))
                        (filter some?))]
        (if (empty? errors)
          (continuation (assoc request :body body))
          (->500 (Exception. (str "Invalid response payload " errors)))))
      (continuation (assoc request :body body)))))

(defn process-response [response mode {:keys [validations-map] :as context}]
  (let [response-map (if (or (some? (:body response))
                             (some? (:status response)))
                       response
                       {:body response})
        status (:status response-map 200)]
    (-> response-map
        (assoc :status (:status response-map 200))
        (assoc :headers (:headers response-map {}))
        (validate-response validations-map mode)
        (response->jsonld context)
        (assoc :status status))))

(defn get-handler [request route-params handler context]
  (try
    (let [response (handler route-params
                            nil
                            request)]
      (process-response response :read context))
    (catch Exception ex
      (->500 ex))))

(defn post-handler [request route-params handler {:keys [validations-map] :as context}]
  (try
    (validate-request request validations-map :write
                      (fn [{:keys [body] :as request}]
                        (let [response (handler route-params
                                                body
                                                request)
                              response (process-response response :read context)
                              response (if (= 200 (:status response))
                                         (assoc response :status 201)
                                         response)]
                          response)))
    (catch Exception ex
      (->500 ex))))

(defn put-handler [request route-params handler {:keys [api validations-map] :as context}]
  (try
    (validate-request request validations-map :update
                      (fn [{:keys [body] :as request}]
                        (let [response (handler route-params
                                                body
                                                request)]
                          (process-response response :read context))))
    (catch Exception ex
      (->500 ex))))

(defn patch-handler [request route-params handler {:keys [api validations-map] :as context}]
  (try
    (validate-request request validations-map :update
                      (fn [{:keys [body] :as request}]
                        (let [response (handler route-params
                                                body
                                                request)]
                          (process-response response :read context))))
    (catch Exception ex
      (->500 ex))))

(defn delete-handler [request route-params handler context]
  (try
    (let [response (handler route-params
                            nil
                            request)]
      (process-response response nil context))
    (catch Exception ex
      (->500 ex))))

(defn head-handler [request route-params handler context]
  (-> (get-handler request route-params handler context)
      (assoc :body nil)))

(defn documentation-handler [api]
  (log/debug "API Documentation request")
  (response->jsonld
   {:status 200
    :headers {"Content-Type" "application/ld+json"}
    :body (assoc (hydra/->jsonld api) "@context" (payload/context))}
   api))

(defn base-url [{:keys [server-port scheme server-name]}]
  (str (name scheme) "://"
       server-name
       (if (not= 80 server-port) (str ":" server-port) "")))

(defn params-map [query-string]
  (-> (if (some? query-string) (url/query->map query-string) {})
      (clojure.walk/keywordize-keys)))

(defn valid-params? [route-params params]
  (->> params
       (map (fn [[var-name {:keys [required] :or {required false}}]]
              (or (not required) (some? (get route-params var-name)))))
       (reduce (fn [acc next-value] (and acc next-value)))))

(defn middleware [{:keys [entrypoint-path api routes documentation-path] :as context}]
  (let [routes (routing/process-routes routes)
        validations (schema/build-api-validations api)
        context (merge context {:routes routes :validations validations})]

    (fn [{:keys [uri body request-method query-string] :as request}]
      (log/debug (str "Processing request " request-method " :: " uri))
      (log/debug query-string)
      (log/debug body)
      (let [request-params  (params-map query-string)
            context (assoc context :request-params request-params)]
        (if (= uri documentation-path)
          (documentation-handler api)
          (let [route (string/replace-first uri entrypoint-path "")
                handler-info (routing/match route)]
            (if (some? handler-info)
              (let [{:keys [handlers route-params params model]
                     :or {route-params {} params {}}} handler-info
                    context (-> context
                                (assoc :base-url (base-url request))
                                (assoc :model (routing/find-model model api)))
                    route-params (merge route-params request-params)
                    handler (get handlers request-method)]
                (cond
                  (nil? handler)                            (->405 (str "Method " request-method " not supported"))
                  (not (valid-params? route-params params)) (->422 "Invalid request parameters")
                  :else (condp = request-method
                          :get    (get-handler request route-params handler context)
                          :head   (head-handler request route-params handler context)
                          :post   (post-handler request route-params handler context)
                          :put    (put-handler request route-params handler context)
                          :patch  (patch-handler request route-params handler context)
                          :delete (delete-handler request route-params handler context)
                          :else (->405 (str "Method " request-method " not supported")))))
              (->404 "Cannot find the requested resource"))))))))
