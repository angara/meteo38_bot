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
  {\0 "вс" \1 "пн" \2 "вт" \3 "ср" \4 "чт" \5 "пт" \6 "сб"})


(def DIGITS_MAP
  {\0 "0️⃣" \1 "1️⃣" \2 "2️⃣" \3 "3️⃣" \4 "4️⃣" 
   \5 "5️⃣" \6 "6️⃣" \7 "7️⃣" \8 "8️⃣" \9 "9️⃣"
   })

;; ⏩ ⏪ ◀ ▶️ ⬅️ ➡️
;; ✅❌🚫❎

(def tf-hhmm (jt/formatter "HH:mm"))

(def tf-hmm (jt/formatter "H:mm"))


(defn hhmm->big [hhmm]
  (->> hhmm (jt/format tf-hhmm) (map #(or (DIGITS_MAP %) %)) (str/join "")))


(defn fmt-title [title]
  (str "🔹 <b>" (botapi/hesc title) "</b>\n"))


(defn fmt-wdays [wds]
  (when (and wds (not= wds "0123456"))
    (->> wds (map WDS_MAP) (str/join ","))
    ))


(defn msg-subs [subs-id st-info hhmm wdays]
  (let [title (:title st-info)
        tt (hhmm->big hhmm)
        wd-set (set wdays)
        wd-kbd (for [c "1234560"]
                 {:text (if (wd-set c) (WDS_MAP c) "-")
                  :callback_data (str "subs:" subs-id ":wd:" c)})
        ]
    ;; NOTE (title st-info)
    {:text (str "🔹 <b>" (botapi/hesc title) "</b>\n\n"  
                "⏰ Время рассылки - " tt
                )
     :parse_mode "HTML"
     :reply_markup {:inline_keyboard
                    [wd-kbd
                     [{:text "⏪" :callback_data (str "subs:" subs-id ":hh-")}
                      {:text "◀"  :callback_data (str "subs:" subs-id ":mm-")}
                      {:text "▶️"  :callback_data (str "subs:" subs-id ":mm+")}
                      {:text "⏩" :callback_data (str "subs:" subs-id ":hh+")}]
                     [{:text "✅" :callback_data (str "subs:" subs-id ":ok")}
                      {:text "❌" :callback_data (str "subs:" subs-id ":del:" (next-seq))}]]}})
  )


(defn cmd-subs [cfg {{chat-id :id} :chat} _]
  (let [subs-list (store/user-subs chat-id)
        
        txts (->> subs-list
                  (map (fn [sb]
                         (let [st-info (store/station-info (:st sb))
                               hhmm (->> sb :hhmm (jt/format tf-hhmm))
                               wds (fmt-wdays (:wdays sb))
                               ]
                           (str (fmt-title (:title st-info))
                                "⏰ " hhmm  (if wds (str " - " wds "\n") "  ") 
                                "️✏️ " "/sub_" (:subs_id sb)
                                "\n"
                                )
                           )
                         
                         ))
                  )
        
        msg {:text (str/join "\n" txts)
             :parse_mode "HTML"
             }
        ]
    
    (botapi/send-message cfg chat-id msg)
    ,))


(defn cmd-subs-edit [cfg {{chat-id :id} :chat} {param :param}]
  (when-let [subs (store/user-subs-by-id chat-id (parse-long (or param "")))]
    (let [st-info (store/station-info (:st subs))
          msg (msg-subs (:subs_id subs) st-info (:hhmm subs) (:wdays subs))]
      (botapi/send-message cfg chat-id msg))
    ))


;; {{{chat-id :id} :chat msg-id :message_id} :message :as msg}

(defn cb-subs-new [cfg
                   {{{chat-id :id} :chat msg-id :message_id} :message  cbk-id :id :as msg}
                   [_ st]]
  (when-let [st-info (store/station-info st)]
    (let [all-subs (store/user-subs chat-id)]
      (if (< (count all-subs) SUBS_MAX)
        (let [hhmm (-> (jt/local-time) (jt/truncate-to :hours))
              wdays "0123456"
              subs (store/user-subs-create chat-id {:hhmm hhmm :st st :wdays wdays})
              msg (msg-subs (:subs_id subs) st-info hhmm wdays)
              ]
          (botapi/answer-callback-text cfg cbk-id "")
          (botapi/send-message cfg chat-id msg)
        ;;
          )
      ;;
        (botapi/answer-callback-text cfg cbk-id (str "Максимальное количество подписок: " SUBS_MAX))
        ))))


(defn cb-subs [cfg
               {{{chat-id :id} :chat msg-id :message_id} :message :as cbk}
               [_ subs-id cmd wd]]

  (log! ["cb-subs:" subs-id cmd wd])

  (when-let [subs (store/user-subs-by-id chat-id (parse-long subs-id))]
    (let [update-msg 
          (case cmd
            "hh-" true
            "hh+" true 
            "mm-" true
            "mm+" true

            "wd" true

            "ok"  (do
                    (botapi/delete-message cfg chat-id msg-id)
                    false)
            "del" (do
                    (store/user-subs-delete chat-id (:subs_id subs))
                    (botapi/delete-message cfg chat-id msg-id)
                    false)
            (do
              (log! :warn ["cb-subs: unexpected cmd" cbk])
              nil))]
      (when update-msg
        (let [st-info (store/station-info (:st subs))
              msg (msg-subs (:subs_id subs) st-info (:hhmm subs) (:wdays subs))]
          (botapi/edit-message cfg chat-id msg-id msg)
          ,)))))


(comment
  
  #_(store/user-subs-add 1 {:hhmm (jt/truncate-to (jt/local-time) :hours) 
                          :wdays "0123456" 
                          :st "uiii"})
  ;;=> {:hhmm #object[java.time.LocalTime 0x25ed1a9f "22:00"],
  ;;    :wdays "0123456",
  ;;    :ts #object[java.time.OffsetDateTime 0x7018f036 "2025-02-02T22:40:32.696608+08:00"],
  ;;    :subs_id 5,
  ;;    :st "uiii",
  ;;    :user_id 1}
      
  ,)
