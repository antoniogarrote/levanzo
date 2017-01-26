(ns issues.routes
  (:require [issues.api :as api]
            [issues.db :as db]
            [levanzo.namespaces :refer [xsd hydra]]
            [levanzo.payload :as payload]
            [monger.operators :as mo]))

(def post-user (fn [args body request]
                 (-> (db/save "users"
                              #(-> body
                                   (payload/merge-jsonld
                                    (payload/jsonld
                                     (payload/id {:model api/User
                                                  :args {:user-id %}})
                                     (payload/type api/User)
                                     (payload/supported-link {:property api/raised-issues-prop
                                                              :model (api/vocab "raised-issues-link")
                                                              :args {:user-id %}})))))
                     payload/compact
                     (dissoc "password"))))


(def get-users (fn [{:keys [page] :or {page "1"}} body request]
                 (let [page (Integer/parseInt page)
                       users (map #(dissoc % "password") (db/find-all "users" page))]
                   (-> (payload/jsonld (payload/id {:model (api/vocab "get-users-link")})
                                       (payload/type api/UsersCollection)
                                       (payload/title "Users Collection")
                                       (payload/members users)
                                       (payload/total-items (count users))
                                       (payload/partial-view {:model (api/vocab "get-users-link")
                                                              :args {}
                                                              :current page
                                                              :pagination-param "page"
                                                              :next (inc page)
                                                              :previous (if (> page 1) (dec page) nil)}))
                       (payload/compact {:context true})))))

(def get-search-users (fn [{:keys [page name] :or {page "1"} :as args} body request]
                        (let [page (Integer/parseInt page)
                              users (db/find-all "users"
                                                 {:name {mo/$regex (str ".*" (:name args) ".*")}}
                                                 page)]
                          (-> (payload/jsonld (payload/id (api/vocab "search-users-link"))
                                              (payload/type api/UsersCollection)
                                              (payload/title "Users found")
                                              (payload/members users)
                                              (payload/total-items (count users))
                                              (payload/partial-view {:model (api/vocab "search-users-link")
                                                                     :args {}
                                                                     :current page
                                                                     :pagination-param "page"
                                                                     :next (inc page)
                                                                     :previous (if (> page 1) (dec page) nil)}))
                              (payload/compact)))))

(defn trace [x] (prn x) x)
(def get-user (fn [args body request] (trace (dissoc (db/find-one "users" (payload/link-for {:model api/User :args args})) "password"))))

(def put-user (fn [args body request]
                (->> (payload/link-for {:model api/User :args args})
                     (db/find-one "users")
                     (payload/merge-jsonld body)
                     (update "users")
                     (payload/compact))))

(def get-issues-for-user (fn [{:keys [page] :as args :or {page "1"}} body request]
                           (let [page (Integer/parseInt page)
                                 link (->  (payload/supported-link {:property (api/vocab "raised_by")
                                                                    :model api/User
                                                                    :args args})
                                           (payload/compact {:context false}))
                                 issues (db/find-all "issues" link page)]
                             (-> (payload/jsonld
                                  (payload/id {:model (api/vocab "raised-issues-link")
                                               :args args})
                                  (payload/type api/IssuesCollection)
                                  (payload/title "Issues Collection")
                                  (payload/members (map payload/expand issues))
                                  (payload/total-items (count issues))
                                  (payload/partial-view {:model (api/vocab "raised-issues-link")
                                                         :args args
                                                         :current page
                                                         :pagination-param "page"
                                                         :next (inc page)
                                                         :previous (if (> page 1) (dec page) nil)}))
                                 (payload/compact)))))

(def delete-user (fn [args body request]
                   (->> (payload/link-for {:model api/User :args args})
                        (db/delete "users"))
                   nil))

(def get-user-for-issue (fn [{:keys [user-id]} body request]
                          (get-user {:user-id user-id} body request)))

(def post-issue-for-user (fn [args body request]
                           (db/save "issues" #(-> body
                                                  (payload/merge-jsonld
                                                   (payload/jsonld
                                                    (payload/id {:model api/Issue
                                                                 :args {:issue-id %}})
                                                    (payload/type api/Issue)
                                                    (payload/supported-property {:property api/is-open-prop
                                                                                 :value true})
                                                    (payload/supported-link {:property (api/vocab "raised_by")
                                                                             :model api/User
                                                                             :args args})))
                                                  (payload/compact)))))

(def get-issues (fn [{:keys [page] :or {page "1"}} body request]
                  (let [page (Integer/parseInt page)
                        issues (db/find-all "issues" page)]
                    (-> (payload/jsonld (payload/id {:model (api/vocab "get-issues-link")})
                                        (payload/type api/IssuesCollection)
                                        (payload/title "Issues Collection")
                                        (payload/members issues)
                                        (payload/total-items (count issues))
                                        (payload/partial-view {:model (api/vocab "get-issues-link")
                                                               :args {}
                                                               :current page
                                                               :pagination-param "page"
                                                               :next (inc page)
                                                               :previous (if (> page 1) (dec page) nil)}))
                        (payload/compact {:context true})))))


(def get-issue (fn [args body request] (db/find-one "issues" (payload/link-for {:model api/Issue :args args}))))

(def put-issue (fn [args body request]
                 (->> (payload/link-for {:model api/Issue :args args})
                      (db/find-one "issues")
                      (payload/merge-jsonld body)
                      (update "issues")
                      (payload/compact))))

(def delete-issue (fn [args body request]
                    (->> (payload/link-for {:model api/Issue :args args})
                         (db/delete "issues"))
                    nil))

(def get-entrypoint (fn [args body request]
                      (-> (payload/jsonld
                           (payload/id {:model api/EntryPoint})
                           (payload/type api/EntryPoint)
                           (payload/supported-property {:property "hydra:title"
                                                        :value "Issues API Entry Point"})
                           (payload/supported-link {:property api/issues-prop
                                                    :model (api/vocab "get-issues-link")})
                           (payload/supported-link {:property api/users-prop
                                                    :model (api/vocab "get-users-link")})
                           (payload/supported-link {:property api/register-user-prop
                                                    :model (api/vocab "register-users-link")})
                           (payload/supported-template  {:property api/search-users-prop
                                                         :template "/search-users{?name}"
                                                         :representation :basic
                                                         :mapping [{:variable :name
                                                                    :range (xsd "string")
                                                                    :required true}]}))
                          (payload/compact))))

(def routes {:path [""]
             :model api/EntryPoint
             :handlers {:get get-entrypoint}
             :nested [{:path ["issues"]
                       :model (api/vocab "get-issues-link")
                       :params {:page {:range (xsd "integer")
                                       :required false}}
                       :handlers {:get get-issues}
                       :nested [{:path ["/" :issue-id]
                                 :model api/Issue
                                 :handlers {:get get-issue
                                            :put put-issue
                                            :delete delete-issue}
                                 :nested [{:path ["/users/" :user-id]
                                           :model (api/vocab "raised-by-link")
                                           :handlers {:get get-user-for-issue}}]}]}
                      {:path ["users"]
                       :model (api/vocab "get-users-link")
                       :params {:page {:range (xsd "integer")
                                       :required false}}
                       :handlers {:get get-users}
                       :nested [{:path ["/" :user-id]
                                 :model api/User
                                 :handlers {:get get-user
                                            :put put-user
                                            :delete delete-user}
                                 :nested [{:path "/raised_issues"
                                           :model (api/vocab "raised-issues-link")
                                           :handlers {:get get-issues-for-user
                                                      :post post-issue-for-user}}]}]}
                      {:path ["register-users"]
                       :model (api/vocab "register-users-link")
                       :handlers {:post post-user}}
                      {:path ["search-users"]
                       :params {:page {:range (xsd "integer")
                                       :required false}
                                :name {:range (xsd "string")
                                       :required true}}
                       :model (api/vocab "search-users-link")
                       :handlers {:get get-search-users}}]})
