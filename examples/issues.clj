(ns examples.issues
  (:require [levanzo.hydra :as hydra]
            [levanzo.routing :as routing]
            [levanzo.namespaces :refer [xsd]]
            [clojure.spec.test :as stest]
            [bidi.bidi :as bidi]))

(def vocab (fn [x] (str "/vocab#" x)))

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


(def get-issues-for-user (fn [_ _ _] :get-issues-for-user))
(def post-user (fn [_ _ _] :post-user))
(def get-users (fn [_ _ _] :get-users))
(def get-user (fn [_ _ _] :get-user))
(def put-user (fn [_ _ _] :put-user))
(def delete-user (fn [_ _ _] :delete-user))
(def get-user-for-issue (fn [_ _ _] :get-user-for-issue))
(def post-issue (fn [_ _ _] :post-issue))
(def get-issues (fn [_ _ _] :get-issues))
(def get-issue (fn [_ _ _] :get-issue))
(def put-issue (fn [_ _ _] :put-issue))
(def delete-issue (fn [_ _ _] :delete-issue))
(def get-entrypoint (fn [_ _ _] :get-entrypoint))



(def routes (routing/process-routes {:path ["/"]
                                     :model EntryPoint
                                     :handlers {:get get-entrypoint}
                                     :nested [{:path ["issues"]
                                               :model (vocab "get-issues-link")
                                               :handlers {:get get-issues
                                                          :post post-issue}
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
                                                                   :handlers {:get get-issues-for-user}}]}]}
                                              {:path ["register-users"]
                                               :model (vocab "register-users-link")
                                               :handlers {:post post-user}}]}))
