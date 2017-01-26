(ns issues.api
  (:require [issues.config :as config]
            [levanzo.hydra :as hydra]
            [levanzo.namespaces :refer [xsd hydra]]))

;; URI minter function
(def vocab (fn [x] (str config/host "vocab#" x)))


;; User Properties
(def name-prop (hydra/property {::hydra/id (vocab "name")
                                ::hydra/title "name"
                                ::hydra/description "The user's full name"
                                ::hydra/range (xsd "string")}))

(def email-prop (hydra/property {::hydra/id (vocab "email")
                                 ::hydra/title "email"
                                 ::hydra/description "The user's emailaddress"
                                 ::hydra/range (xsd "string")}))

(def password-prop (hydra/property {::hydra/id (vocab "password")
                                    ::hydra/title "password"
                                    ::hydra/description "The user's password"
                                    ::hydra/range (xsd "string")}))

(def raised-issues-prop (hydra/link {::hydra/id (vocab "raised_issues")
                                     ::hydra/title "raised_issues"
                                     ::hydra/description "The issues raised by this user"
                                     ::hydra/range (vocab "IssuesCollection")}))

;; User class
(def User (hydra/class {::hydra/id (vocab "User")
                        ::hydra/title "User"
                        ::hydra/description "A User represents a person registered in the system."
                        ::hydra/supported-properties [(hydra/supported-property {::hydra/property name-prop})
                                                      (hydra/supported-property {::hydra/property email-prop
                                                                                 ::hydra/required true})
                                                      (hydra/supported-property {::hydra/property password-prop
                                                                                 ::hydra/writeonly true
                                                                                 ::hydra/required true})
                                                      (hydra/supported-property {::hydra/id (vocab "raised-issues-link")
                                                                                 ::hydra/property raised-issues-prop
                                                                                 ::hydra/readonly true
                                                                                 ::hydra/operations
                                                                                 [(hydra/get-operation
                                                                                   {::hydra/returns (vocab "IssuesCollection")
                                                                                    ::hydra/description "Retrieves the issues raised by a User entity"})
                                                                                  (hydra/post-operation {::hydra/expects (vocab "Issue")
                                                                                                         ::hydra/returns (vocab "Issue")
                                                                                                         ::hydra/description "Creates a new issue for a user"})]})]
                        ::hydra/operations [(hydra/get-operation {::hydra/returns (vocab "User")
                                                                  ::hydra/id (vocab "user_retrieve")
                                                                  ::hydra/description "Retrieves a User entity"})
                                            (hydra/put-operation {::hydra/returns (vocab "User")
                                                                  ::hydra/expects (vocab "User")
                                                                  ::hydra/id (vocab "user_replace")
                                                                  ::hydra/description "Replaces an existing User entity"})
                                            (hydra/delete-operation {::hydra/id (vocab "user_delete")
                                                                     ::hydra/description "Deletes a User entity"})]}))

;; Issue properties
(def title-prop (hydra/property {::hydra/id (vocab "title")
                                 ::hydra/title "title"
                                 ::hydra/description "The issue's title"
                                 ::hydra/range (xsd "string")}))

(def description-prop (hydra/property {::hydra/id (vocab "description")
                                       ::hydra/title "description"
                                       ::hydra/description "A description of the issue"
                                       ::hydra/range (xsd "string")}))

(def created-at-prop (hydra/property {::hydra/id (vocab "createdAt")
                                      ::hydra/title "created_at"
                                      ::hydra/description "The date and time this issue was created"
                                      ::hydra/range (xsd "dateTime")}))

(def is-open-prop (hydra/property {::hydra/id (vocab "isOpen")
                                   ::hydra/title "is_open"
                                   ::hydra/description "Is the issue open?\nUse for 1 yes, 0 for no when modifying this value."
                                   ::hydra/range (xsd "boolean")}))

(def raised-by-prop (hydra/link {::hydra/id (vocab "raised_by")
                                 ::hydra/property (vocab "raisedBy")
                                 ::hydra/title "raised_by"
                                 ::hydra/description "The user who raised the issue"}))

