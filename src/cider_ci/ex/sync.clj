; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
;

(ns cider-ci.ex.sync
  (:refer-clojure :exclude [sync])
  (:require
    [cider-ci.ex.accepted-repositories :as accepted-repositories]
    [cider-ci.ex.scripts.processor.abort :as scripts-abort]
    [cider-ci.ex.self-update :refer [self-update!]]
    [cider-ci.ex.traits :as traits]
    [cider-ci.ex.trials :as trials]
    [cider-ci.ex.trials.state :as trials.state]
    [cider-ci.ex.utils :refer [terminal-states]]

    [cider-ci.utils.config :refer [get-config parse-config-duration-to-seconds]]
    [cider-ci.utils.daemon :refer [defdaemon]]
    [cider-ci.utils.http :as http :refer [build-service-url]]
    [cider-ci.utils.core :refer :all]
    [cider-ci.utils.runtime :as runtime]
    [cider-ci.self ]

    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.tools.logging :as logging]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.ring :refer [wrap-handler-with-logging]]
    ))

(defn- unfinished-trials-count []
  (->> (trials.state/get-trials-properties)
       (filter #(-> % :state ((terminal-states)) boolean not))
       count))

(defn- execute-trials [trials]
  (doseq [trial trials]
    (catcher/snatch {}
                    (trials/execute trial))))

(defn- terminate-aborting [trials]
  (->> trials
       (filter #(= "aborting" (:state %)))
       (map #(trials.state/get-trial (:id %)))
       (filter identity)
       (map scripts-abort/abort)
       doall))

(defn- get-trials []
  (->> (trials.state/get-trials-properties)
       (map #(select-keys % [:trial_id :started_at :finished_at :state]))))

(defn sync []
  (catcher/snatch
    {}
    (let [config (get-config)
          url (build-service-url :dispatcher "/sync")
          traits (into [] (traits/get-traits))
          max-load (or (:max_load config)
                       (.availableProcessors(Runtime/getRuntime)))
          data {:traits traits
                :max_load max-load
                :temporary_overload_factor (or (:temporary_overload_factor config)
                                               1.5)
                :accepted_repositories (accepted-repositories/get-accepted-repositories)
                :available_load (- max-load (unfinished-trials-count))
                :trials (get-trials)
                :status {:memory (runtime/check-memory-usage)
                         :version cider-ci.self/VERSION}}]
      (let [response (http/post url {:body (json/write-str data)})
            body (json/read-str (:body response) :key-fn keyword)]
        (execute-trials (:trials-to-be-executed body))
        (terminate-aborting (->> body :trials-being-processed))
        (self-update! body)
        ))))

(defn- sync-interval-pause-duration []
  (or (catcher/snatch {}
                      (parse-config-duration-to-seconds :sync_interval_pause_duration))
      3))

(defn initialize []
  (defdaemon "sync" (sync-interval-pause-duration) (sync))
  (start-sync)
  )

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(debug/debug-ns *ns*)

