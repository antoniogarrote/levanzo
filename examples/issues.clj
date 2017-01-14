(ns examples.issues
  (:require [levanzo.hydra :as hydra]
            [levanzo.routing :as routing]
            [levanzo.payload :as payload]
            [levanzo.namespaces :refer [xsd hydra]]
            [levanzo.jsonld :as jsonld]
            [clojure.spec.test :as stest]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :as mo]
            [monger.conversion :as mconv]
            [bidi.bidi :as bidi]))

(def host "http://localhost/")
(def vocab (fn [x] (str host "vocab#" x)))

(def name (hydra/property {::hydra/id (vocab "name")
                           ::hydra/title "name"
                           ::hydra/description "The user's full name"
                           ::hydra/range (xsd "string")}))

(def email (hydra/property {::hydra/id (vocab "email")
                            ::hydra/title "email"
                            ::hydra/description "The user's emailaddress"
                            ::hydra/range (xsd "string")}))

(def password (hydra/property {::hydra/id (vocab "password")
                               ::hydra/title "password"
                               ::hydra/description "The user's password"
                               ::hydra/range (xsd "string")}))

(def raised-issues (hydra/link {::hydra/id (vocab "raised_issues")
                                ::hydra/title "raised_issues"
                                ::hydra/description "The issues raised by this user"
                                ::hydra/range (vocab "IssuesCollection")}))

(def User (hydra/class {::hydra/id (vocab "User")
                        ::hydra/title (vocab "User")
                        ::hydra/description "A User represents a person registered in the system."
                        ::hydra/supported-properties [(hydra/supported-property {::hydra/property name})
                                                      (hydra/supported-property {::hydra/property email})
                                                      (hydra/supported-property {::hydra/property password
                                                                                 ::hydra/writeonly true})
                                                      (hydra/supported-property {::hydra/id (vocab "raised-issues-link")
                                                                                 ::hydra/property raised-issues
                                                                                 ::hydra/readonly true
                                                                                 ::hydra/operations
                                                                                 [(hydra/get-operation
                                                                                   {::hydra/returns (vocab "IssuesCollection")
                                                                                    ::hydra/description "Retrieves the issues raised by a User entity"})]})]
                        ::hydra/operations [(hydra/get-operation {::hydra/returns (vocab "User")
                                                                  ::hydra/id (vocab "user_retrieve")
                                                                  ::hydra/description "Retrieves a User entity"})
                                            (hydra/put-operation {::hydra/returns (vocab "User")
                                                                  ::hydra/expects (vocab "User")
                                                                  ::hydra/id (vocab "user_replace")
                                                                  ::hydra/description "Replaces an existing User entity"})
                                            (hydra/delete-operation {::hydra/id (vocab "user_delete")
                                                                     ::hydra/description "Deletes a User entity"})]}))

(def title (hydra/property {::hydra/id (vocab "title")
                            ::hydra/title "title"
                            ::hydra/description "The issue's title"
                            ::hydra/range (xsd "string")}))

(def description (hydra/property {::hydra/id (vocab "description")
                                  ::hydra/title "description"
                                  ::hydra/description "A description of the issue"
                                  ::hydra/range (xsd "string")}))

(def created-at (hydra/property {::hydra/id (vocab "createdAt")
                                 ::hydra/title "created_at"
                                 ::hydra/description "The date and time this issue was created"
                                 ::hydra/range (xsd "dateTime")}))

(def is-open (hydra/property {::hydra/id (vocab "isOpen")
                              ::hydra/title "is_open"
                              ::hydra/description "Is the issue open?\nUse for 1 yes, 0 for no when modifying this value."
                              ::hydra/range (xsd "boolean")}))

(def raised-by (hydra/link {::hydra/id (vocab "raised_by")
                            ::hydra/property (vocab "raisedBy")
                            ::hydra/title "raised_by"
                            ::hydra/description "The user who raised the issue"}))

(def Issue (hydra/class {::hydra/id (vocab "Issue")
                         ::hydra/title (vocab "Issue")
                         ::hydra/description "An Issue tracked by the system."
                         ::hydra/supported-properties [(hydra/supported-property {::hydra/property title})
                                                       (hydra/supported-property {::hydra/property description})
                                                       (hydra/supported-property {::hydra/property created-at})
                                                       (hydra/supported-property {::hydra/property is-open
                                                                                  ::hydra/readonly true})
                                                       (hydra/supported-property {::hydra/id (vocab "raised-by-link")
                                                                                  ::hydra/property raised-by
                                                                                  ::hydra/readonly true
                                                                                  ::hydra/operations
                                                                                  [(hydra/get-operation {::hydra/returns (vocab "User")
                                                                                                         ::hydra/description "Retrieves a User entity"})]})]
                         ::hydra/operations [(hydra/get-operation {::hydra/returns (vocab "Issue")
                                                                   ::hydra/id (vocab "issue_retrieve")
                                                                   ::hydra/description "Retrieves a Issue entity"})
                                             (hydra/put-operation {::hydra/returns (vocab "Issue")
                                                                   ::hydra/expects (vocab "Issue")
                                                                   ::hydra/id (vocab "issue_replace")
                                                                   ::hydra/description "Replaces an existing Issue entity"})
                                             (hydra/delete-operation {::hydra/id (vocab "issue_delete")
                                                                      ::hydra/description "Deletes a Issue entity"})]}))

