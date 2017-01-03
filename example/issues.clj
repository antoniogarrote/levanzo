(ns issues
  (:require [levanzo.hydra :as hydra]
            [levanzo.routing :as routing]
            [clojure.spec.test :as stest]))

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

(def User (hydra/class {::hydra/id ":User"
                        ::hydra/title "User"
                        ::hydra/description "A User represents a person registered in the system."
                        ::hydra/supported-properties [(hydra/property {::hydra/property ":name"
                                                                       ::hydra/title "name"
                                                                       ::hydra/description "The user's full name"
                                                                       ::hydra/range "xsd:string"})
                                                      (hydra/property {::hydra/property ":email"
                                                                       ::hydra/title "email"
                                                                       ::hydra/description "The user's email address"
                                                                       ::hydra/range "xsd:string"})
                                                      (hydra/property {::hydra/property ":password"
                                                                       ::hydra/title "password"
                                                                       ::hydra/description "The user's password"
                                                                       ::hydra/range "xsd:string"
                                                                       ::hydra/writeonly true})
                                                      (hydra/link {::hydra/id ":raised_issues_link"
                                                                   ::hydra/title "raised_issues"
                                                                   ::hydra/description "The issues raised by this user"
                                                                   ::hydra/property ":raissed_issues"
                                                                   ::hydra/route ["raised_issues"]
                                                                   ::hydra/range ":IssuesCollection"
                                                                   ::hydra/readonly true
                                                                   ::hydra/operations [(hydra/get-operation {::hydra/returns ":IssuesCollection"
                                                                                                             ::hydra/handler get-issues-for-user
                                                                                                             ::hydra/description "Retrieves the issues raised by a User entity"})]})]
                        ::hydra/operations [(hydra/get-operation {::hydra/returns ":User"
                                                                  ::hydra/id ":user_retrieve"
                                                                  ::hydra/description "Retrieves a User entity"
                                                                  ::hydra/handler get-user})
                                            (hydra/put-operation {::hydra/returns ":User"
                                                                  ::hydra/expects ":User"
                                                                  ::hydra/id ":user_replace"
                                                                  ::hydra/description "Replaces an existing User entity"
                                                                  ::hydra/handler put-user})
                                            (hydra/delete-operation {::hydra/id ":user_delete"
                                                                     ::hydra/description "Deletes a User entity"
                                                                     ::hydra/handler delete-user})]}))

(def Issue (hydra/class {::hydra/id ":Issue"
                         ::hydra/title ":Issue"
                         ::hydra/description "An Issue tracked by the system."
                         ::hydra/supported-properties [(hydra/property {::hydra/property ":title"
                                                                        ::hydra/title "title"
                                                                        ::hydra/description "The issue's title"
                                                                        ::hydra/range "xsd:string"})
                                                       (hydra/property {::hydra/property ":description"
                                                                        ::hydra/title "description"
                                                                        ::hydra/description "A description of the issue"
                                                                        ::hydra/range "xsd:string"})
                                                       (hydra/property {::hydra/property ":createdAt"
                                                                        ::hydra/title "created_at"
                                                                        ::hydra/description "The date and time this issue was created"
                                                                        ::hydra/range "xsd:dateTime"
                                                                        ::hydra/readonly true})
                                                       (hydra/property {::hydra/property ":isOpen"
                                                                        ::hydra/title "is_open"
                                                                        ::hydra/description "Is the issue open?\nUse for 1 yes, 0 for no when modifying this value."
                                                                        ::hydra/range "xsd:boolean"
                                                                        ::hydra/readonly true})
                                                       (hydra/link {::hydra/id ":raised_by"
                                                                    ::hydra/property ":raisedBy"
                                                                    ::hydra/title "raised_by"
                                                                    ::hydra/description "The user who raised the issue"
                                                                    ::hydra/range ":User"
                                                                    ::hydra/readonly true
                                                                    ::hydra/route ["/users" :user-id]
                                                                    ::hydra/operations [(hydra/get-operation {::hydra/returns ":User"
                                                                                                              ::hydra/handler get-user-for-issue
                                                                                                              ::hydra/description "Retrieves a User entity"})]})]
                         ::hydra/operations [(hydra/get-operation {::hydra/returns ":Issue"
                                                                   ::hydra/id ":issue_retrieve"
                                                                   ::hydra/description "Retrieves a Issue entity"
                                                                   ::hydra/handler get-issue})
                                             (hydra/put-operation {::hydra/returns ":Issue"
                                                                   ::hydra/expects ":Issue"
                                                                   ::hydra/id ":issue_replace"
                                                                   ::hydra/description "Replaces an existing Issue entity"
                                                                   ::hydra/handler put-issue})
                                             (hydra/delete-operation {::hydra/id ":issue_delete"
                                                                      ::hydra/description "Deletes a Issue entity"
                                                                      ::hydra/handler delete-issue})]}))

