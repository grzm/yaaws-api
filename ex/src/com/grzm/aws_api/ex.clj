(ns com.grzm.aws-api.ex
  (:require
   [clojure.pprint :as pprint]
   [com.grzm.aws.client.api :as aws]))

(defn -main [& _args]
  (let [sts (aws/client {:api :sts})
        res' (aws/invoke sts {:op :GetCallerIdentity})
        s3 (aws/client {:api :s3})
        res (aws/invoke s3 {:op :ListBuckets})]
    ;; (pprint/pprint {:ops (aws/ops sts)})
    ;; (pprint/pprint {:doc (aws/doc sts :GetCallerIdentity)})
    (pprint/pprint {:res (if (:cognitect.anomalies/category res)
                           res
                           res)
                    :meta (meta res)})))


(comment
  (def s3 (aws/client {:api :s3}))

  (-> (aws/ops s3)
      keys
      sort)

  (do
    (require '[clojure.java.io :as io])
    (require '[clojure.edn :as edn]))
  (let [sts-edn-path "/Users/grzm/dev/sts-811.2.958.0/cognitect/aws/sts/service.edn"
        sts-service-path (io/file "/Users/grzm/dev/sts-811.2.958.0" "service.edn")]
    (-> (slurp sts-edn-path)
        (edn/read-string)
        (pprint/pprint)
        (with-out-str)
        (->> (spit sts-service-path))))


  :end)
