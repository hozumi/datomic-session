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
          newkey (if key-change?
                   (str (java.util.UUID/randomUUID)) key)]
      (if eid
        (let [old-data (into {} (d/entity db eid))
              tx-data (diff-tx-data eid old-data (assoc data :session/key newkey))]
          (when (seq tx-data)
            @(d/transact conn tx-data)))
        @(d/transact conn
                     [(assoc data
                        :db/id #db/id [:db.part/user]
                        :session/key newkey)]))
      newkey))
  (delete-session [_ key]
    (when-let [eid (get-eid-by-key (d/db conn) key)]
      @(d/transact conn [[:db.fn/retractEntity eid]]))
    nil))

(defn ensure-attrs [conn attrs]
  (let [db (d/db conn)
        new-attrs (filter #(->> % :db/ident (d/entity db) :db/id not)
                          attrs)]
    (when-not (empty? new-attrs)
      @(d/transact conn new-attrs))))

(def key-attr
  {:db/ident :session/key
   :db/id #db/id[:db.part/db]
   :db/valueType :db.type/string
   :db/cardinality :db.cardinality/one
   :db/unique :db.unique/value
   :db/index true
   :db/doc "A key of session"
   :db.install/_attribute :db.part/db})

(defn datomic-store [{:keys [conn attrs no-history? auto-key-change?]}]
  (ensure-attrs conn
                (conj attrs
                      (if no-history?
                        (assoc key-attr :db/noHistory true) key-attr)))
  (DatomicStore. conn auto-key-change?))