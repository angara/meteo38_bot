(ns meteobot.data.sql
  (:require
    [clojure.java.io :as io]
    [pg.hugsql :as hug]
   ,))
  

(declare user-favs)
(declare save-favs)

(hug/def-db-fns (io/resource "meteobot/data/api.sql"))

