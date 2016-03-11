(ns replikativ-demo.core
  (:require [replikativ.crdt.cdvcs.realize :refer [head-value stream-into-atom!]]
            [replikativ.crdt.cdvcs.stage :as s]
            [replikativ.stage :refer [subscribe-crdts!]]
            [replikativ.stage :refer [create-stage! connect! subscribe-crdts!]]
            [replikativ.peer :refer [client-peer server-peer]]

            [kabel.platform :refer [start stop]]
            [konserve.memory :refer [new-mem-store]]

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


;; create a local ACID key-value store
(def server-store (<?? (new-mem-store)))

;; collect errors
(def err-ch (chan))

;; and just print them to the REPL
(go-loop [e (<? err-ch)]
  (when e
    (println "ERROR:" e)
    (recur (<? err-ch))))

(def server (<?? (server-peer server-store err-ch uri)))

(start server)
(comment
  (stop server))

;; let's get distributed :)
(def client-store (<?? (new-mem-store)))

(def client (<?? (client-peer client-store err-ch)))

;; to interact with a peer we use a stage
(def stage (<?? (create-stage! "mail:eve@replikativ.io" client err-ch)))

(<?? (connect! stage uri))

;; create a new CDVCS
(<?? (s/create-cdvcs! stage :description "testing" :id cdvcs-id))

;; let's stream operations in an atom that we can watch
(def val-atom (atom -1))
(stream-into-atom! stage ["mail:eve@replikativ.io" cdvcs-id] eval-fns val-atom)

;; prepare a transaction
(<?? (s/transact stage ["mail:eve@replikativ.io" cdvcs-id]
                 ;; set a new value for this CDVCS
                 '(fn [_ new] new)
                 0))

;; commit it
(<?? (s/commit! stage {"mail:eve@replikativ.io" #{cdvcs-id}}))


;; did it work locally?
@val-atom ;; => 0

;; let's alter the value with a simple addition
(<?? (s/transact stage ["mail:eve@replikativ.io" cdvcs-id]
                 '+ 1123))

;; commit it
(<?? (s/commit! stage {"mail:eve@replikativ.io" #{cdvcs-id}}))

;; and did everything also apply remotely?
(<?? (head-value server-store
                 eval-fns
                 ;; manually verify metadata presence
                 (:state (get @(:state server-store) ["mail:eve@replikativ.io" cdvcs-id]))))
