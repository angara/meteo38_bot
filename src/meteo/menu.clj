(ns meteo.menu
  (:require
    [clojure.string :as s]
    [mlib.tlg.core :as tg]
    [meteo.util :refer [apikey cid wd-map]]
    [meteo.db :refer [st-ids]]
    [meteo.data :refer [get-favs get-subs]]
  ))


(defn sect-favs [ids]
  (when-let [sts (not-empty (st-ids ids [:_id :title :ll]))]
    (str "В избранном:\n"
      (s/join ", " (map #(str "*" (:title %) "*") sts))
      ".\n")))
;

(defn wdf [ddays]
  (s/join "," (map wd-map (filter (set ddays) "1234560"))))
;

(defn sect-subs [sbs]
  (when sbs
    (str "Рассылки:\n"
      (s/join "\n"
        (map #(str " *" (:time %) "* -- " (-> % :days wdf)) sbs))
      "\n")))
;

(defn cmd-menu [msg _par]
  (let [cid (cid msg)
        ids (get-favs cid)
        sc  (count (get-subs cid))]
    (tg/send-message apikey cid
      { :text (or
                (sect-favs ids)
                "Для добавления рассылки добавьте в Избранное интересующие станции.")

        :parse_mode "Markdown"
        :reply_markup
          {:inline_keyboard
            [
             [{:text "Все станции" :callback_data "all"}
              {:text "Избранное"   :callback_data "favs"}]
             [{:text (str "Рассылки (" sc ")") :callback_data "subs"}]]}})))
              ; {:text "Добавить"    :callback_data "adds"}]]}})))
;
