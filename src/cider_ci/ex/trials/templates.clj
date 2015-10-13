; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.trials.templates
  (:require
    [cider-ci.ex.environment-variables]
    [cider-ci.ex.trials.helper :refer :all]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    [drtom.logbug.thrown :as thrown]
    [selmer.parser :refer [render]]
    [clojure.string :refer [join]]
    )
  (:import
    [java.io File]
    ))



(defn- render-string-template [s env-vars]
  (let [rendered (render s env-vars)]
    (if (not= rendered s)
      (render-string-template rendered env-vars)
      rendered)))

(defn- render-file-template
  ([src dest env-vars]
   (let [template (slurp src)
         rendered (render-string-template template env-vars)]
     (spit dest rendered))))

(defn- render-template
  ([template params]
   (let [working-dir (:working_dir params)
         env-vars (-> params
                      cider-ci.ex.environment-variables/prepare
                      clojure.walk/keywordize-keys)
         src (join (File/separator) (flatten [working-dir (:src template)]))
         dest (join (File/separator) (flatten [working-dir (:dest template)]))]
     (render-file-template src dest env-vars)
     )))

(defn render-templates [trial]
  (let [trial-params (-> trial :params-atom deref)]
    (doseq [template (:templates trial-params)]
      (render-template template trial-params))
    trial))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)