(ns meteobot.data.sql
  (:require
    [clojure.java.io :as io]
    [pg.hugsql :as hug]
   ,))
  

(declare user-favs)
(declare save-favs)
(declare user-subs)
(declare user-subs-by-id)
(declare user-subs-create)
(declare user-subs-update)
(declare user-subs-delete)
(declare subs-hhmm)

(hug/def-db-fns (io/resource "meteobot/data/api.sql"))
