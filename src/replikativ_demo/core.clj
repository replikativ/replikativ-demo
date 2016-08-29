(ns replikativ-demo.core
  (:require [replikativ.crdt.cdvcs.realize :refer [head-value stream-into-atom!]]
            [replikativ.crdt.cdvcs.stage :as s]
            [replikativ.stage :refer [create-stage! connect! subscribe-crdts!]]
            [replikativ.peer :refer [client-peer server-peer]]

            [kabel.http-kit :refer [start stop]]
            [konserve.memory :refer [new-mem-store]]
            [konserve.filestore :refer [new-fs-store delete-store]]

            [full.async :refer [<?? <? go-try go-loop-try]] ;; core.async error handling
            [clojure.core.async :refer [chan go-loop go]]))

(def uri "ws://127.0.0.1:31744")

(def cdvcs-id #uuid "8e9074a1-e3b0-4c79-8765-b6537c7d0c44")

;; we allow you to model the state efficiently as a reduction over function applications
;; for this to work you supply an "eval"-like mapping to the actual functions
(def eval-fns
  ;; the CRDTs are reduced over the transaction history according to this function mapping
  ;; NOTE: this allows you to change reduction semantics of past transactions as well
  {'(fn [_ new] new) (fn [_ new] new)
   '+ +})


(comment
  (delete-store "/tmp/replikativ-demo-store"))
;; create a local ACID key-value store
(def server-store (<?? (new-fs-store "/tmp/replikativ-demo-store")))


(def server (<?? (server-peer server-store uri)))

(start server)
(comment
  (stop server))

;; let's get distributed :)
(def client-store (<?? (new-mem-store)))

(def client (<?? (client-peer client-store)))

;; to interact with a peer we use a stage
(def stage (<?? (create-stage! "mail:eve@replikativ.io" client)))

(<?? (connect! stage uri))

;; create a new CDVCS
(<?? (s/create-cdvcs! stage :description "testing" :id cdvcs-id))

;; let's stream operations in an atom that we can watch
(def val-atom (atom -1))
(def close-stream
  (stream-into-atom! stage ["mail:eve@replikativ.io" cdvcs-id] eval-fns val-atom))

;; prepare a transaction
(<?? (s/transact! stage ["mail:eve@replikativ.io" cdvcs-id]
                 ;; set a new value for this CDVCS
                 [['(fn [_ new] new) 0]]))


;; did it work locally?
@val-atom ;; => 0

;; let's alter the value with a simple addition
(<?? (s/transact! stage ["mail:eve@replikativ.io" cdvcs-id]
                 [['+ 1123]]))

;; and did everything also apply remotely?
(<?? (head-value server-store
                 eval-fns
                 ;; manually verify metadata presence
                 (get-in @stage ["mail:eve@replikativ.io" cdvcs-id :state])))

(<?? (head-value client-store
                 eval-fns
                 ;; manually verify metadata presence
                 (get-in @stage ["mail:eve@replikativ.io" cdvcs-id :state])))
;; => 1123


(comment
  ;; a little stress test :)
  (doseq [i (range 100)]
    (<?? (s/transact! stage ["mail:eve@replikativ.io" cdvcs-id]
                      [['+ 1]]))))
