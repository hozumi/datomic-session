# datomic-session

Datomic-session is a Datomic version of Ring http session storage.

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
 :db/valueType :db.type/uuid
 :db/cardinality :db.cardinality/one
 :db/unique :db.unique/value
 :db/index true
 :db/doc "A key of session"
 :db.install/_attribute :db.part/db}
```

Then you also need to install attributes you will use as session.<br>
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

* **:conn** *(Connection)* specifies datomic connection.
* **:key-attr** *(Keyword)* specifies session-key attribute. Default is `:session/key`.
* **:partition** *(Keyword)* specifies datomic partition where session data is stored. Default is `:db.part/user`.
* **:auto-key-change?** *(boolean)* specifies whether or not session id are changed when updated. Default is false.

## Installation
Leiningen

    [datomic-session "0.2.0"]
