(ns meteobot.data.pg
  (:require
   [mount.core :refer [defstate args]]
   [pg.core :as pg]
   [pg.pool :as pool]
   ,))


(set! *warn-on-reflection* true)


(defn exec [conn sql params]
  (-> conn (pg/execute sql {:params params})))


(defn exec-one [conn sql params]
  (-> conn (pg/execute sql {:params params}) (first)))


(defstate dbc
  :start (pool/pool {:connection-uri (:database-url (args))})
  :stop (.close ^java.lang.AutoCloseable dbc))
