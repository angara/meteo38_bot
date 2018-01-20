
(ns meteo.commands
  (:require
    [clojure.string :refer [trim lower-case] :as s]
    [clj-time.core :as tc]
    [mount.core :refer [defstate]]
    ;
    [mlib.conf :refer [conf]]
    [mlib.log :refer [warn]]
    [mlib.tlg.core :as tg]
    ;
    [meteo.db :refer [st-near st-by-id st-find]]
    [meteo.util :refer
      [apikey cid inkb q-st-alive
       main-buttons locat-ll default-locat default-favs]]
    [meteo.data :refer
      [ sess-params sess-save
        get-favs favs-add! favs-del!
        start-user user-track]]
    [meteo.menu :refer [cmd-menu]]
    [meteo.subs :refer [cmd-adds cmd-subs on-sbed]]
    [meteo.stform :refer [format-st]]))
;



(defn cmd-help [msg par]

  (let [chat    (cid msg)
        user_id (-> msg :from :id)]  

    (start-user user_id
      { :start {:ts (tc/now) :param par}
        :from (:from msg)})

    (tg/send-message apikey chat
      { :text
          (str
            "@meteo38bot - информация с сети автоматических метеостанций онлайн,"
            " рассылка уведомлений в указаное время.\n\n"
            "По кнопке *Погода* выводятся последние данные с выбранных метеостанций."
            " Кнопка *Рядом* использует функцию геолокации для поиска ближайших станций."
            " В разделе *Меню* настройки списка избранных станций и управление рассылками."
            " Для поиска станции по названию или адресу отправьте текст.\n\n"
            "Пожелания и сообщения об ошибках пишите в группу - https://telegram.me/meteo38")
        :parse_mode "Markdown"
        :reply_markup (when (= chat user_id) main-buttons)})))
;


(defn cmd-start [msg par]
  ;; telegram.me/bot_name?startgroup=...
  ; (start-user
  ;   (-> msg :from :id)
  ;   { :start {:ts (tc/now) :group par}
  ;     :forn (:from msg)})
  (cmd-help msg par))
;

(defn st-kbd [st-id fav? more?]
  (let [k-fav
          { :text (if fav? "В избранном" "( + )")
            :callback_data
              (str (if fav? "favs-del " "favs-add ") st-id " " (inkb))}
        k-more
          { :text "Еще ..."
            :callback_data (str "more " st-id)}]
    {:reply_markup
      {:inline_keyboard
        (if more?
          [[k-fav k-more]]
          [[k-fav]])}}))
;

(defn has-more? [cid]
  (seq (:sts (sess-params cid))))

