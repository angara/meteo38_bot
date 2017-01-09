
(ns angara.process
  (:require
    [clojure.core.async :refer [<!! chan close!]]
    [taoensso.timbre :refer [debug info warn]]
    [mount.core :refer [defstate]]
    [mlib.conf :refer [conf]]
    [mlib.tlg.core :as tg]
    [mlib.tlg.poller :as tgp]))
    ;
  ;  [skichat.weather.darksky :refer [get-forecast forecast-text]]))
;

;
; (defn forecast [msg match]
;   (let [token (-> conf :bots :skichat :apikey)
;         cid (-> msg :chat :id)
;         place (last match)
;         latlng [51.39,104.85]
;         txt (forecast-text "Мамай" latlng 3)]
;     (tg/send-md token cid txt)))
; ;

(defn on-msg [msg match])
;

(defn on-message [msg]
  (when-let [text (-> msg :text not-empty)]
    (condp re-matches text
      #"(?ui)\?\s{0,3}(прогн)(оз)?(\s+(.*))?" :>> #(on-msg msg %)
      nil)))
;

(defn msg-log [msg]
  (debug "msg:" msg))
;

(defn cmd-loop [msg-chan]
  (debug "cmd-loop started.")
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


(defstate poller
  :start
    (tgp/start (-> conf :bots :angarabot))
  :stop
    (tgp/stop poller))
;

(defstate process
  :start
    (when poller
      (-> #(cmd-loop (tgp/get-chan poller)) Thread. .start))
  :stop
    (when-let [c (tgp/get-chan poller)]
      (close! c)))
;

;;.
