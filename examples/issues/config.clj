(ns issues.config
  (:require [levanzo.http :as http]
            [levanzo.payload :as payload]
            [clojure.string :as string]))

(http/set-debug-errors! true)

(def port 8080)
(def host (str "http://localhost:" port "/"))
(payload/context {:vocab (str host "vocab#")
                  :base host})
