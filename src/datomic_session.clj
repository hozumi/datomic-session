(ns datomic-session
  (:require [datomic.api :as d]
            [ring.middleware.session.store :as rs]))

(defn get-eid-by-key [db key]
  (and key
       (ffirst
        (d/q '[:find ?eid
               :in $ ?key
               :where
               [?eid :session/key ?key]]
             db key))))

(deftype DatomicStore [conn auto-key-change?]
  rs/SessionStore
  (read-session [_ key]
    (let [db (d/db conn)]
      (if-let [eid (get-eid-by-key db key)]
        (d/entity db eid)
        {})))
  (write-session [_ key data]
    (let [eid (get-eid-by-key (d/db conn) key)
          key-change? (or (not eid) auto-key-change?)
          newkey (if key-change?
                   (str (java.util.UUID/randomUUID)) key)]
      @(d/transact conn
                   [(if eid
                      (if key-change?
                        (assoc data :db/id eid :session/key newkey)
                        (assoc data :db/id eid))
                      (assoc data :db/id #db/id [:db.part/user]
                             :session/key newkey
                             :session/date (java.util.Date.)))])
      newkey))
  (delete-session [_ key]
    (when-let [eid (get-eid-by-key (d/db conn) key)]
      @(d/transact conn [[:db.fn/retractEntity eid]]))
    nil))

(defn ensure-schema [conn schema]
  (let [installed-attr-set (->> (d/q '[:find ?attr
                                       :where
                                       [?e :db/ident ?attr]]
                                     (d/db conn))
                                (apply concat)
                                set)
        new-schema (filter (fn [{ident :db/ident}]
                             (not (installed-attr-set ident))) schema)]
    (when-not (empty? new-schema)
      @(d/transact conn new-schema))))

(def my-schema
  [{:db/ident :session/key
    :db/id #db/id[:db.part/db]
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/value
    :db/index true
    :db/noHistory true
    :db/doc "A key of session"
    :db.install/_attribute :db.part/db}
   {:db/ident :session/date
    :db/id #db/id[:db.part/db]
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :db/noHistory true
    :db/doc "A starting date of session"
    :db.install/_attribute :db.part/db}])

(defn datomic-store [{:keys [conn schema auto-key-change?]}]
  (ensure-schema conn (concat my-schema schema))
  (DatomicStore. conn auto-key-change?))
