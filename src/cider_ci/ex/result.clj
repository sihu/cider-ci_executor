; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.result
  (:import 
    [java.io File]
    [java.util UUID]
    [org.apache.commons.exec ExecuteWatchdog]
    )
  (:require
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.with :as with]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [me.raynes.fs :as fs]
    [clojure.data.json :as json]
    ))



(defn file-path-to-result [working-dir]
  (-> 
    (clojure.java.io/file working-dir)
    (fs/absolute)
    (fs/normalized)
    (str "/result.json")))

(defn try-read-and-merge [working-dir params-atom]
  (with/suppress-and-log-warn  
    (let [json-data (-> (file-path-to-result working-dir)
                        slurp
                        json/read-str)]
      (swap! params-atom 
             #(assoc %1 :result %2)
             json-data))
    params-atom))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)