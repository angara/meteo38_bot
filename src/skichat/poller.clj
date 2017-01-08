
(ns skichat.poller
  (:require
    [clojure.core.async :refer [>!! chan]]
    [taoensso.timbre :refer [debug info warn]]
    [mount.core :refer [defstate]]
    [mlib.conf :refer [conf]]
    [mlib.core :refer [to-int]]
    [mlib.telegram :as tg]))
;


(def api-error-sleep 3000)


(defn update-loop [run-flag cnf msg-chan]
  (let [token (:apikey cnf)
        poll-limit (:poll-limit cnf 100)
        poll-timeout (:poll-timeout cnf 1)]
    ;
    (reset! run-flag true)
    (debug "update-loop started.")
    ;
    (loop [last-id 0  updates nil]
      (if @run-flag
        (if-let [u (first updates)]
          (let [id (-> u :update_id to-int)]
            (if (< last-id id)
              (when-not (>!! msg-chan u)
                (info "msg-chan closed! exiting loop")
                (reset! run-flag false))
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
        (debug "update-loop stopped.")))))
;

(defstate poller
  :start
    (let [cnf (-> conf :bots :skichat)
          run-flag (atom nil)
          msg-chan (chan)]
      { :run-flag
          run-flag
        :msg-chan
          msg-chan
        :res
          (if (-> cnf :apikey not-empty)
            (-> #(update-loop run-flag cnf msg-chan) Thread. .start)
            (warn "skichat bot disabled in config."))})
  :stop
    (reset! (:run-flag poller) false))
;


;;.
