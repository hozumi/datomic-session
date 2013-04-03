(ns datomic-session
  (:require [clojure.data :as data]
            [datomic.api :as d]
            [ring.middleware.session.store :as rs]))

(defn key->eid [db key]
  (when key
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
    (if (seq new-only)
      (conj retracts (assoc new-only :db/id eid))
      retracts)))

(deftype DatomicStore [conn partition auto-key-change?]
  rs/SessionStore
  (read-session [_ key]
    (let [db (d/db conn)]
      (into {} (d/entity db (key->eid db key)))))
  (write-session [_ key data]
    (let [db (and key (d/db conn))
          eid (key->eid db key)
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
                        :db/id (d/tempid partition)
                        :session/key key)]))
      key))
  (delete-session [_ key]
    (when-let [eid (key->eid (d/db conn) key)]
      @(d/transact conn [[:db.fn/retractEntity eid]]))
    nil))

(defn datomic-store [{:keys [conn partition auto-key-change?]}]
  (DatomicStore. conn (or partition :db.part/user) auto-key-change?))
