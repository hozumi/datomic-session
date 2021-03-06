(ns datomic-session-test
  (:use [clojure.test])
  (:require [ring.middleware.session.store :as rs]
            [datomic.api :as d]
            [datomic-session :as ds]))

(defn fresh-db-conn! []
  (let [uri "datomic:mem://datomic-session-test"]
    (d/delete-database uri)
    (d/create-database uri)
    (let [c (d/connect uri)]
      c)))

(def ^:dynamic *conn*)

(def attrs
  [{:db/ident :session/key
    :db/id #db/id[:db.part/db]
    :db/valueType :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/value
    :db/index true
    :db/doc "A key of session"
    :db.install/_attribute :db.part/db},
   {:db/ident :session/foo
    :db/id #db/id[:db.part/db]
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/noHistory true
    :db.install/_attribute :db.part/db}])

(defmacro with-testdb [& body]
  `(binding [*conn* (fresh-db-conn!)]
     @(d/transact *conn* attrs)
     ~@body))

(deftest read-not-exist
  (with-testdb
    (let [store (ds/datomic-store {:conn *conn*})]
      (is (= (rs/read-session store "non-existent")
             {})))))

(deftest session-create
  (with-testdb
    (let [store (ds/datomic-store {:conn *conn*})
          key (rs/write-session store nil {:session/foo "bar"})
          entity   (rs/read-session store key)]
      (is key)
      (is (= entity
             {:session/foo "bar",
              :session/key (java.util.UUID/fromString key)})))))

(deftest session-update
  (with-testdb
    (let [store (ds/datomic-store {:conn *conn*})
          key0 (rs/write-session store nil {:session/foo "bar"})
          key1 (rs/write-session store key0 {:session/foo "baz",
                                             :session/key key0})
          entity1 (rs/read-session store key1)
          key2 (rs/write-session store key1 {:session/key key1})
          entity2 (rs/read-session store key2)]
      (is (= key0 key1 key2))
      (is (= entity1 {:session/foo "baz",
                      :session/key (java.util.UUID/fromString key1)}))
      (is (= entity2 {:session/key (java.util.UUID/fromString key2)})))))

(deftest session-auto-key-change
  (with-testdb
    (let [store (ds/datomic-store
                 {:conn *conn*
                  :auto-key-change? true})
          key (rs/write-session store nil {:session/foo "bar"})
          key* (rs/write-session store key {:session/foo "baz",
                                            :session/key key})
          entity (rs/read-session store key*)]
      (is (not= key key*))
      (is (= entity
             {:session/foo "baz",
              :session/key (java.util.UUID/fromString key*)})))))

(deftest session-delete
  (with-testdb
    (let [store (ds/datomic-store {:conn *conn*})
          key (rs/write-session store nil {:session/foo "bar"})]
      (is (nil? (rs/delete-session store key)))
      (is (= (rs/read-session store key) {})))))