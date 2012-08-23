# datomic-session

Datomic-session is a Datomic version of Ring's http session storage.

## Usage

```clojure
(ns hello
  (:require [ring.middleware.session]
            [ring.middleware.cookies]
            [datomic-session]
            [datomic.api :as d]
    ...))

(def conn (d/connect "datomic:mem://test"))

(def app (-> myhandler
             (ring.middleware.session/wrap-session
               {:store (datomic-session/datomic-store {:conn conn})})
             ring.middleware.cookies/wrap-cookies))
```

You must install following :session/key attribute into datomic.

```clojure
{:db/ident :session/key
 :db/id #db/id[:db.part/db]
 :db/valueType :db.type/string
 :db/cardinality :db.cardinality/one
 :db/unique :db.unique/value
 :db/index true
 :db/doc "A key of session"
 :db.install/_attribute :db.part/db}
```

Then you also need to install attributes you will use as session.
*Example*

```clojure
{:db/ident :session/user
 :db/id #db/id[:db.part/db]
 :db/valueType :db.type/ref
 :db/cardinality :db.cardinality/one
 :db/doc "Logined user"
 :db.install/_attribute :db.part/db}
```

*options*

* **:conn** *(Connection)* Datomic connection.
* **:auto-key-change?** *(boolean)* Change session id when session is updated. Defaults to false.

## Installation
Leiningen

    [datomic-session "0.1.0-SNAPSHOT"]
