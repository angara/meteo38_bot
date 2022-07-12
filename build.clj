(ns build
  (:require 
    [clojure.string :refer [trim-newline]]
    [clojure.java.io :as io]
    [clojure.tools.build.api :as b]
  ))


(def APPLICATION 'angara/meteo38_bot)
(def VER_MAJOR 1)
(def VER_MINOR 1)
(def MAIN_CLASS 'meteo38-bot.main)


(def JAVA_SRC   "./java")
(def TARGET     "./target")
(def CLASS_DIR  "./target/classes")
(def RESOURCES  "./resourses")
(def TARGET_RESOURCES "./target/resources")
(def VERSION_FILE "./VERSION")


(defn clean [_]
  (b/delete {:path TARGET}))


(defn write-build-info [app-ver]
  (let [bi-file (io/file TARGET_RESOURCES "build-info")]
    (io/make-parents bi-file)  
    (spit bi-file app-ver)
  ))


(defn version [_] 
  (format "%s.%s.%s" VER_MAJOR VER_MINOR (b/git-count-revs nil)))


(defn write-version [_]
  (let [ver (version nil)]
    (println "version:" ver)
    (spit VERSION_FILE ver)))


;; https://clojure.org/guides/tools_build

(defn javac [{basis :basis}]
  (b/javac {:src-dirs [JAVA_SRC]
            :class-dir CLASS_DIR
            :basis (or basis (b/create-basis {:project "deps.edn"}))
            ; :javac-opts ["-source" "8" "-target" "8"]
           }))


(defn uberjar [_]
  (let [VERSION   (trim-newline (slurp VERSION_FILE))
        UBER_FILE (format "%s/%s.jar" TARGET (name APPLICATION))
        app-ver   (str (name APPLICATION) " v" VERSION)
        basis     (b/create-basis {:project "deps.edn"})
       ]

    (write-build-info app-ver)

    ;; (b/write-pom {:class-dir CLASS_DIR
    ;;               :lib APPLICATION
    ;;               :version VERSION
    ;;               :basis basis
    ;;               :src-dirs ["src"]})

    ; (javac {:basis basis})

    (b/copy-dir {:src-dirs ["src" RESOURCES TARGET_RESOURCES]
                 :target-dir CLASS_DIR})

    (b/compile-clj {:basis basis
                    :src-dirs ["src"]
                    :class-dir CLASS_DIR})

    (b/uber {:class-dir CLASS_DIR
             :uber-file UBER_FILE
             :basis basis
             :main APPLICATION})
  ))
