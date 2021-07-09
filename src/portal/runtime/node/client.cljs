(ns portal.runtime.node.client
  (:require [portal.runtime :as rt]))

(defonce sessions (atom {}))

(defonce ^:private id (atom 0))
(defonce ^:private pending-requests (atom {}))

(defn- next-id [] (swap! id inc))

(defn- get-session [session-id]
  (get @sessions session-id))

(def ops
  {:portal.rpc/response
   (fn [message _done]
     (let [id (:portal.rpc/id message)]
       (when-let [[resolve] (get @pending-requests id)]
         (resolve message))))})

(defn request
  ([message]
   (js/Promise.all
    (for [session-id (keys @sessions)]
      (request session-id message))))
  ([session-id message]
   (if-let [send! (get-session session-id)]
     (let [id      (next-id)
           message (assoc message :portal.rpc/id id)]
       (.then
        (js/Promise.
         (fn [resolve reject]
           (swap! pending-requests assoc id [resolve reject])
           (send! message)))
        #(do (swap! pending-requests dissoc id) %)))
     (throw (ex-info "No such portal session"
                     {:session-id session-id :message message})))))

(defn- push-state [session-id new-value]
  (request session-id {:op :portal.rpc/push-state :state new-value})
  new-value)

(defrecord Portal [session-id]
  IDeref
  (-deref [_this] @rt/selected)
  IReset
  (-reset! [_this new-value] (push-state session-id new-value))
  ISwap
  (-swap! [this f]
    (reset! this (f @this)))
  (-swap! [this f a]
    (reset! this (f @this a)))
  (-swap! [this f a b]
    (reset! this (f @this a b)))
  (-swap! [this f a b xs]
    (reset! this (apply f @this a b xs))))

(defn make-atom [session-id] (Portal. session-id))

(defn open? [session-id]
  (get @sessions session-id))

(reset! rt/request request)