;; Issue class
(def Issue (hydra/class {::hydra/id (vocab "Issue")
                         ::hydra/title "Issue"
                         ::hydra/description "An Issue tracked by the system."
                         ::hydra/supported-properties [(hydra/supported-property {::hydra/property title-prop})
                                                       (hydra/supported-property {::hydra/property description-prop})
                                                       (hydra/supported-property {::hydra/property created-at-prop})
                                                       (hydra/supported-property {::hydra/property is-open-prop
                                                                                  ::hydra/readonly true})
                                                       (hydra/supported-property {::hydra/id (vocab "raised-by-link")
                                                                                  ::hydra/property raised-by-prop
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

;; Collections
(def IssuesCollection (hydra/collection {::hydra/id (vocab "IssuesCollection")
                                         ::hydra/title "IssuesCollection"
                                         ::hydra/description "The collection of all issues"
                                         ::hydra/is-paginated false
                                         ::hydra/member-class (vocab "Issue")}))

(def UsersCollection (hydra/collection {::hydra/id (vocab "UsersCollection")
                                        ::hydra/title "UsersCollection"
                                        ::hydra/description "The collection of all users"
                                        ::hydra/is-paginated false
                                        ::hydra/member-class (vocab "User")}))

;; Entrypoint properties
(def issues-prop (hydra/link {::hydra/id (vocab "issues")
                              ::hydra/title "issues"
                              ::hydra/description "Returns all the issues"
                              ::hydra/range (vocab "IssuesCollection")}))

(def users-prop (hydra/link {::hydra/id (vocab "users")
                             ::hydra/title "users"
                             ::hydra/description "Returns all the issues"
                             ::hydra/range (vocab "UsersCollection")}))

(def register-user-prop (hydra/link {::hydra/id (vocab "registerUser")
                                     ::hydra/title "register_user"
                                     ::hydra/description "Creates a new User entity"
                                     ::hydra/range (vocab "User")}))

(def search-users-prop (hydra/templated-link {::hydra/id (vocab "searchUsesr")
                                              ::hydra/title "search_users"
                                              ::hydra/description "Searches registered users by name"
                                              ::hydra/range (vocab "User")}))

;; Entrypoint class
(def EntryPoint (hydra/class {::hydra/id (vocab "Entrypoint")
                              ::hydra/title "EntryPoint"
                              ::hydra/description "The main entry point of the API"
                              ::hydra/supported-properties [(hydra/supported-property {::hydra/id (vocab "get-issues-link")
                                                                                       ::hydra/property issues-prop
                                                                                       ::hydra/operations
                                                                                       [(hydra/get-operation {::hydra/returns (vocab "IssuesCollection")})]})
                                                            (hydra/supported-property {::hydra/id (vocab "get-users-link")
                                                                                       ::hydra/property users-prop
                                                                                       ::hydra/operations
                                                                                       [(hydra/get-operation {::hydra/returns (vocab "UsersCollection")})]})
                                                            (hydra/supported-property {::hydra/id (vocab "register-users-link")
                                                                                       ::hydra/property register-user-prop
                                                                                       ::hydra/operations
                                                                                       [(hydra/post-operation {::hydra/expects (vocab "User")
                                                                                                               ::hydra/returns (vocab "User")})]})
                                                            (hydra/supported-property {::hydra/id (vocab "search-users-link")
                                                                                       ::hydra/property search-users-prop
                                                                                       ::hydra/operations
                                                                                       [(hydra/get-operation {::hydra/returns (vocab "User")})]})]
                              ::hydra/operations [(hydra/get-operation {::hydra/returns (vocab "EntryPoint")
                                                                        ::hydra/description "The APIs main entry point."})]}))

;; API definition
(def API (hydra/api {::hydra/entrypoint "/"
                     ::hydra/entrypoint-class (vocab "EntryPoint")
                     ::hydra/supported-classes [EntryPoint
                                                IssuesCollection
                                                Issue
                                                User
                                                UsersCollection]}))
