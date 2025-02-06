(ns migrate
  (:require
   [clojure.string :as str]
   [jsonista.core :as j]
   [java-time.api :as jt]
   [meteobot.data.pg :as pg]
   [meteobot.data.store :refer [active-stations]]
   ,))



(def SUBS 
  (->>
   (-> "./tmp/mbot_subs.jsonl" 
       (slurp) 
       (str/split #"\n"))
   (map #(j/read-value % j/keyword-keys-object-mapper))))


(def ST_ACTIVE 
  (set (map :st (active-stations 1 1 20))))


(defn number-long [id]
  (if-let [n (:$numberLong id)]
    (parse-long n)
    id
    ))


(defn convert-sub [{:keys [cid time ids]}]
  (->> ids 
       (filter ST_ACTIVE) 
       (set) 
       (map (fn [st]
              {:user_id (number-long cid)
               :wdays "1234567"
               :hhmm (jt/local-time time)
               :st st
               }
              ))))

(defn insert-sub [{:keys [user_id hhmm wdays st]}]
  (prn user_id hhmm wdays st)
  (pg/exec pg/dbc 
           "insert into meteobot_subs(ts, user_id, hhmm, wdays, st) values(now(),$1,$2,$3,$4)" 
           [user_id hhmm wdays st])
  )



(comment

  (->> SUBS
       (map convert-sub)
       (remove empty?)
       (mapcat identity)
       (map insert-sub)
       (count)
       )
    

  (pg/exec pg/dbc "select user_id, favs from meteobot_favs" nil)

  )