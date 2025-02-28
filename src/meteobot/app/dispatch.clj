(ns meteobot.app.dispatch
  (:require
   [meteobot.app.inbound :as in]
   [meteobot.metrics.reg :refer [inc-metric]]
   ))


(def PRIVATE_HANDLERS_MAP 
  {
   :message #'in/message
   :callback_query #'in/callback
   })


(defn handler-by-type [update-type chat-type]
  (case chat-type
    "private" (get PRIVATE_HANDLERS_MAP update-type)
    ;; "group"      (get GROUP_HANDLERS_MAP update-type)
    ;; "supergroup" (get SUPERGROUP_HANDLERS_MAP update-type)
    ;; "channel"    (get CHANNEL_HANDLERS_MAP update-type)
    nil
    ))


(defn router [ctx upd]
  (let [[u t] (keys upd)
        update-type (if (= :update_id u) t u)
        data (get upd update-type)
        chat-type (or
                   (some-> data :chat :type)
                   (some-> data :message :chat :type))
        _ (inc-metric :meteobot/telegram-updates {:type (name update-type)}) ;; ?chat-type?
        handler (handler-by-type update-type chat-type)]
    (if handler
      (handler ctx data)
      (in/unhandled ctx upd)
      ,)))
