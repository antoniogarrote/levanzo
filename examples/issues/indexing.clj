(ns issues.indexing
  (:require [levanzo.indexing :as indexing]
            [levanzo.payload :as payload]
            [levanzo.routing :as routing]
            [issues.api :as api]
            [issues.db :as db]
            [issues.config :as config]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :as mo]
            [monger.conversion :as mconv]
            [monger.query :as mquery]
            [clojure.string :as string]
            [clojure.test :refer :all]))

(defn relative-url [url] (string/replace url config/host ""))

(defn expand-url [url] (str config/host url))

(defn pattern->predicate [property object]
  (if (nil? object)
    {(keyword property) {mo/$exists true}}
    (if (some? (get object "@value"))
      {(keyword property) (get object "@value")}
      {(keyword property) {:_id (relative-url (get object "@id"))}})))

(defn find-all
  ([collection {:keys [page per-page]} condition]
   (->> (mquery/with-collection db/db collection
          (mquery/find condition)
          (mquery/skip (* (dec page) per-page))
          (mquery/limit per-page))
        (map db/bson->json)))
  ([collection pagination] (find-all collection {} pagination)))

(defn filter-property [collection property]
  (fn [{:keys [predicate object pagination]}]
    (->> (pattern->predicate property object)
         (find-all collection pagination))))

(defn lookup-resource [collection]
  (fn [{:keys [subject request]}]
    (let [id (relative-url subject)]
      (db/find-one collection id))))

(defn collection-join
  ([collection join-condition]
   (fn [{:keys [object pagination request]}]
     (if (some? object)
       (let [objects (map #(relative-url %) object)]
         (find-all collection pagination (merge
                                          join-condition
                                          {:_id {mo/$in objects}})))
       (find-all collection pagination join-condition))))
  ([collection] (collection-join collection {})))

(defn raised-issues-join [{:keys [subject] :as pattern}]
  (let [user-path (string/replace (relative-url subject) "/raised_issues" "")
        finder (collection-join "issues" {:raised_by {:_id user-path}})]
    (finder pattern)))

;; indices
(def users-email-index (filter-property "users" "email"))

(def users-name-index (filter-property "users" "name"))
                                        ;
(def issues-is-open-index (filter-property "issues" "isOpen"))

(def issues-raised-by-index (filter-property "issues" "raisedBy"))

;; resource look-up
(def users-resource-lookup (lookup-resource "users"))

(def issues-resource-lookup (lookup-resource "issues"))

;; collection joins
(def users-join (collection-join "users"))

(def issues-join (collection-join "issues"))


(def indices {api/User {:resource users-resource-lookup

                        :properties {api/name-prop {:index users-name-index}
                                     api/email-prop {:index users-email-index}}
                        :join {(api/vocab "raised-issues-link") raised-issues-join}}

              api/Issue {:resource issues-resource-lookup

                         :properties {api/is-open-prop {:index issues-is-open-index}
                                      api/raised-by-prop {:index issues-raised-by-index}}

                         :join {(api/vocab "get-users-link") users-join}}

              api/EntryPoint {:join {(api/vocab "get-issues-link") issues-join}}})

(deftest indices-test

  (clojure.spec/check-asserts true)
  (require '[clojure.spec :as s])


  (indexing/api-index indices)


  (def user (issues.routes/post-user {} (-> (payload/jsonld
                                             (payload/supported-property {:property api/name-prop :value "User 2"})
                                             (payload/supported-property {:property api/email-prop :value "usen2@test.com"})
                                             (payload/supported-property {:property api/password-prop :value "test02n"}))
                                            (payload/expand))
                                     {}))


  (users-email-index {:predicate nil
                      :object {"@value" "usen2@test.com"}
                      :pagination {:page 1 :per-page 10}
                      :request nil})

  (count (users-resource-lookup {:subject "http://localhost:8080/users/1"
                                 :request nil}))

  (issues-join {:subject nil
                :object ["http://localhost:8080/issues/3"
                          "http://localhost:8080/issues/5"
                          "http://localhost:8080/issues/66"]
                :pagination {:page 1 :per-page 40}
                :request nil})

  (users-join {:subject nil
               :object nil
               :pagination {:page 1 :per-page 40}
               :request nil})

  (raised-issues-join {:subject "http://localhost:8080/users/1/raised_issues"
                       :object nil
                       :pagination {:page 1 :per-page 40}
                       :request nil})

  (raised-issues-join {:subject "http://localhost:8080/users/1/raised_issues"
                       :object ["http://localhost:8080/issues/4"]
                       :pagination {:page 1 :per-page 40}
                       :request nil})

  )
