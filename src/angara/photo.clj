
(ns angara.photo
  (:require
    [taoensso.timbre :refer [debug info warn]]
    [clj-time.core :as tc]
    [mlib.conf :refer [conf]]
    [mlib.core :refer [to-int byte-array-hash]]
    [mlib.tlg.core :refer [send-text get-file]]
    [angara.db :refer [last-loc]]
    [angara.util :refer [chat-creds]]))
;

(comment
  (def apikey "174879986:AAHwiXbfHTRrVwczWPRNW1xtxeJjVSbnEeA")
  (def file-id "AgADAgADkKgxG4ShBQAB-ELVmnHFvEKD5oENAARDxx-Oc6WV3wVdBAABAg")
  (def file-path "photo/file_4.jpg")

  (map #(format "%02x" %)
    (byte-array-hash "sha1"
      (get-file apikey file-id)))

  (mlib.tlg.core/file-path apikey file-id))
;


(def FRESH_LOC (tc/minutes 30))

(defn save-photo-thumbs [file]
  (let [hash (byte-array-hash "sha1" file)]))
    ;; conf photo-base + xx/yy / hash
    ;; save orig
    ;; make thumb 320
    ;; make thumb 90
    ;; get exif

    ;; save to db "abot_photos"
    ;; { uid cid }
;

(defn insert-photo-rec [msg hash]
  (try
    (do)
    (catch Exception e
      (warn "insert-photo-rec:" msg e))))
;


(defn big-photo-id [pho]
  (let [w+h (fn [p]
              (+ (-> p :with (to-int 0)) (-> p :height (to-int 0))))]
    (:file_id (first (sort-by w+h > photo)))))
;

(defn handle-photo [msg photo]
  (let [[apikey cid] (chat-creds msg)
        uid (-> msg :from :id)
        orig-id (big-photo-id photo)]
    (when-let [loc (last-loc cid uid (tc/minus (tc/now) FRESH_LOC))]
      (if-let [file (get-file apikey orig-id)]
        (send-text apikey cid
          (str "orig:" orig " " (count file)))
        ;
        (warn "handle-photo - unable to get photo:" msg)))))
;

;;.
