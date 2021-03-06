;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns com.grzm.aws.client.api.async
  "API functions for using a client to interact with AWS services."
  (:require [clojure.core.async :as a]
            [com.grzm.aws.client :as client]
            [com.grzm.aws.retry :as retry]
            [com.grzm.aws.service :as service]))

(set! *warn-on-reflection* true)

(defn ^:skip-wiki validate-requests?
  "For internal use. Don't call directly."
  [client]
  (some-> client client/-get-info :validate-requests? deref))

(defn ^:skip-wiki validate-requests
  "For internal use. Don't call directly."
  [client tf]
  (reset! (-> client client/-get-info :validate-requests?) tf)
  (when tf
    (service/load-specs (-> client client/-get-info :service)))
  tf)

(def ^:private registry-ref (delay (requiring-resolve 'clojure.spec.alpha/registry)))
(defn ^:skip-wiki registry
  "For internal use. Don't call directly."
  [& args] (apply @registry-ref args))

(def ^:private valid?-ref (delay (requiring-resolve 'clojure.spec.alpha/valid?)))
(defn ^:skip-wiki valid?
  "For internal use. Don't call directly."
  [& args] (apply @valid?-ref args))

(def ^:private explain-data-ref (delay (requiring-resolve 'clojure.spec.alpha/explain-data)))
(defn ^:skip-wiki explain-data
  "For internal use. Don't call directly."
  [& args] (apply @explain-data-ref args))

(defn ^:skip-wiki validate
  "For internal use. Don't call directly."
  [service {:keys [op request] :or {request {}}}]
  (let [spec (service/request-spec-key service op)]
    (when (contains? (-> (registry) keys set) spec)
      (when-not (valid? spec request)
        (assoc (explain-data spec request)
               :cognitect.anomalies/category :cognitect.anomalies/incorrect)))))

(defn invoke
  "Async version of cognitect.aws.client.api/invoke. Returns
  a core.async channel which delivers the result.

  Additional supported keys in op-map:

  :ch - optional, channel to deliver the result

  Alpha. Subject to change."
  [client op-map]
  (let [result-chan                          (or (:ch op-map) (a/promise-chan))
        {:keys [service retriable? backoff]} (client/-get-info client)
        validation-error                     (and (validate-requests? client)
                                                  (validate service op-map))]
    (when-not (contains? (:operations service) (:op op-map))
      (throw (ex-info "Operation not supported" {:service   (keyword (service/service-name service))
                                                 :operation (:op op-map)})))
    (if validation-error
      (a/put! result-chan validation-error)
      (retry/with-retry
        #(client/send-request client op-map)
        result-chan
        (or (:retriable? op-map) retriable?)
        (or (:backoff op-map) backoff)))
    result-chan))
