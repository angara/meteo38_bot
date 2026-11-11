(ns meteobot.data.pg
  (:require
   [mount.core :refer [defstate args]]
   [pg.core :as pg]
   ,))


(set! *warn-on-reflection* true)


(defn exec [conn sql params]
  (-> conn (pg/execute sql {:params params})))


(defn exec-one [conn sql params]
  (-> conn (pg/execute sql {:params params}) (first)))


(defstate dbc
  :start (pg/pool {:connection-uri (:database-url (args))})
  :stop (pg/close dbc))
