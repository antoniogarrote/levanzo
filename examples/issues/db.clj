(ns issues.db
  (:require [issues.config :as config]
            [issues.api :as api]
            [levanzo.payload :as payload]
            [levanzo.namespaces :refer [xsd hydra]]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :as mo]
            [monger.conversion :as mconv]
            [monger.query :as mquery]))

(def mongo (mg/connect))

(def db (mg/get-db mongo "issues"))

(def page-size 5)

(def context (payload/context {:base config/host
                               :vocab (api/vocab "")
                               :ns ["hydra" "rdfs"]
                               "createdAt" {"@type" (xsd "dateTime")}
                               "_id" "@id"}))


(defn next-counter [collection]
  (:ids (mc/find-and-modify db "counters" {:kind collection}
                            {mo/$inc {:ids 1}}
                            {:return-new true
                             :upsert true})))

(defn bson->json [obj]
  (let [res (-> obj
                (mconv/from-db-object false)
                (clojure.walk/stringify-keys))]
    (if (some? res)
      (assoc res "@context" context)
      res)))

(defn json->bson [json]
  (let [compacted (payload/compact json {:context false})]
    (-> compacted
        (clojure.walk/keywordize-keys))))

(defn find-all
  ([collection condition page]
   (->> (mquery/with-collection db collection
          (mquery/find condition)
          (mquery/skip (* (dec page) page-size))
          (mquery/limit page-size))
        (map bson->json)))
  ([collection page] (find-all collection {} page)))

(defn find-one [collection id]
  (->> id
       (mc/find-by-id db collection)
       bson->json))

(defn save [collection f]
  (let [next-id (next-counter collection)
        json-ld (f next-id)]
    (mc/save db collection (json->bson json-ld))
    json-ld))

(defn update [collection json-ld]
  (-> json-ld
      json->bson
      (mc/update db collection))
  json-ld)

(defn delete [collection id]
  (mc/remove-by-id db collection id))
