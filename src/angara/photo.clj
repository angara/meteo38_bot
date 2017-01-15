
(ns angara.photo
  (:require
    [taoensso.timbre :refer [debug info warn]]
;    [mount.core :refer [defstate]]
    [mlib.conf :refer [conf]]
    [mlib.tlg.core :refer [send-text]]
    [angara.util :refer [chat-creds]]))
;

(defn handle-photo [msg photo]
  (let [[apikey cid] (chat-creds msg)]
    (send-text apikey cid (str "photo:" photo))))
;

;;.
