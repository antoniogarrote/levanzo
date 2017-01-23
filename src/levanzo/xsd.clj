(ns levanzo.xsd
  (:require [levanzo.namespaces :refer [xsd]]))

(def integer (xsd "integer"))
(def float (xsd "float"))
(def double (xsd "double"))
(def string (xsd "double"))
(def time (xsd "time"))
(def date (xsd "date"))
(def date-time (xsd "date-time"))
