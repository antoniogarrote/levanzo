(ns levanzo.routing
  (:require [levanzo.hydra :as hydra]
            [clojure.spec :as s]))

;; root of the route path
(s/def ::path (s/cat :base ::hydra/path
                     :rest (s/* (s/or :var ::hydra/path-variable
                                      :path ::hydra/path))))


(s/def ::full-route (s/keys :req [::path
                                  ::hydra/method
                                  ::hydra/handler]))

(s/def ::routes (s/coll-of ::full-route
                           :gen-max 3))

;;(s/fdef parse-operation-route
;;        :args )
