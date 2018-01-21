
(ns meteo.subs
  (:require
    [clojure.string :as s]
    [clj-time.core :as tc]
    [clj-time.local :refer [local-now]]
    ;
    [mlib.log :refer [debug]]
    [mlib.core :refer [to-int]]
    [mlib.time :refer [hhmm]]
    [mlib.tlg.core :as tg]
    ;
    [meteo.db :refer [st-ids]]
    [meteo.util :refer [apikey cid gmaps-link inkb wd-map]]
    [meteo.data :refer
      [get-favs get-subs subs-add! subs-update! subs-remove!]]))
;


(def SUBS-MAX 20)

;; {_id, ts, cid:cid, ord:ord,
;;   time:"16:45", days:"01233456", ids:["uiii","npsd",...] }


(defn week2 [days]
  (let [ds (set days)]
    (s/join " "
      (for [d "1234560"]
        (if (ds d) (wd-map d) " -- ")))))
;

(defn kbd [{:keys [ord time days del]}]
  (let [cmd (str "sbed " ord " ")
        ds (set days)
        wd2 (fn [d] (if (ds d) (wd-map d) "--"))]
    {:inline_keyboard
      [
        [(if del
          {:text "Удалить" :callback_data (str cmd "remove "(inkb))}
          {:text time :callback_data (str cmd "del " (inkb))})]
        [
          {:text "<<" :callback_data (str cmd "h-")}
          {:text "<"  :callback_data (str cmd "m-")}
          {:text ">"  :callback_data (str cmd "m+")}
          {:text ">>" :callback_data (str cmd "h+")}]
        (for [d "1234560"]
          {:text (wd2 d) :callback_data (str cmd "d " d)})]}))
;


(defn sub-kbd [msg cid ord]
  (when-let [sb (first (get-subs cid ord))]
    (tg/api apikey :editMessageReplyMarkup
      { :chat_id cid
        :message_id (:message_id msg)
        :reply_markup (kbd sb)})))
;

(defn show-sub [cid ord]
  (when-let [sb (first (get-subs cid ord))]
    (let [sts (st-ids (:ids sb) [:title])]
      (tg/send-message apikey cid
        { :text (str (s/join ", " (map :title sts)) ".")
          :parse_mode "Markdown"
          :reply_markup (kbd sb)}))))
;

(defn change-time [sb h m]
  (let [[hh mm] (s/split (-> sb :time str) #":")
        hh (mod (+ (to-int hh 0) h 24) 24)
        mm (mod (+ (to-int mm 0) m 60) 60)]
    (format "%02d:%02d" hh mm)))
;

(defn change-days [sb d]
  (let [days (:days sb)
        dset (set d)]
    (s/join ""
      (if (some dset days)
        (remove dset days)
        (concat days d)))))
;

(declare cmd-subs)

(defn on-sbed [msg par params]
  (let [cid (cid msg)
        ord (to-int par)
        cmd (first params)]
    (when-let [sb (first (get-subs cid ord))]
      (condp = cmd
        "show"  (show-sub cid ord)
        "h-"    (when-let [t (change-time sb -1 0)]
                  (subs-update! cid ord {:del false :time t})
                  (sub-kbd msg cid ord))
        "h+"   (when-let [t (change-time sb 1 0)]
                  (subs-update! cid ord {:del false :time t})
                  (sub-kbd msg cid ord))
        "m-"   (when-let [t (change-time sb 0 -5)]
                  (subs-update! cid ord {:del false :time t})
                  (sub-kbd msg cid ord))
        "m+"   (when-let [t (change-time sb 0 5)]
                  (subs-update! cid ord {:del false :time t})
                  (sub-kbd msg cid ord))
        "d"     (when-let [days (change-days sb (second params))]
                  (subs-update! cid ord {:del false :days days})
                  (sub-kbd msg cid ord))
        "del"   (do
                  (subs-update! cid ord {:del true})
                  (sub-kbd msg cid ord))
        "remove"
                (do
                  (subs-remove! cid ord)
                  (tg/send-text apikey cid "Рассылка удалена.")
                  (cmd-subs msg nil))
        ;
        (debug "sbed: !cmd" cmd)))))
;

(defn cmd-subs [msg par]
  (let [cid (cid msg)
        subs (get-subs cid)
        kbd (for [s subs]
              [{:text (str (:time s) " \u00A0 " (week2 (:days s)))
                :callback_data (str "sbed " (:ord s) " show")}])]
    (tg/send-message apikey cid
      { :text "Рассылки:"
        :reply_markup
          {:inline_keyboard
            (if (> SUBS-MAX (count subs))
              (conj (vec kbd) [{:text "Добавить" :callback_data "adds"}])
              kbd)}})))
;

(defn cmd-adds [msg par]
  (let [cid (cid msg)
        favs (not-empty (get-favs cid))
        subs (not-empty (get-subs cid))]
    (cond
      (not favs)
      (tg/send-text apikey cid "Ничего нет в Избранном!")

      (<= SUBS-MAX (count subs))
      (tg/send-text apikey cid "Больше рассылок добавить нельзя!")

      :else
      (let [ord (inc (apply max (cons 0 (map :ord subs))))
            hrs (format "%02d" (tc/hour (local-now)))]
        (subs-add! cid ord (str hrs ":00") "0123456" favs)
        (show-sub cid ord)))))
;

;;.
