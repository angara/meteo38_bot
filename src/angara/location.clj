
(ns angara.location
  (:require
    [mlib.conf :refer [conf]]
    [mlib.tlg.core :refer [send-text]]
    [angara.util :refer [chat-creds]]
    [angara.db :refer [insert-loc]]))
;


(defn handle-location [msg location]
  (let [[apikey cid] (chat-creds msg)]
    (when
      (insert-loc (:chat msg) (:from msg) location)
      (send-text apikey cid
        (str "Геопозиция сохранена - ["
          (:latitude location) "," (:longitude location) "]")))))
;

;