(def IssuesCollection (hydra/collection {::hydra/id (vocab "IssuesCollection")
                                         ::hydra/title (vocab "IssuesCollection")
                                         ::hydra/description "The collection of all issues"
                                         ::hydra/is-paginated false
                                         ::hydra/member-class (vocab "Issue")}))

(def UsersCollection (hydra/collection {::hydra/id (vocab "UsersCollection")
                                        ::hydra/title (vocab "UsersCollection")
                                        ::hydra/description "The collection of all users"
                                        ::hydra/is-paginated false
                                        ::hydra/member-class (vocab "User")}))


(def issues (hydra/link {::hydra/id (vocab "issues")
                         ::hydra/title "issues"
                         ::hydra/description "Returns all the issues"
                         ::hydra/range (vocab "IssuesCollection")}))

(def users (hydra/link {::hydra/id (vocab "users")
                        ::hydra/title "users"
                        ::hydra/description "Returns all the issues"
                        ::hydra/range (vocab "UsersCollection")}))

(def register-user (hydra/link {::hydra/id (vocab "registerUser")
                                ::hydra/title "register_user"
                                ::hydra/description "Creates a new User entity"
                                ::hydra/range (vocab "User")}))

(def EntryPoint (hydra/class {::hydra/id (vocab "EntryPoint")
                              ::hydra/title "EntryPoint"
                              ::hydra/description "The main entry point of the API"
                              ::hydra/supported-properties [(hydra/supported-property {::hydra/id (vocab "get-issues-link")
                                                                                       ::hydra/property issues
                                                                                       ::hydra/operations
                                                                                       [(hydra/post-operation {::hydra/expects (vocab "Issue")
                                                                                                               ::hydra/returns (vocab "Issue")})
                                                                                        (hydra/get-operation {::hydra/returns (vocab "IssuesCollection")})]})
                                                            (hydra/supported-property {::hydra/id (vocab "get-users-link")
                                                                                       ::hydra/property users
                                                                                       ::hydra/operations
                                                                                       [(hydra/get-operation {::hydra/returns (vocab "UsersCollection")})]})
                                                            (hydra/supported-property {::hydra/id (vocab "post-users-link")
                                                                                       ::hydra/property register-user
                                                                                       ::hydra/operations
                                                                                       [(hydra/post-operation {::hydra/expects (vocab "User")
                                                                                                               ::hydra/returns (vocab "User")})]})]
                              ::hydra/operations [(hydra/get-operation {::hydra/returns (vocab "EntryPoint")
                                                                        ::hydra/description "The APIs main entry point."})]}))

(def API (hydra/api {::hydra/entrypoint "/"
                     ::hydra/entrypoint-class (vocab "EntryPoint")
                     ::hydra/supported-classes [EntryPoint
                                                IssuesCollection
                                                Issue
                                                User
                                                UsersCollection]}))

(def mongo (mg/connect))
(def db (mg/get-db mongo "issues"))

(def context (payload/context {:base host
                               :vocab (vocab "")
                               :ns ["hydra"]}))


(defn next-counter [collection]
  (:ids (mc/find-and-modify db "counters" {:kind collection}
                            {mo/$inc {:ids 1}}
                            {:return-new true
                             :upsert true})))

(defn bson->json [obj]
  (-> obj
      (mconv/from-db-object false)
      (assoc "@context" context)
      (dissoc "_id")))

(defn json->bson [json]
  (let [compacted (payload/compact json)]
    (-> compacted
        (dissoc "@context")
        (assoc :_id (get compacted "@id")))))

(def post-user (fn [args body request]
                 (let [next-id (next-counter "users")
                       user (payload/merge body (payload/json-ld
                                                (payload/id User {:user-id next-id})
                                                (payload/type User)
                                                (payload/supported-link (vocab "raised-issues-link") {:user-id next-id})))
                       bson (json->bson user)]
                   (mc/save db "users" bson)
                   user)))

(def get-users (fn [args body request]
                 (let [users (->> (mc/find-seq db "users")
                                  (map bson->json))]
                   (-> (payload/json-ld (payload/id (vocab "get-users-link"))
                                        (payload/type UsersCollection)
                                        (payload/title "Users Collection")
                                        (payload/members users)
                                        (payload/total-items (count users)))
                       (jsonld/compact-json-ld context)))))

(def get-user (fn [args body request]
                (->> (payload/link-for User args)
                     (mc/find-by-id db "users")
                     bson->json)))

(def put-user (fn [args body request]
                (let [id (payload/link-for User args)
                      user (->> (mc/find-by-id db "users" id)
                                bson->json)]
                  (->> (payload/merge user body)
                       json->bson
                       (mc/update db "users" {:_id id})))))

