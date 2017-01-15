
(ns angara.util)


(defn chat-creds
  "returns apikey and chat id to use in reply"
  [msg]
  [(-> msg :apikey) (-> msg :chat :id)])
;

(defn from-name [from]
  (when-let [username (:username from)]
    (str "@" username)
    (str (:first_name from) " " (:last_name from))))
;;.
