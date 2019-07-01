(ns ^{:doc "Defines a ring handler that exposes the Naga rule engine as a web service."
      :author "Jesse Bouwman"}
    naga-http.server
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            [cheshire.core :as json]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.response :refer :all]
            ; [compojure.api.sweet :refer :all]
            ; [ring.util.http-response :refer [not-found]]
            [naga.data :as data]
            [naga.engine :as e]
            [naga.lang.pabu :as pabu]
            [naga.rules :as r]
            [asami.core :as asami]
            [naga.store :as store]
            [naga.store-registry :as store-registry]
            [appa.core :as appa]
            [naga-http.configuration :as c]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            ; [ring.middleware.json-response :refer [wrap-json-response]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]])
  (:import [java.util Date]))

(def plain-headers {"Content-Type" "text/plain"})

(def json-headers {"Content-Type" "application/json"})

(def programs (atom {}))

(def ^:const default-graph (store-registry/get-storage-handle {:type :memory-multi}))

(def ^:const default-store {:type :memory-multi :store default-graph})

(def storage (atom default-store))

(defn uuid-str []
  (str (java.util.UUID/randomUUID)))

(defn register-store! [s]
  (let [g (store-registry/get-storage-handle s)]
    (reset! storage (assoc s :store g)))
  {:headers plain-headers
   :body (:type s)})

(defn reset-store! []
  (reset! storage default-store)
  {:headers plain-headers
   :body "OK"})

(defn update-store! [s]
  (swap! storage assoc :store s))

(defn registered-storage []
  (or @storage default-store))

(defn load-graph [g]
  (println "data: " g))

(defn parse-program [text]
  (let [{rules :rules} (pabu/read-str text)]
    (r/create-program rules [])))

(defn install-program! [s]
  (let [uuid (uuid-str)
        text (slurp s)]
    (swap! programs assoc uuid
           {:created (Date.)
            :text text
            :program (parse-program text)})
    uuid))

(defn post-program [s]
  {:headers plain-headers
   :body (install-program! s)})

(defn get-program [uuid]
  (when-let [program (get @programs uuid)]
    {:headers json-headers
     :body (:text program)}))

(defn delete-programs []
  (reset! programs {})
  {:headers plain-headers
   :body "OK"})

(defn execute-program [program store data]
  (when program
    (let [triples-data (when data (data/stream->triples store data))
          loaded-store (store/assert-data store triples-data)
          config (assoc storage :store loaded-store)
          [store stats] (e/run config program)
          output (data/store->str store)]
      [output store])))

(defn exec-registered [uuid s]
  (when-let [program (get @programs uuid)]
    (let [storage (registered-storage)
          store (or (:store storage)
                    (store-registry/get-storage-handle storage))
          [output new-store] (execute-program program store s)]
      (update-store! new-store)
      {:headers json-headers
       :body output})))

(defn exec-program [uuid program storage-config]
  (when-let [program (or program (get @programs uuid))]
    (let [storage (or storage-config (registered-storage))
          store (or (:store storage)
                    (store-registry/get-storage-handle storage))
          [output new-store] (execute-program program store nil)]
      (when-not storage-config
        (update-store! new-store))
      {:headers json-headers
       :body output})))

(defn upload
  [{data :body :as request}]
  (println "KEYS: " request)
  (println "INCOMING: " data)
  (let [store (:store (registered-storage))
        triples (data/json->triples store data)]
    (println "TRIPLES")
    (pprint triples)
    (update-store! (store/assert-data store triples))))

(defn query-store
  [{{q-data :query} :body :as request}]
  (let [result (asami/q q-data (:store (registered-storage)))]
    {:headers json-headers
     :body {:data result}}))

(defn jquery-store
  [{{jq-text :query} :body :as request}]
  (let [jq (appa/jq->graph-query jq-text)
        result (asami/q jq (:store (registered-storage)))]
    {:headers json-headers
     :body {:data result}}))

(defroutes app-routes
  (GET    "/data" request (query-store request))
  (GET    "/jq" request (jquery-store request))
  (POST   "/data" request (upload request))
  (POST   "/store" request (register-store! (:body request)))
  (DELETE "/store" request (reset-store!))
  (POST   "/graph" request (load-graph (:body request)))
  (POST   "/rules" request (post-program (:body request)))
  (DELETE "/rules" request (delete-programs))
  (GET    "/rules/:uuid" [uuid] (get-program uuid))
  (POST   "/rules/:uuid/eval" [uuid :as request] (exec-registered uuid (:body request)))
  (POST   "/rules/:uuid/execute" [uuid :as {{:keys [program store]} :body}]
          (exec-program uuid program store))
  (route/not-found "Not Found"))

(def app
  (wrap-defaults
   (wrap-json-body
    (wrap-json-response app-routes))
   (assoc-in api-defaults [:params :multipart] true)))

;; TODO: main, starting a web server
(c/init!)
