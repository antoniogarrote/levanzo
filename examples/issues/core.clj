(ns isses.core
  (:require [issues.api :as api]
            [issues.routes :as routes]
            [issues.db :as db]
            [issues.config :as config]
            [levanzo.payload :as payload]
            [levanzo.routing :as routing]
            [levanzo.namespaces :refer [xsd hydra]]
            [clojure.test :refer :all]
            [org.httpkit.server :as http-kit]
            [monger.collection :as mc]))

(defn start-server []
  (http-kit/run-server routes/middleware {:port config/port}))

(deftest api-operations-test
  (mc/remove db/db "issues" {})
  (mc/remove db/db "users" {})
  (mc/remove db/db "counters" {})
  (payload/context {:vocab (str config/host "vocab#")
                    :base config/host
                    "_id" "@id"})

  (def user (routes/post-user {} (-> (payload/jsonld
                                      (payload/supported-property {:property api/name-prop
                                                                   :value "User n"})
                                      (payload/supported-property {:property api/email-prop
                                                                   :value "usen1@test.com"})
                                      (payload/supported-property {:property api/password-prop
                                                                   :value "test00n"}))
                                     (payload/expand))
                              {}))

  (def user-resource-url (-> user payload/compact (get "_id")))
  (is (= user-resource-url "users/1"))
  (def user-id (-> (routing/match user-resource-url) :route-params :user-id))
  (is (= user-id "1"))

  (def users-collection (routes/get-users {} nil nil))
  (is (= "users" (get users-collection "_id")))
  (def users-collection-members (get (payload/expand users-collection) (hydra "member")))
  (is (= 1 (count users-collection-members)))

  (doseq [i (map inc (range 0 15))]
    (routes/post-issue-for-user
     {:user-id user-id}
     (-> (payload/jsonld
          (payload/supported-property {:property api/title-prop :value (str "Test Issue " i)})
          (payload/supported-property {:property api/description-prop :value (str "This is just a test issue " i)})
          (payload/supported-property {:property api/created-at-prop
                                       :value {"@value" (str (java.time.LocalDateTime/now))
                                               "@type" (xsd "dateTime")}}))
         (payload/expand))
     nil))
  (def users-issues-collection (routes/get-issues-for-user {:user-id user-id :page "1"} {} nil))
  (def users-issues-collection-members (get (payload/expand users-issues-collection) (hydra "member")))
  (is (= 5 (count users-issues-collection-members)))
  (def users-issues-collection (routes/get-issues-for-user {:user-id user-id :page "2"} {} nil))
  (def users-issues-collection-members (get (payload/expand users-issues-collection) (hydra "member")))
  (is (= 5 (count users-issues-collection-members)))
  (def users-issues-collection (routes/get-issues-for-user {:user-id user-id :page "3"} {} nil))
  (def users-issues-collection-members (get (payload/expand users-issues-collection) (hydra "member")))
  (is (= 5 (count users-issues-collection-members)))
  (def users-issues-collection (routes/get-issues-for-user {:user-id user-id :page "4"} {} nil))
  (def users-issues-collection-members (get (payload/expand users-issues-collection) (hydra "member")))
  (is (= 0 (count users-issues-collection-members)))
  (def users-issues-collection (routes/get-issues-for-user {:user-id "99" :page "1"} {} nil))
  (def users-issues-collection-members (get (payload/expand users-issues-collection) (hydra "member")))
  (is (= 0 (count users-issues-collection-members)))


  (def issues-collection (routes/get-issues {} {} nil))
  (def issues-collection-members (get (payload/expand issues-collection) (hydra "member")))
  (is (= 5 (count issues-collection-members)))
  (def issues-collection (routes/get-issues {:page "2"} {} nil))
  (def issues-collection-members (get (payload/expand issues-collection) (hydra "member")))
  (is (= 5 (count issues-collection-members)))
  (def issues-collection (routes/get-issues {:page "3"} {} nil))
  (def issues-collection-members (get (payload/expand issues-collection) (hydra "member")))
  (is (= 5 (count issues-collection-members)))
  (def issues-collection (routes/get-issues {:page "4"} {} nil))
  (def issues-collection-members (get (payload/expand issues-collection) (hydra "member")))
  (is (= 0 (count issues-collection-members)))




  (routes/delete-user {:user-id user-id} nil nil)
  (def users-collection (routes/get-users {} nil nil))
  (is (= "users" (get users-collection "_id")))
  (def users-collection-members (get (payload/expand users-collection) (hydra "member")))
  (is (= 0 (count users-collection-members))))
