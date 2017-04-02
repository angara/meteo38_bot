
(ns angara.process
  (:require
    [clojure.core.async :refer [<!! chan close!]]
    [mount.core :refer [defstate]]
    [mlib.conf :refer [conf]]
    [mlib.log :refer [debug info warn]]
    [mlib.tlg.core :as tg]
    [mlib.tlg.poller :as tgp]
    [angara.location :refer [handle-location]]
    [angara.photo :refer [handle-photo]]))
;

(defn on-message [msg]
  (condp #(%1 %2) msg
    :location :>> #(handle-location msg %)
    :photo    :>> #(handle-photo msg %)
    (debug "on-message:" msg)))

  ; (when-let [text (-> msg :text not-empty)]
  ;   (condp re-matches text
  ;     #"(?ui)\?\s{0,3}(прогн)(оз)?(\s+(.*))?" :>> #(on-msg msg %)
  ;     nil)))
;

(defn msg-log [msg]
  (debug "msg-log:" msg))
;

(defn cmd-loop [msg-chan]
  (debug "cmd-loop started.")
  (let [apikey (-> conf :bots :angarabot :apikey)]
    (loop []
      (when-let [msg (<!! msg-chan)]
        (try
          (msg-log msg)
          (condp #(%1 %2) msg
            :message :>>
              #(on-message (assoc % :apikey apikey))
  ;          :callback_query :>> on-callback
            (debug "unexpected:" msg))
          (catch Exception e
            (warn "dispatch:" msg e)))
        (recur)))
    (debug "cmd-loop stopped.")))
;


(defstate poller
  :start
    (tgp/start (-> conf :bots :angarabot))
  :stop
    (when poller
      (tgp/stop poller)))
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
