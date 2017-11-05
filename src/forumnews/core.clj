(ns forumnews.core
  (:require
    [clojure.string :as s]
    [clojure.core.async :refer [thread]]
    [clj-time.core :as tc]
    [monger.collection :as mc]
    [honeysql.helpers :as hs]
    [mount.core :refer [defstate]]
    [mlib.conf :refer [conf]]
    [mlib.log :refer [debug info warn try-warn]]
    [mlib.time :refer [now-ms]]
    [mlib.psql.core :refer [fetch]]
    [mlib.tlg.core :as tg]
    [bots.db :refer [dbc]]))
;


(def FETCH-TOPICS 
  (->
    (hs/select :*)
    (hs/from :forum_topics)
    (hs/where 
      [:> :tid :?last-tid] 
      [:>= :created :?last-time])
      ;; [:< :tgroup 1000]
    (hs/order-by [:tid :asc])
    (hs/limit :?limit)))
;    

(def BOTSTATE_COLL "bot_state")
(def BOTSTATE_ID   "forumnews")


(defn get-last-tid []
  (try-warn "save-last-tid:"
    (->
      (mc/find-map-by-id (dbc) BOTSTATE_COLL BOTSTATE_ID) 
      (:last_tid 0))))
;

(defn save-last-tid [tid]
  (try-warn "save-last-tid:"
    (mc/update (dbc) BOTSTATE_COLL 
      {:_id BOTSTATE_ID}
      {:$set {:last_tid tid :ts (tc/now)}}
      {:upsert true})))
;

(defn fetch-topics [last-tid age-limit fetch-limit]
  (fetch FETCH-TOPICS 
    {
      :last-tid  last-tid 
      :last-time (tc/minus (tc/now) (tc/seconds age-limit))
      :limit     fetch-limit}))
;

(defn hesc [text]
  (s/escape text {\& "&amp;" \< "&lt;" \> "&gt;" \" "&quot;"}))
;

(defn update-channel [apikey channel topics]
  (Thread/sleep 30)
  (let [fmt (fn [t]
              (str "<a href=\"https://angara.net/forum/t" (:tid t) "\">" (hesc (:title t)) "</a>"))
        txs (map fmt (reverse topics))]
    (tg/send-message apikey channel
      {
        :text (str "-\n" (s/join "\n\n" txs)) 
        :parse_mode "HTML" 
        :disable_web_page_preview true})))
;

(defn forum-task [cfg]
  (try
    ; (debug "task:" cfg)
    (let [bot (:bot cfg)
          apikey (get-in (:bots conf) [bot :apikey])
          chn (:channel cfg)
          tpm (:topics-per-message cfg)
          last-tid (get-last-tid)
          topics (not-empty (fetch-topics last-tid (:age-limit cfg) (:fetch-limit cfg)))]
      (when topics
        (let [max-tid (reduce #(max %1 (:tid %2)) last-tid topics)]
          (when (not= last-tid max-tid)
            (save-last-tid max-tid))
          (doseq [tps (partition tpm tpm nil topics)]
            (update-channel apikey chn tps)))))
    (catch Exception e
      (warn "forum-task:" e))))
;

(defn start-periodical-task [interval task-fn]
  (let [runflag (atom true)]
    {:runflag runflag
     :thread  (thread
                (loop [t0 0]
                  (task-fn)
                  (when @runflag
                    (let [dt (- interval (- (now-ms) t0))]
                      (when (> dt 0)
                        (Thread/sleep dt)))
                    (recur (now-ms)))))}))
;

(defn stop-periodical-task [process]
  (reset! (:runflag process) false))                      
;

(defstate process
  :start
    (if-let [cfg (:forumnews conf)]
      (do
        (debug "forumnews start:" cfg)
        (start-periodical-task 
          (* (:fetch-interval cfg) 1000)
          #(forum-task cfg)))
      (do
        (warn "forumnews did not start")
        false))
  :stop
    (stop-periodical-task process))

;;.
