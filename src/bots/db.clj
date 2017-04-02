
(ns bots.db
  (:require
    [mount.core :refer [defstate]]
    ;
    [mlib.conf :refer [conf]]
    [mlib.mdb.conn :refer [connect disconnect]]))
;


(defstate mdb_angara
  :start
    (connect (-> conf :mdb :angara))
  :stop
    (disconnect mdb_angara))
;

(defstate mdb_meteo
  :start
    (connect (-> conf :mdb :meteo))
  :stop
    (disconnect mdb_meteo))
;

(defn dbc []
  (:db mdb_angara))
;

(defn db_meteo []
  (:db mdb_meteo))
;

;;.
