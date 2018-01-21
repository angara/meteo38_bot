(ns forumnews.photos
  (:require
    [clojure.string :as s]
    [clj-time.core :as tc]
    [monger.collection :as mc]
    [honeysql.core :as sql]
    [honeysql.helpers :as hs]
    [mlib.conf :refer [conf]]
    [mlib.core :refer [to-int]]
    [mlib.log :refer [debug info warn try-warn]]
    [mlib.time :refer [now-ms]]
    [mlib.psql.core :refer [fetch]]
    [mlib.tlg.core :as tg :refer [hesc]]
    [bots.db :refer [dbc]]))
;


(def MSGS_PER_PAGE 20)

;; select msgid, topic, owner, updated, attach from forum_msgs 
;; where attach ilike 'jpg%' and not censored order by msgid;

(def FETCH-MSGS-JPG
  (->
    (hs/select 
      [:m.msgid :mid] [:m.topic :tid] [:m.owner :uid] [:m.updated :ts]
      [:t.title :title] 
      [:u.login :username])
    (hs/from [:forum_msgs :m]) 
    (hs/join [:forum_topics :t] [:= :m.topic :t.tid])
    (hs/left-join [:users :u] [:= :u.uid :m.owner])
    (hs/where 
      [:> :m.msgid :?last-mid]
      [:> :m.updated :?last-time]
      [:like :m.attach "jpg%"] 
      [:not= :m.censored true])
    (hs/order-by [:mid :asc])
    (hs/limit :?limit)))
;    

(def TOPIC-MIDS
  (->
    (hs/select [:m.msgid :mid])
    (hs/from [:forum_msgs :m])
    (hs/where [:= :m.topic :?tid])
    (hs/order-by [:mid :asc])))
;

(def BOTSTATE_COLL "bot_state")
(def BOTSTATE_ID   "forumphotos")


(defn get-last-mid []
  (try-warn "get-last-mid:"
    (-> (mc/find-map-by-id (dbc) BOTSTATE_COLL BOTSTATE_ID)
      (:last_mid 0))))
;

(defn save-last-mid [mid]
  (try-warn "save-last-mid:"
    (mc/update (dbc) BOTSTATE_COLL 
      {:_id BOTSTATE_ID}
      {:$set {:last_mid mid :ts (tc/now)}}
      {:upsert true})))
;

(defn fetch-msgs [last-mid age-limit fetch-limit]
  (fetch FETCH-MSGS-JPG
    {
      :last-mid  last-mid 
      :last-time (tc/minus (tc/now) (tc/seconds age-limit))
      :limit     fetch-limit}))
;

(defn msg-page [msg]
  (let [mid (:mid msg)
        mids (->> (fetch TOPIC-MIDS {:tid (:tid msg)}) (map :mid))
        idx  (first (keep-indexed (fn [i v] (when (= mid v) i)) mids))]
    (if idx
      (assoc msg :p (quot idx MSGS_PER_PAGE))
      msg)))
;

(defn base-uri [mid]
  (let [d0 (-> mid (mod 10000) (quot 100))
        d1 (-> mid (mod 100))]
    (format "/upload/%02d/%02d/f_%d.jpg" d0 d1 mid)))
  

(defn word-trunc [text len & [ellip]]
  (if (<= (.length text) len)
    text
    (let [ellip (or ellip "...")
          len (- len (.length ellip))
          idx (s/last-index-of text " " len)]
      (str (.substring text 0 (or idx len)) ellip))))
;


(defn update-channel [apikey channel msg]
  (Thread/sleep 30)
  (debug "msg:" msg)
  (let [photo-url (str "http://angara.net" (base-uri (:mid msg)))
        msg-url   (str "http://angara.net/forum/t" (:tid msg) 
                    (when-let [p (:p msg)] (str "?p=" p)) "#" (:mid msg))
        username (:username msg)
        ll (str "\n\n" username " - " msg-url)
        txt (str "Из темы:\n" (:title msg))
        txt (str 
              (word-trunc txt (- 199 (.length ll)))
              ll)]
    ; (debug "txt:" txt)
    (tg/api apikey :sendPhoto 
      {:chat_id channel :photo photo-url :caption txt})))
;

(defn forum-photos [cfg]
  (try
    (let [bot-id  (:bot cfg)
          chn     (:channel cfg)
          apikey  (get-in conf [:bots bot-id :apikey])
          last-mid (get-last-mid)
          msgs (not-empty (fetch-msgs last-mid (:age-limit cfg) (:fetch-limit cfg)))
          msgs (map msg-page msgs)]
      (when msgs
        (let [max-mid (reduce #(max %1 (:mid %2)) last-mid msgs)]
          (save-last-mid max-mid)
          (doseq [m msgs]
            (update-channel apikey chn m)))))
    (catch Exception e
      (warn "forum-photos:" e))))
;

;;.