(def get-issues-for-user (fn [args body request]
                           (let [link (-> (vocab "raised-by-link")
                                          (payload/supported-link  User args)
                                          (payload/compact {:context false}))
                                 issues (->> (mc/find db "issues" link)
                                             (map bson->json)
                                             (map payload/expand))]
                             (-> (payload/json-ld
                                  (payload/id (vocab "raised-issues-link") args)
                                  (payload/type IssuesCollection)
                                  (payload/title "Issues Collection")
                                  (payload/members issues)
                                  (payload/total-items (count issues)))
                                 bson->json
                                 (jsonld/compact-json-ld context)))))

(def delete-user (fn [args body request]
                   (mc/remove-by-id db "users"
                                    (payload/link-for User args))))

(def get-user-for-issue (fn [{:keys [user-id]} body request]
                          (get-user {:user-id user-id} body request)))

(def post-issue-for-user (fn [args body request]
                           (let [next-id (next-counter "issues")
                                 issue (-> body
                                           (payload/merge
                                            (payload/json-ld
                                             (payload/id Issue {:issue-id next-id})
                                             (payload/type Issue)
                                             (payload/supported-property is-open true)
                                             (payload/supported-link (vocab "raised-by-link") User args)))
                                           (payload/compact))]
                             (mc/save db "issues" (json->bson issue))
                             issue)))

(def get-issues (fn [args body request]
                  (let [issues (->> (mc/find-seq db "issues")
                                    (map bson->json))]
                    (-> (payload/json-ld (payload/id (vocab "get-issues-link"))
                                         (payload/type IssuesCollection)
                                         (payload/title "Issues Collection")
                                         (payload/members issues)
                                         (payload/total-items (count issues)))
                        (payload/compact)))))


(def get-issue (fn [args body request]
                 (->> (payload/link-for Issue args)
                      (mc/find-by-id "issues")
                      bson->json)))

(def put-issue (fn [args body request]
                 (let [issue (->> (payload/link-for Issue args)
                                  (mc/find-by-id "issues")
                                  bson->json)]
                   (-> body
                       (payload/merge issue)
                       json->bson
                       (mc/update db "issues")))))

(def delete-issue (fn [args body request]
                    (mc/remove-by-id db "issues"
                                     (payload/link-for Issue args))))

(def get-entrypoint (fn [args body request]
                      (-> (payload/json-ld
                           (payload/id EntryPoint)
                           (payload/type EntryPoint)
                           (payload/supported-property "hydra:title" "Issues API Entry Point")
                           (payload/supported-link (vocab "get-issues-link"))
                           (payload/supported-link (vocab "get-users-link"))
                           (payload/supported-link (vocab "register-users-link")))
                          (payload/compact))))

(levanzo.routing/clear!)
(def routes (routing/process-routes {:path [""]
                                     :model EntryPoint
                                     :handlers {:get get-entrypoint}
                                     :nested [{:path ["issues"]
                                               :model (vocab "get-issues-link")
                                               :handlers {:get get-issues}
                                               :nested [{:path ["/" :issue-id]
                                                         :model Issue
                                                         :handlers {:get get-issue
                                                                    :put put-issue
                                                                    :delete delete-issue}
                                                         :nested [{:path ["/users/" :user-id]
                                                                   :model (vocab "raised-by-link")
                                                                   :handlers {:get get-user-for-issue}}]}]}
                                              {:path ["users"]
                                               :model (vocab "get-users-link")
                                               :handlers {:get get-users}
                                               :nested [{:path ["/" :user-id]
                                                         :model User
                                                         :handlers {:get get-user
                                                                    :put put-user
                                                                    :delete delete-user}
                                                         :nested [{:path "/raised_issues"
                                                                   :model (vocab "raised-issues-link")
                                                                   :handlers {:get get-issues-for-user
                                                                              :post post-issue-for-user}}]}]}
                                              {:path ["register-users"]
                                               :model (vocab "register-users-link")
                                               :handlers {:post post-user}}]}))


(comment

  (clojure.spec/check-asserts true)

  (do (mc/remove db "issues" {})
      (mc/remove db "users" {})
      (mc/remove db "counters" {}))

  (get-entrypoint {} nil nil)
  (get-users {} nil nil)
  (get-user {:user-id 1} nil nil)

  (def user (post-user {} (payload/json-ld
                           (payload/supported-property name "User 1")
                           (payload/supported-property email "user1@test.com")
                           (payload/supported-property password "test001"))
                       {}))

  (def update-result (put-user {:user-id 1} (payload/json-ld
                                             (payload/supported-property email "user1@test2.com"))
                               {}))

  (delete-user {:user-id 1} nil nil)

  (get-issues-for-user {:user-id 1} nil nil)
  (def issue (post-issue-for-user {:user-id 1}
                                  (payload/json-ld
                                   (payload/supported-property title "Test Issue")
                                   (payload/supported-property description "This is just a test issue")
                                   (payload/supported-property created-at (str (java.time.LocalDateTime/now))))
                                  {}))
  (get-issues {} nil nil)




  (put-user {:user-id 14} (assoc user (vocab "name") "Updated") nil)

  (get-user {:user-id 1} {} {})

  (delete-user {:user-id 10} {} nil))
