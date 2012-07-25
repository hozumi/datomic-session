(ns clj-datomic-session-test
  (:use [clojure.test])
  (:require [ring.middleware.session.store :as rs]
            [datomic.api :as d]
            [clj-datomic-session :as ds]))

(defn fresh-db-conn! []
  (let [uri "datomic:mem://datomic-session-test"]
    (d/delete-database uri)
    (d/create-database uri)
    (let [c (d/connect uri)]
      c)))

(def ^:dynamic *conn*)

(defmacro with-testdb [& body]
  `(binding [*conn* (fresh-db-conn!)]
     ~@body))

(deftest read-not-exist
  (with-testdb
    (let [store (ds/datomic-store {:conn *conn*})]
      (is (rs/read-session store "non-existent")
          {}))))

(def my-schema [{:db/ident :session/foo
                 :db/id #db/id[:db.part/db]
                 :db/valueType :db.type/string
                 :db/cardinality :db.cardinality/one
                 :db/noHistory true
                 :db.install/_attribute :db.part/db}])

(deftest session-create
  (with-testdb
    (let [store (ds/datomic-store
                 {:conn *conn*
                  :schema my-schema})
          key (rs/write-session store nil {:session/foo "bar"})
          entity   (rs/read-session store key)]
      (is key)
      (is (:session/key entity))
      (is (= (:session/foo entity) "bar")))))

(deftest session-update
  (with-testdb
    (let [store (ds/datomic-store
                 {:conn *conn*
                  :schema my-schema})
          key (rs/write-session store nil {:session/foo "bar"})
          key* (rs/write-session store key {:session/foo "baz"})
          entity (rs/read-session store key*)]
      (is (= key key*))
      (is (:session/key entity))
      (is (= (:session/foo entity) "baz")))))

(deftest session-auto-key-change
  (with-testdb
    (let [store (ds/datomic-store
                 {:conn *conn*
                  :schema my-schema
                  :auto-key-change? true})
          key (rs/write-session store nil {:session/foo "bar"})
          key* (rs/write-session store key {:session/foo "baz"})
          entity (rs/read-session store key*)]
      (is (not= key key*))
      (is (:session/key entity))
      (is (= (:session/foo entity) "baz")))))

(deftest session-delete
  (with-testdb
    (let [store (ds/datomic-store
                 {:conn *conn*
                  :schema my-schema})
          key (rs/write-session store nil {:session/foo "bar"})]
      (is (nil? (rs/delete-session store key)))
      (is (= (rs/read-session store key) {})))))