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
   {:results (->> (mquery/with-collection db/db collection
                    (mquery/find condition)
                    (mquery/skip (* (dec page) per-page))
                    (mquery/limit per-page))
                  (map db/bson->json))
    :count (mc/count db/db collection condition)})
  ([collection pagination] (find-all collection pagination {})))

(defn filter-property [collection property]
  (fn [{:keys [predicate object pagination]}]
    (->> (pattern->predicate property object)
         (find-all collection pagination))))

(defn lookup-resource [collection]
  (fn [{:keys [subject request]}]
    (let [id (relative-url subject)]
      {:results [(db/find-one collection id)]
       :count 1})))

(defn collection-join
  ([subject collection join-condition]
   (fn [{:keys [object pagination request]}]
     (if (some? object)
       (if (= 1  (:count (find-all collection pagination (merge
                                                          join-condition
                                                          {:_id {mo/$in [(relative-url object)]}}))))
         {:results [{:subject subject
                     :object {"@id" object}}]
          :count 1}
         {:results []
          :count 0})
       (let [{:keys [results count]} (find-all collection pagination join-condition)]
         {:results (mapv (fn [obj] {:subject subject
                                   :object {"@id" (-> obj (get "_id") expand-url)}})
                         results)
          :count count}))))
  ([subject collection] (collection-join subject collection {})))

(defn raised-issues-join [{:keys [subject object pagination] :as pattern}]
  (cond
    (and (some? subject) (nil? object)) (let [user-path (string/replace (relative-url subject) "/raised_issues" "")
                                              finder (collection-join {"@id" subject} "issues" {:raised_by {:_id user-path}})]
                                          (finder pattern))
    (and (some? subject) (some? object))  (let [user-path (string/replace (relative-url subject) "/raised_issues" "")
                                                issue-id (relative-url object)
                                                finder (collection-join {"@id" subject} "issues" {:raised_by {:_id user-path}
                                                                                          :_id issue-id})]
                                            (finder pattern))
    (and (nil? subject) (some? object))  (let [issue-id (relative-url object)
                                               issue (first (db/find-one "issues" issue-id))]
                                           (if (some? issue)
                                             [{:subject {"@id" (expand-url (get-in issue ["raised_by" "_id"]))}
                                               :object {"@id" object}}]
                                             []))
    (and (nil? subject) (nil? object))  (let [{:keys [results count]} (find-all "issues" pagination)
                                              results (map (fn [issue] {:subject {"@id" (expand-url (get-in issue ["raised_by" "_id"]))}
                                                                       :object {"@id" (expand-url (get issue "_id"))}})
                                                           results)]
                                          {:results results
                                           :count count})
    ))

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
(def users-join (collection-join (payload/id {:model api/EntryPoint :base config/host})
                                 "users"))

(def issues-join (collection-join (payload/id {:model api/EntryPoint :base config/host})
                                  "issues"))


(def indices (indexing/api-index
              {api/User {:resource users-resource-lookup

                         :properties {api/name-prop {:index users-name-index}
                                      api/email-prop {:index users-email-index}}
                         :links {(api/vocab "raised-issues-link") raised-issues-join}}

               api/Issue {:resource issues-resource-lookup

                          :properties {api/is-open-prop {:index issues-is-open-index}
                                       api/raised-by-prop {:index issues-raised-by-index}}

                          :links {(api/vocab "get-users-link") users-join}}

               api/EntryPoint {:links {(api/vocab "get-issues-link") issues-join}}}))

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

  (:count (users-resource-lookup {:subject "http://localhost:8080/users/1"
                                  :request nil}))

  (issues-join {:subject nil
                :object "http://localhost:8080/issues/4"
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

  (raised-issues-join {:subject nil
                       :object nil
                       :pagination {:page 1 :per-page 40}
                       :request nil})

  (raised-issues-join {:subject "http://localhost:8080/users/1/raised_issues"
                       :object "http://localhost:8080/issues/1"
                       :pagination {:page 1 :per-page 40}
                       :request nil})

  (raised-issues-join {:subject nil
                       :object "http://localhost:8080/issues/1"
                       :pagination {:page 1 :per-page 40}
                       :request nil})

  )
