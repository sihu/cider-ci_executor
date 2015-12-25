; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.attachments
  (:require
    [cider-ci.ex.utils.include-exclude :as in-ex]
    [cider-ci.ex.trials.helper :refer :all]
    [cider-ci.utils.http :refer [build-server-url]]

    [cider-ci.utils.map :as map :refer [deep-merge convert-to-array]]
    [cider-ci.utils.http :as http]

    [logbug.debug :as debug]
    [logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clj-time.format :as time-format]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [me.raynes.fs :as fs]
    )
  (:import
    [java.io File]
    [java.nio.file Files FileSystems Path Paths]
    )
  )

(defn- put-file [file working-dir base-url content-type]
  (catcher/wrap-with-log-error
    (let [url (str base-url file)]
      (logging/debug "putting attachment"
                     {:file file :url url})
      (http/put url {:body (clojure.java.io/file (str working-dir "/" file))
                     :content-type content-type})
      )))


;### path #####################################################################

(defn nio-path [s]
  (.getPath (FileSystems/getDefault)
            s (make-array String 0)))


(defn put-attachments [dir-str in-ex-matcher trial]
  (let [dir-path (-> dir-str nio-path)]
    (->>(-> dir-str clojure.java.io/file file-seq)
            (map #(.toPath %))
            (filter #(Files/isRegularFile % (make-array java.nio.file.LinkOption 0)))
            (map #(.relativize dir-path %))
            (map #(.toString %))
            (filter #(in-ex/passes? % in-ex-matcher))
            )))

(defn- matching-paths-seq [dir-str in-ex-matcher]
  (let [dir-path (-> dir-str nio-path)]
    (->>(-> dir-str clojure.java.io/file file-seq)
            (map #(.toPath %))
            (filter #(Files/isRegularFile % (make-array java.nio.file.LinkOption 0)))
            (map #(.relativize dir-path %))
            (map #(.toString %))
            (filter #(in-ex/passes? % in-ex-matcher))
            )))


;(matching-paths-seq (System/getProperty "user.dir") {:include-match "log$"} )


(defn find-and-upload [trial]
  (let [params (-> trial get-params-atom deref)
        working-dir (get-working-dir trial)]
    (doseq [kind ["tree" "trial"]]
      (let [base-url (build-server-url ((keyword (str kind "-attachments-path")) params))]
        (when-let [matchers ((keyword (str kind "-attachments")) params)]
          (logging/info {:matchers matchers})
          (doseq [matcher (convert-to-array matchers)]
            (let [content-type (:content-type matcher)]
              (logging/info {:matcher matcher})
              (when-not (:include-match matcher)
                (throw (ex-info (str "An attachment matcher must include "
                                     "the 'include-match' directive'.") {:matcher matcher})))
              (doseq [path-str (matching-paths-seq working-dir matcher)]
                (logging/info "Attaching" path-str "for"  kind)
                (put-file path-str working-dir base-url content-type)
                ))))))))

;(debug/re-apply-last-argument #'find-and-upload)


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :info)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
