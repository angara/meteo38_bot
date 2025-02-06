(ns meteobot.app.subs
  (:require
   [clojure.string :as str]
   [java-time.api :as jt]
   [taoensso.telemere :refer [log!]]
   [mlib.telegram.botapi :as botapi]
   [meteobot.data.store :as store]
   [meteobot.app.fmt :refer [next-seq]]
   ,))


(def ^:const SUBS_MAX 10)


(def WDS_MAP
  {\1 "пн" \2 "вт" \3 "ср" \4 "чт" \5 "пт" \6 "сб" \7 "вс"})


(def DIGITS_MAP
  {\0 "0️⃣" \1 "1️⃣" \2 "2️⃣" \3 "3️⃣" \4 "4️⃣" \5 "5️⃣" \6 "6️⃣" \7 "7️⃣" \8 "8️⃣" \9 "9️⃣"})


; 0️⃣1️⃣2️⃣3️⃣4️⃣5️⃣6️⃣7️⃣8️⃣9️⃣

;; ⏩ ⏪ ◀ ▶️ ⬅️ ➡️
;; ✅❌🚫❎


(def tf-hhmm (jt/formatter "HH:mm"))


(defn hhmm->big [hhmm]
  (->> hhmm (jt/format tf-hhmm) (map #(or (DIGITS_MAP %) %)) (str/join "")))


(defn fmt-title [title]
  (str "🔹 <b>" (botapi/hesc title) "</b>\n"))


(defn fmt-wdays [wds]
  (when (seq wds)
    (let [wset (set wds)]
      (str "["
           (->> "1234567"
                (filter wset)
                (map WDS_MAP)
                (str/join ", "))
           "]")   
      ,)))

(comment
  (fmt-wdays "")
  ;;=> nil
  (fmt-wdays "0145")
  ;;=> "[пн, чт, пт, вс]"
  (fmt-wdays "21") 
  ;;=> "[пн, вт]"
  )


(defn msg-subs [subs-id st-info hhmm wdays]
  (let [title (:title st-info)
        tt (hhmm->big hhmm)
        wd-set (set wdays)
        wd-kbd (for [c "1234567"]
                 {:text (if (wd-set c) (WDS_MAP c) "--")
                  :callback_data (str "subs:" subs-id ":wd:" c)})
        ]
    ;; NOTE (title st-info)
    {:text (str "🔹 <b>" (botapi/hesc title) "</b>\n\n"  
                "⏰ Время рассылки - " tt
                )
     :parse_mode "HTML"
     :reply_markup {:inline_keyboard
                    [wd-kbd
                     [{:text #_"⏪" "-1 ч" :callback_data (str "subs:" subs-id ":hh-")}
                      {:text #_"◀" "-10 м"  :callback_data (str "subs:" subs-id ":mm-")}
                      {:text #_"▶️" "+10 м" :callback_data (str "subs:" subs-id ":mm+")}
                      {:text #_"⏩" "+1 ч" :callback_data (str "subs:" subs-id ":hh+")}]
                     [{:text "✅" :callback_data (str "subs:" subs-id ":ok")}
                      {:text "❌" :callback_data (str "subs:" subs-id ":del:" (next-seq))}]]}})
  )


(defn cmd-subs [cfg {{chat-id :id} :chat} _]
  (if-let [subs-list (seq (store/user-subs chat-id))]
    (let [txts (->> subs-list
                    (map (fn [sb]
                           (let [st-info (store/station-info (:st sb))
                                 hhmm (->> sb :hhmm (jt/format tf-hhmm))
                                 wds (fmt-wdays (:wdays sb))]
                             (str (fmt-title (:title st-info))
                                  "⏰ " hhmm  (if wds (str " - " wds "\n") "  ")
                                  "️✏️ " "/sub_" (:subs_id sb)
                                  "\n")))))]
      (botapi/send-html cfg chat-id (str/join "\n" txts))
      ,)
    (botapi/send-html cfg chat-id 
                      (str "🔸 Чтобы настроить рассылку\n\n"
                           "- зайдите в список станций /active или /near\n"
                           "- перейдите на информацию о станции по ссылке 'ℹ️ info_'\n"
                           "- добавьте рассылку кнопкой ⏰\n"
                           "- установите время и дни недели"
                           ))
    ,))


(defn cmd-subs-edit [cfg {{chat-id :id} :chat} {param :param}]
  (when-let [subs (store/user-subs-by-id chat-id (parse-long (or param "")))]
    (let [st-info (store/station-info (:st subs))
          msg (msg-subs (:subs_id subs) st-info (:hhmm subs) (:wdays subs))]
      (botapi/send-message cfg chat-id msg))
    ))


;; {{{chat-id :id} :chat msg-id :message_id} :message :as msg}

(defn cb-subs-new [cfg  
                   {{{chat-id :id} :chat} :message cbk-id :id} 
                   [_ st]]
  (when-let [st-info (store/station-info st)]
    (let [all-subs (store/user-subs chat-id)]
      (if (< (count all-subs) SUBS_MAX)
        (let [hhmm (-> (jt/local-time) (jt/truncate-to :hours))
              wdays "1234567"
              subs (store/user-subs-create chat-id {:hhmm hhmm :st st :wdays wdays})
              msg (msg-subs (:subs_id subs) st-info hhmm wdays)
              ]
          (botapi/answer-callback-text cfg cbk-id "")
          (botapi/send-message cfg chat-id msg)
        ;;
          )
      ;;
        (botapi/answer-callback-text cfg cbk-id (str "Максимальное количество рассылок: " SUBS_MAX))
        ))))


(defn- wd-toggle [wdays wd]
  (let [c (first wd)
        ws (set wdays)
        wds (if (ws c) (disj ws c) (conj ws c))]
    (apply str (sort wds))))

(comment
  (wd-toggle "1234" "1")
  ;;=> "234"
  (wd-toggle "1234" "7")
  ;;=> "12347"
  (wd-toggle "4321" "2")
  ;;=> "134"
  ,)


(defn cb-subs [cfg
               {{{chat-id :id} :chat msg-id :message_id} :message :as cbk}
               [_ subs-id cmd wd]]
    (when-let [subs (store/user-subs-by-id chat-id (parse-long subs-id))]
    (let [sub-id (:subs_id subs)
          hhmm (:hhmm subs)
          wdays (:wdays subs)
          update-msg 
          (case cmd
            "hh-" (do 
                    (store/user-subs-update chat-id sub-id (jt/- hhmm (jt/hours 1)) wdays)
                    true)
            "hh+" (do
                    (store/user-subs-update chat-id sub-id (jt/+ hhmm (jt/hours 1)) wdays)
                    true)
            "mm-" (do
                    (store/user-subs-update chat-id sub-id (jt/- hhmm (jt/minutes 10)) wdays)
                    true)
            "mm+" (do
                    (store/user-subs-update chat-id sub-id (jt/+ hhmm (jt/minutes 10)) wdays)
                    true)
            "wd" (do 
                   (store/user-subs-update chat-id sub-id hhmm (wd-toggle wdays wd))
                   true)
            "ok"  (do
                    (botapi/delete-message cfg chat-id msg-id)
                    false)
            "del" (do
                    (store/user-subs-delete chat-id sub-id)
                    (botapi/delete-message cfg chat-id msg-id)
                    false)
            (do
              (log! :warn ["cb-subs: unexpected cmd" cbk])
              nil))]
      (when update-msg
        (let [subs (store/user-subs-by-id chat-id sub-id)
              st-info (store/station-info (:st subs))
              msg (msg-subs sub-id st-info (:hhmm subs) (:wdays subs))]
          (botapi/edit-message cfg chat-id msg-id msg)
          ,)))))
