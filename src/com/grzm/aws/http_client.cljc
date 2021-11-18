;; Copyright (c) Michael Glaesemann
;; Heavily inspired by congitect.http-client, Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns com.grzm.aws.http-client
  (:require
   [clojure.core.async :refer [put!] :as a]
   [com.grzm.aws.http-client.client :as client]
   [com.grzm.aws.http-client.specs])
  (:import
   (clojure.lang ExceptionInfo)
   (java.net URI)
   (java.net.http HttpClient
                  HttpClient$Redirect
                  HttpHeaders
                  HttpRequest
                  HttpRequest$Builder
                  HttpRequest$BodyPublishers
                  HttpResponse
                  HttpResponse$BodyHandlers)
   (java.nio ByteBuffer)
   (java.time Duration)
   (java.util.function Function)))

#?(:bb
   (do (require 'spartan.spec)
       (alias 's 'clojure.spec.alpha))
   :clj (require '[clojure.spec.alpha :as s]))

(set! *warn-on-reflection* true)

(defn submit
  "Submit an http request, channel will be filled with response. Returns ch.

  Request map:

  :server-name        string
  :server-port         integer
  :uri                string
  :query-string       string, optional
  :request-method     :get/:post/:put/:head
  :scheme             :http or :https
  :headers            map from downcased string to string
  :body               ByteBuffer, optional
  :cognitect.http-client/timeout-msec   opt, total request send/receive timeout
  :cognitect.http-client/meta           opt, data to be added to the response map

  content-type must be specified in the headers map
  content-length is derived from the ByteBuffer passed to body

  Response map:

  :status              integer HTTP status code
  :body                ByteBuffer, optional
  :header              map from downcased string to string
  :cognitect.http-client/meta           opt, data from the request

  On error, response map is per cognitect.anomalies"
  ([client request]
   (submit client request (a/chan 1)))
  ([client request ch]
   (s/assert ::submit-request request)
   (client/submit client request ch)))

(def method-string
  {:get "GET"
   :post "POST"
   :put "PUT"
   :head "HEAD"
   :delete "DELETE"
   :path "PATCH"})

(defn byte-buffer->byte-array
  [^ByteBuffer bbuf]
  (.rewind bbuf)
  (let [arr (byte-array (.remaining bbuf))]
    (.get bbuf arr)
    arr))

(defn flatten-headers [headers]
  (->> headers
       (mapcat (fn [[nom val]]
                 (if (coll? val)
                   (map (fn [v] [(name nom) v]) val)
                   [[(name nom) val]])))))

(defn add-headers
  [^HttpRequest$Builder builder headers]
  (doseq [[nom val] (flatten-headers headers)]
    (.header builder nom val))
  builder)

(defn map->http-request
  [{:keys [scheme server-name server-port uri query-string
           request-method headers body]
    :or {scheme "https"}
    :as m}]
  (let [uri (URI. (str (name scheme)
                       "://"
                       server-name
                       (some->> server-port (str ":"))
                       uri
                       (some->> query-string (str "?"))))
        method (method-string request-method)
        bp (if body
             (HttpRequest$BodyPublishers/ofByteArray (byte-buffer->byte-array body))
             (HttpRequest$BodyPublishers/noBody))
        builder (-> (HttpRequest/newBuilder uri)
                    (.method method bp))]
    (.build (cond-> builder
              (seq headers) (add-headers headers)
              (::timeout-msec m) (.timeout (Duration/ofMillis
                                             (::timeout-msec m)))))))

(defn error->anomaly [^Throwable t]
  {:cognitect.anomalies/category :cognitect.anomalies/fault
   :cognitect.anomalies/message (.getMessage t)
   ::throwable t})

(defn header-map [^HttpHeaders headers]
  (->> headers
       (.map)
       (map (fn [[k v]] [k (if (< 1 (count v))
                             (into [] v)
                             (first v))]))
       (into {})))

(defn response-map
  [^HttpResponse http-response]
  (let [body (.body http-response)]
    (cond-> {:status (.statusCode http-response)
             :headers (header-map (.headers http-response))}
      body (assoc :body (ByteBuffer/wrap body)))))

(defrecord Client
    [^HttpClient http-client pending-ops pending-ops-limit]
  client/Client
  (-submit [_ request ch]
    (if (< pending-ops-limit (swap! pending-ops inc))
      (do
        (put! ch (merge {:cognitect.anomalies/category :cognitect.anomalies/busy
                         :cognitect.anomalies/message (str "Ops limit reached: " pending-ops-limit)
                         :pending-ops-limit pending-ops-limit}
                        (select-keys request [::meta])))
        (swap! pending-ops dec))
      (try
        (let [http-request (map->http-request request)]
          (-> (.sendAsync http-client http-request (HttpResponse$BodyHandlers/ofByteArray))
              (.thenApply
                (reify Function
                  (apply [_ http-response]
                    (put! ch (merge (response-map http-response)
                                    (select-keys request [::meta]))))))
              (.exceptionally
                (reify Function
                  (apply [_ e]
                    (let [cause (.getCause ^Exception e)]
                      (if (instance? ExceptionInfo cause)
                        (throw cause)
                        (throw e))))))))
        (catch Throwable t
          (put! ch (merge (error->anomaly t) (select-keys request [::meta])))
          (swap! pending-ops dec))))
    ch))

(defn create
  [{:keys [connect-timeout-msecs
           pending-ops-limit]
    :or {pending-ops-limit 64
         connect-timeout-msecs 5000}
    :as _config}]
  (System/setProperty "jdk.httpclient.allowRestrictedHeaders" "host")
  (let [http-client (.build (-> (HttpClient/newBuilder)
                                (.connectTimeout (Duration/ofMillis connect-timeout-msecs))
                                (.followRedirects HttpClient$Redirect/NORMAL)))]
    (->Client http-client (atom 0) pending-ops-limit)))

(defn stop
  "no-op. Implemented for compatibility"
  [^Client _client])
