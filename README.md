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

(defroutes my-routes ....)

(def conn (d/connect "datomic:mem://test"))

(def app (-> my-routes
             (ring.middleware.session/wrap-session
               {:store (datomic-session/datomic-store
                         {:conn conn
                          :schema [{:db/ident :session/user
                                    :db/id #db/id[:db.part/db]
                                    :db/valueType :db.type/ref
                                    :db/cardinality :db.cardinality/one
                                    :db/doc "Logined user"
                                    :db.install/_attribute :db.part/db}]})})
             ring.middleware.cookies/wrap-cookies))
```

Attribute definitions passed to the option-map and following definition of :session/key are installed automatically when they are not in db.

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

*options*

* **:conn** *(Connection)* Datomic connection.
* **:schema** *(Sequence)* A sequence of attribute definitions you will use as session. Probably you need only one attribute something like :session/user in order to track who the user is. I think you should store other infomation not in session, but in the db directly because session system has no advantage over datomic thanks to datomic's efficient cache system.
* **:no-history?** *(boolean)* Add `:db/noHistory true` to the definition of :session/key attrubute. Defaults to false.
* **:auto-key-change?** *(boolean)* Change session id when session is updated. Defaults to false.

## Installation
Leiningen

    [datomic-session "0.1.0-SNAPSHOT"]
