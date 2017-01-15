
(ns angara.photo
  (:require
    [taoensso.timbre :refer [debug info warn]]
    [mlib.conf :refer [conf]]
    [mlib.core :refer [to-int byte-array-hash]]
    [mlib.tlg.core :refer [send-text get-file]]
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

(defn handle-photo [msg photo]
  (let [[apikey cid] (chat-creds msg)
        w+h (fn [p] (+
                      (-> p :with (to-int 0))
                      (-> p :height (to-int 0))))
        orig (first (sort-by w+h > photo))]
    (if-let [file (get-file apikey (:file_id orig))]
      (send-text apikey cid
        (str "orig:" orig " " (count file)))
      ;
      (warn "handle-photo - unable to get photo:" msg))))
;

;;.
