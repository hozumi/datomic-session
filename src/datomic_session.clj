(ns datomic-session
  (:require [clojure.data :as data]
            [datomic.api :as d]
            [ring.middleware.session.store :as rs]))

(defn get-eid-by-key [db key]
  (and key
       (ffirst
        (d/q '[:find ?eid
               :in $ ?key
               :where
               [?eid :session/key ?key]]
             db key))))

(defn diff-tx-data [eid old-m new-m]
  (let [[old-only new-only] (data/diff old-m new-m)
        retracts (->> old-only
                      (remove (fn [[k]] (get new-only k)))
                      (map (fn [[k v]] [:db/retract eid k v])))]
    (if (empty? new-only)
      retracts
      (conj retracts (assoc new-only :db/id eid)))))

(deftype DatomicStore [conn auto-key-change?]
  rs/SessionStore
  (read-session [_ key]
    (let [db (d/db conn)]
      (into {} (d/entity db (get-eid-by-key db key)))))
  (write-session [_ key data]
    (let [db (and key (d/db conn))
          eid (get-eid-by-key db key)
          key-change? (or (not eid) auto-key-change?)
          key (if key-change?
                   (str (java.util.UUID/randomUUID)) key)]
      (if eid
        (let [old-data (into {} (d/entity db eid))
              tx-data (diff-tx-data eid old-data (assoc data :session/key key))]
          (when (seq tx-data)
            @(d/transact conn tx-data)))
        @(d/transact conn
                     [(assoc data
                        :db/id #db/id [:db.part/user]
                        :session/key key)]))
      key))
  (delete-session [_ key]
    (when-let [eid (get-eid-by-key (d/db conn) key)]
      @(d/transact conn [[:db.fn/retractEntity eid]]))
    nil))

(defn datomic-store [{:keys [conn auto-key-change?]}]
  (DatomicStore. conn auto-key-change?))