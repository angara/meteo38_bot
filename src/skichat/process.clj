
(ns skichat.process
  (:require
    [clojure.core.async :refer [<!! chan thread close!]]
    [taoensso.timbre :refer [debug info warn]]
    [mount.core :refer [defstate]]
    [mlib.conf :refer [conf]]
    [mlib.telegram :as tg]
    [skichat.poller :refer [poller]]
    [skichat.weather.darksky :refer [get-forecast forecast-text]]))
;


(defn forecast [msg match]
  (let [token (-> conf :bots :skichat :apikey)
        cid (-> msg :chat :id)
        place (last match)
        latlng [51.39,104.85]
        txt (forecast-text "Мамай" latlng 3)]
    (tg/send-md token cid txt)))
;

(defn on-message [msg]
  (when-let [text (-> msg :text not-empty)]
    (condp re-matches text
      #"(?ui)\?\s{0,3}(прогн)(оз)?(\s+(.*))?" :>> #(forecast msg %)
      nil)))
;

(defn msg-log [msg]
  (prn "msg:" msg))
;

(defn cmd-loop [msg-chan]
  (loop []
    (when-let [msg (<!! msg-chan)]
      (try
        (msg-log msg)
        (condp #(%1 %2) msg
          :message :>> on-message
;          :callback_query :>> on-callback
          (debug "unexpected:" msg))
        (catch Exception e
          (warn "dispatch:" msg e)))
      (recur)))
  (debug "cmd-loop stopped."))
;

(defstate process
  :start
    (thread (cmd-loop (:msg-chan poller)))
  :stop
    (close! (:msg-chan poller)))
;

;;.
