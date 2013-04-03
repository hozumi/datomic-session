(ns datomic-session
  (:require [clojure.data :as data]
            [datomic.api :as d]
            [ring.middleware.session.store :as rs]))

(defn key->eid [db key-attr key]
  (ffirst
   (d/q '[:find ?eid
          :in $ ?key-attr ?key
          :where
          [?eid ?key-attr ?key]]
        db key-attr key)))

(defn diff-tx-data [eid old-m new-m]
  (let [[old-only new-only] (data/diff old-m new-m)
        retracts (->> old-only
                      (remove (fn [[k]] (get new-only k)))
                      (map (fn [[k v]] [:db/retract eid k v])))]
    (if (seq new-only)
      (conj retracts (assoc new-only :db/id eid))
      retracts)))

(deftype DatomicStore [conn key-attr partition auto-key-change?]
  rs/SessionStore
  (read-session [_ key]
    (let [uuid-key (when key
                     (try (java.util.UUID/fromString key)
                          (catch java.lang.IllegalArgumentException e nil)))]
      (into {} (when uuid-key
                 (let [db (d/db conn)]
                   (d/entity db (key->eid db key-attr uuid-key)))))))
  (write-session [_ key data]
    (let [uuid-key (when key
                     (try (java.util.UUID/fromString key)
                          (catch java.lang.IllegalArgumentException e nil)))
          db (when uuid-key (d/db conn))
          eid (when uuid-key (key->eid db key-attr uuid-key))
          key-change? (or (not eid) auto-key-change?)
          uuid-key (if key-change?
                     (java.util.UUID/randomUUID) uuid-key)]
      (if eid
        (let [old-data (into {} (d/entity db eid))
              tx-data (diff-tx-data eid old-data (assoc data key-attr uuid-key))]
          (when (seq tx-data)
            @(d/transact conn tx-data)))
        @(d/transact conn
                     [(assoc data
                        :db/id (d/tempid partition)
                        key-attr uuid-key)]))
      (str uuid-key)))
  (delete-session [_ key]
    (when-let [uuid-key (when key
                          (try (java.util.UUID/fromString key)
                               (catch java.lang.IllegalArgumentException e nil)))]
      (when-let [eid (key->eid (d/db conn) key-attr uuid-key)]
        @(d/transact conn [[:db.fn/retractEntity eid]])))
    nil))

(defn datomic-store [{:keys [conn key-attr partition auto-key-change?]
                      :or {key-attr :session/key partition :db.part/user}}]
  (DatomicStore. conn key-attr partition auto-key-change?))
