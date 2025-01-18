(ns meteobot.app.serv
  (:require
   [taoensso.telemere :refer [log!]]
   [mount.core :refer [defstate args]]
   [mlib.telegram.botapi :refer [get-me get-updates LONG_POLLING_TIMEOUT send-text]]
   ,))


(set! *warn-on-reflection* true)


(defn update-handler [{apikey :apikey} upd] 
  (log! ["update handler:" upd])
  (let [message (:message upd)
        chat-id (-> message :chat :id)
        text (-> message :text)
        ]
  ;; XXX: !!!
    (let [rc (send-text apikey chat-id (str ">" text))]
      (log! ["rc:" rc])
      )
  ))


(def GET_UPDATES_ERROR_PAUSE 4000)


(defn- ensure-int ^long [n]
  (if (int? n) n 
      (do
        (log! :warn ["value is not intteger:" n])
        0)))


(defn poller-loop [cfg handler]
  (let [apikey (:telegram-apikey cfg)
        bot-info (get-me apikey)
        ctx {:apikey apikey :bot-name (:username bot-info)}
        poll-opts {:timeout LONG_POLLING_TIMEOUT}]
    (log! ["start poller-loop for:" (select-keys bot-info [:username :id])])
    (loop [[upd & rest] nil last-id 0]
      (if upd
        (do
          (try
            ;; (inc :telegram-updates-in)
            (handler ctx upd)
            (catch InterruptedException ex 
              (log! "poller-loop: handler interrupted")
              (throw ex))
            (catch Exception ex
              ;; (inc :telegram-handler-err)
              (log! {:level :warn :error ex :msg ["update handler exception"]})))
          (recur rest (ensure-int (:update_id upd))))
        (let [updates (try
                        (get-updates apikey (inc last-id) poll-opts)
                        (catch InterruptedException ex 
                          (log! "poller-loop: get-updates interrupted")
                          (throw ex))
                        (catch Exception ex
                          ;; (inc :telegram-updates-err)
                          (log! {:level :warn :data (ex-data ex) :msg ["get-updates exception" ex]})
                          (Thread/sleep ^long GET_UPDATES_ERROR_PAUSE)
                          nil))]
          (recur updates last-id))
        ,))))


(defstate poller
  :start (let [cfg (args)]
           (doto 
            (Thread. #(poller-loop cfg update-handler))
            (.start)
             ))
  :stop (do
          (log! ["stop poller"])
          (.interrupt ^Thread poller)))