(def IssuesCollection (hydra/collection {::hydra/id ":IssuesCollection"
                                         ::hydra/title ":IssuesCollection"
                                         ::hydra/description "The collection of all issues"
                                         ::hydra/is-paginated false
                                         ::hydra/member-class ":Issue"
                                         ::hydra/member-route ["/issues" :issue-id]}))

(def UsersCollection (hydra/collection {::hydra/id ":UsersCollection"
                                        ::hydra/title ":UsersCollection"
                                        ::hydra/description "The collection of all users"
                                        ::hydra/is-paginated false
                                        ::hydra/member-class ":User"
                                        ::hydra/member-route ["/users" :user-id]}))


(def EntryPoint (hydra/class {::hydra/id ":EntryPoint"
                              ::hydra/title "EntryPoint"
                              ::hydra/description "The main entry point of the API"
                              ::hydra/supported-properties [(hydra/link {::hydra/id ":get-issues-link"
                                                                         ::hydra/title "issues"
                                                                         ::hydra/description "Returns all the issues"
                                                                         ::hydra/property ":issues"
                                                                         ::hydra/route ["issues"]
                                                                         ::hydra/range ":IssuesCollection"
                                                                         ::hydra/operations [(hydra/post-operation {::hydra/expects ":Issue"
                                                                                                                    ::hydra/returns ":Issue"
                                                                                                                    ::hydra/handler post-issue})
                                                                                             (hydra/get-operation {::hydra/returns ":IssuesCollection"
                                                                                                                   ::hydra/handler get-issues})]})
                                                            (hydra/link {::hydra/id ":get-users-link"
                                                                         ::hydra/title "users"
                                                                         ::hydra/description "Returns all the issues"
                                                                         ::hydra/property ":users"
                                                                         ::hydra/route ["users"]
                                                                         ::hydra/range ":UsersCollection"
                                                                         ::hydra/operations [(hydra/get-operation {::hydra/returns ":UsersCollection"
                                                                                                                   ::hydra/handler get-users})]})
                                                            (hydra/link {::hydra/id ":post-users-link"
                                                                         ::hydra/title "register_user"
                                                                         ::hydra/description "Creates a new User entity"
                                                                         ::hydra/property ":registerUser"
                                                                         ::hydra/route ["registerUser"]
                                                                         ::hydra/range ":User"
                                                                         ::hydra/operations [(hydra/post-operation {::hydra/expects ":User"
                                                                                                                    ::hydra/returns ":User"
                                                                                                                    ::hydra/handler post-issue})]})]
                              ::hydra/operations [(hydra/get-operation {::hydra/returns ":EntryPoint"
                                                                        ::hydra/description "The APIs main entry point."
                                                                        ::hydra/handler get-entrypoint})]}))

(def API (hydra/api {::hydra/entrypoint "/"
                     ::hydra/entrypoint-class ":EntryPoint"
                     ::hydra/supported-classes [EntryPoint
                                                IssuesCollection
                                                Issue
                                                User
                                                UsersCollection]}))


(comment
  (stest/instrument `routing/parse-routes)
  (stest/instrument `routing/class-links)
  (stest/instrument `routing/property-cycle?)
  (clojure.pprint/pprint (->> (routing/parse-routes API)
                              (mapv (fn [route]
                                      {:prop (if (some? (::routing/route-link route))
                                               (-> route ::routing/route-link :common-props ::hydra/id)
                                               nil)
                                       :op (if (some? (::hydra/Operation route))
                                             (-> route ::hydra/Operation :common-props ::hydra/id)
                                             nil)
                                       :method (-> route ::hydra/Operation :operation-props ::hydra/method)
                                       :path (::routing/path route)
                                       :property-path (::routing/property-path route)
                                       :handler (::hydra/handler route)}))))
  )
