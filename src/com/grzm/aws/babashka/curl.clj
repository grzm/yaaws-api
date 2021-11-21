(ns com.grzm.aws.babashka.curl
  (:require
   [babashka.curl :as curl]
   [clojure.core.async :refer [put!] :as a]
   [clojure.set :as set]
   [com.grzm.aws.http-client.client :as client])
  (:import
   (java.io ByteArrayInputStream InputStream)
   (java.nio ByteBuffer)))

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
   (client/submit client request ch)))

(defn byte-buffer->input-stream
  [^ByteBuffer bbuf]
  (.rewind bbuf)
  (let [arr (byte-array (.remaining bbuf))]
    (.get bbuf arr)
    (ByteArrayInputStream. arr)))

(defn map->http-request
  [{:keys [request-method headers body]
    :as m}]
  (let [url (-> {:scheme "https"}
                (merge (select-keys m [:scheme :server-port :server-name :uri :query-string]))
                (update :scheme name)
                (set/rename-keys {:server-port :port
                                  :server-name :host
                                  :uri :path
                                  :query-string :query}))]
    (cond-> {:url url
             :method request-method
             :follow-redirects true
             :as :stream}
      (seq headers) (assoc :headers headers)
      (::timeout-msecs m) (assoc :raw-args ["--max-time" (* 1000 (::timeout-msecs m))])
      body (assoc :body (byte-buffer->input-stream body)))))

(defn error->anomaly [^Throwable t]
  {:cognitect.anomalies/category :cognitect.anomalies/fault
   :cognitect.anomalies/message (.getMessage t)
   ::throwable t})

(defn input-stream->byte-buffer
  [^InputStream input-stream]
  (-> (.readAllBytes input-stream)
      (ByteBuffer/wrap)))

(defn response-map
  [{:keys [status body headers] :as _response}]
  (cond-> {:status status
           :headers headers}
    body (assoc :body (input-stream->byte-buffer body))))

(defrecord Client
    [pending-ops pending-ops-limit connect-timeout-msecs]
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
          (a/thread
            (let [response (curl/request http-request)]
              (if (:error response)
                (put! ch {:cognitect.anomalies/category :cognitect.anomalies/fault
                          :error (:error response)})
                (put! ch (merge (response-map response)
                                (select-keys request [::meta])))))))
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
  (->Client (atom 0) pending-ops-limit connect-timeout-msecs))

(defn stop
  "no-op. Implemented for compatibility"
  [^Client _client])

(comment
  (def c (create nil))
  (def request {:server-name "www.google.com"
                :request-method :get
                :server-port 443})

  (curl/request (map->http-request request))
  (def ch (submit c request))

  (def res (a/<!! ch))

  :end)
