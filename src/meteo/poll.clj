
(ns meteo.poll
  (:require
    [mount.core :refer [defstate]]
    ;
    [mlib.conf :refer [conf]]
    [mlib.log :refer [debug warn]]
    [mlib.core :refer [to-int]]
    [mlib.tlg.core :as tg]
    ;
    [meteo.data :refer [mbot-log]]
    [meteo.commands :refer [on-message on-callback on-inline]]))
;


(def api-error-sleep 3000)


(defn dispatch-update [upd]
  (try
    (mbot-log upd)
    (condp #(%1 %2) upd
      :message :>> on-message
      :edited_message :>> on-message
      :callback_query :>> on-callback
      :inline_query   :>> on-inline
      :chosen_inline_result nil
      (debug "dispatch: unexpected update -" upd))
    (catch Exception e
      (warn "dispatch:" upd (or (.getMessage e) e)))))
;


(defn update-loop [mbot cnf dispatcher]
  (let [token (:apikey cnf)
        poll-limit (:poll-limit cnf 100)
        poll-timeout (:poll-timeout cnf 1)]
    ;
    (reset! (:run-flag mbot) true)
    (debug "update-loop started")
    ;
    (loop [last-id 0  updates nil]
      (if (deref (:run-flag mbot))
        (if-let [u (first updates)]
          (let [id (-> u :update_id to-int)]
            (if (< last-id id)
              (dispatcher u)
              (debug "update-dupe:" id))
            (recur id (next updates)))
          ;
          (let [upd (tg/api token :getUpdates
                      { :offset (inc last-id)
                        :limit poll-limit
                        :timeout poll-timeout})]
            (when-not upd
              (warn "api-error")
              (Thread/sleep api-error-sleep))
            (recur last-id upd)))
        ;;
        (debug "update-loop stop")))))
;

(defstate mbot
  :start
    (let [cfg (-> conf :bots :meteo38bot)]
      (if (:apikey cfg)
        {:run-flag (atom nil)
         :thread (->
                    #(update-loop mbot cfg dispatch-update)
                    Thread. 
                    .start)}
        (do
          (warn "mbot did not start")
          false)))
  :stop
    (when mbot
      (reset! (:run-flag mbot) false)))
;

;;.