(defn next-st [cid]
  (when-let [sts (:sts (sess-params cid))]
    (let [favs (get-favs cid)
          st   (first sts)
          st   (if (map? st) st (st-by-id st))
          id   (:_id st)
          tail (next sts)
          kbd  (st-kbd id (some #{id} favs) (seq tail))
          par  {:text (format-st st)
                :parse_mode "Markdown"
                :disable_web_page_preview true}]
      (sess-save cid {:sts tail})
      (tg/send-message apikey cid (merge par kbd)))))
;


(defn cmd-all [msg par]
  (let [cid (cid msg)
        ids (map :_id (st-find (q-st-alive) [:_id]))]
    (when (seq ids)
      (tg/send-text apikey cid "Все станции:")
      (sess-save cid {:sts ids})
      (next-st cid))))
;

(defn cmd-near [msg par]
  (let [cid (cid msg)
        locat (:locat (sess-params cid) (default-locat))
        sts   (st-near (locat-ll locat) (q-st-alive))]
    (when (seq sts)
      (tg/send-text apikey cid "Ближайшие:")
      (sess-save cid {:sts sts})
      (next-st cid))))
;

(defn cmd-favs [msg]
  (let [cid (cid msg)
        favs (not-empty (get-favs cid))]
    (sess-save cid {:sts favs})
    (if favs
      (do
        (tg/send-text apikey cid
          (str "В избранном (" (count favs) "):"))
        (next-st cid))
      (tg/send-text apikey cid "Нет выбранных станций."))))
;

(defn cmd-show [msg]
  (let [cid (cid msg)
        favs (or (not-empty (get-favs cid)) (default-favs))]
    (doseq [f favs]
      (when-let [st (st-by-id f)]
        (tg/send-message apikey cid
          { :text (format-st st)
            :parse_mode "Markdown"
            :disable_web_page_preview true})))))
;

(defn st-search [msg txt]
  (let [cid (cid msg)
        fnm (fn [stn]
                (let [nm (-> stn :title str lower-case)
                      ad (-> stn :addr  str lower-case)
                      ds (-> stn :descr str lower-case)]
                  (or
                    (.contains nm txt)
                    (.contains ad txt)
                    (.contains ds txt))))
        locat  (:locat (sess-params cid) (default-locat))
        sts (filter fnm (st-near (locat-ll locat) (q-st-alive)))]
    (if (seq sts)
      (do
        (sess-save cid {:sts sts})
        (next-st cid))
      (tg/send-text apikey cid "Станции не найдены.\n/help" true))))
;

(defn cmd-find [msg par]
  (if (<= 3 (count par))
    (st-search msg par)
    (tg/send-text apikey (cid msg) 
      (str
        "Для поиска станции введите не менее трех символов.\n"
        "Например: /find Байкал"))))
;

(defn parse-command [text]
  (when-let [match (re-matches #"^/([A-Za-z0-9]+)(@[A-Za-z0-9]+)?([ _]+(.+))?$" text)]
    [(second match) (get match 4)]))
;

(defn on-message [msg]
  (let [cid (cid msg)
        text (-> msg :text str trim not-empty)
        [cmd par] (when text (parse-command text))
        locat (:location msg)]
    (cond
      cmd
        (condp = (lower-case cmd)
          "start" (cmd-start msg par)
          "find"  (cmd-find  msg par)
          "help"  (cmd-help  msg par)
          "all"   (cmd-all   msg par)
          "near"  (cmd-near  msg par)
          "favs"  (cmd-favs  msg)
          "subs"  (cmd-subs  msg par)
          "adds"  (cmd-adds  msg par)
                  (cmd-help  msg nil))
      text
        (let [txt (lower-case text)]
          (cond
            (= "погода" txt) (cmd-show msg)
            (= "меню"   txt) (cmd-menu msg nil)
            :else  (if (<= 3 (.length text))
                      (st-search msg txt)
                      (cmd-help msg nil))))
      locat
        (do
          (sess-save cid {:locat locat})
          (user-track (-> msg :from :id) (locat-ll locat))
          (cmd-near msg nil))
      :else
        nil)))
;

(defn on-callback [cbq]
  (let [msg (:message cbq)
        cid (cid msg)
        [cmd par & params] (-> cbq :data str (s/split #"\s+"))]
    (when-not
      (condp = cmd
        "favs-add"    ;; TODO: limit favs num
                  (do
                    (favs-add! cid par)
                    (tg/api apikey :editMessageReplyMarkup
                      (merge
                        {:chat_id cid :message_id (:message_id msg)}
                        (st-kbd par true (has-more? cid)))))    ;; check is added
        "favs-del"
                  (do
                    (favs-del! cid par)
                    (tg/api apikey :editMessageReplyMarkup
                      (merge
                        {:chat_id cid :message_id (:message_id msg)}
                        (st-kbd par false (has-more? cid)))))
        "more" (do (next-st cid) nil)
        "all"  (do (cmd-all  msg nil) nil)
        "favs" (do (cmd-favs msg) nil)
        "subs" (do (cmd-subs msg nil) nil)
        "adds" (do (cmd-adds msg nil) nil)
        "sbed" (do (on-sbed  msg par params) nil)
        (warn "cbq-unexpected:" cmd))
      ;
      (tg/api apikey :answerCallbackQuery
        {:callback_query_id (:id cbq) :text ""}))))
;

;;.
