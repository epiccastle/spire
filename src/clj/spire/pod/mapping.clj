(ns spire.pod.mapping
  (:import [java.lang.ref WeakReference ReferenceQueue]
           [java.util WeakHashMap Collections HashMap]))

;; A two-way, auto collecting, weakref object instance mapping system

;; All the spire java instances need to stay in the spire heap memory.
;; On the babashka side, we need to refer to these objects with keywords.
;; We need to look up an instance from a keyword, and a keyword from
;; an instance. If we stored these in a normal hashmap, we would
;; hold references to these instances and prevent garbage collection. Thus
;; we need to use weak references and create both lookups so what when
;; the garbage collector deletes the instance, the references are
;; removed from the mapping structure automatically.

(def key-set (into [] "0123456789abcdefghijklmnopqrstuvwxyz"))
(def key-length 16)

(defn make-key [namespace prefix]
  (->> key-length
       range
       (map (fn [_] (rand-nth key-set)))
       (apply str prefix "-")
       (keyword namespace)))

(defrecord weak-mapping
    [
     ;; maps full java object instances to their keys.
     ;; is a WeakHashMap so instances magically disappear when GCed
     instance->key

     ;; maps the reference key to a weakref of the instance
     key->instance

     ;; maps the weakref if the instance to the key. When the
     ;; instance is GCed, we can find it on the queue, but
     ;; we still need to look up the key with that wekref,
     ;; so this enables O(log32N) performance for that lookup
     weakref->key

     ;; the weakrefs that have been GCed
     queue

     ;; Synchronisation lock used around the entire mapping pair
     ;; to keep it threadsafe
     lock])

(defn make-weak-mapping []
  (->weak-mapping
   (Collections/synchronizedMap (WeakHashMap.))
   (HashMap.)
   (HashMap.)
   (ReferenceQueue.)
   (Object.)))

#_ (def mapping (make-weak-mapping))

(defn add-instance! [weak-mapping instance key-ns key-prefix]
  (let [{:keys [instance->key key->instance weakref->key queue lock]} weak-mapping]
    (locking lock
      (if-let [existing-key (.get instance->key instance)]
        existing-key
        (let [new-key (make-key key-ns key-prefix)
              weakref (WeakReference. instance queue)]
          (.put instance->key instance new-key)
          (.put key->instance new-key weakref)
          (.put weakref->key weakref new-key)
          new-key)))))

#_ (def obj (Object.))
#_ (def addded-key (add-instance! weak-mapping obj "ns" "prefix"))
#_ weak-mapping

(defn clear-gc-references! [weak-mapping]
  (let [{:keys [key->instance weakref->key queue lock]} weak-mapping]
    (locking lock
      (loop [weakref (.poll queue)
             deleted []]
        (if weakref
          (let [deleted-key (.get weakref->key weakref)]
            (.remove key->instance deleted-key)
            (.remove weakref->key weakref)
            (recur (.poll queue) (conj deleted deleted-key)))
          deleted)))))

#_ (clear-gc-references! weak-mapping)
#_ weak-mapping

#_ (def obj nil)
#_ (System/gc)
#_ (System/runFinalization)

#_ (clear-gc-references! weak-mapping)
#_ weak-mapping

(defn get-key-for-instance [weak-mapping instance]
  (clear-gc-references! weak-mapping)
  (let [{:keys [instance->key lock]} weak-mapping]
    (locking lock
      (.get instance->key instance))))

#_ (def obj (Object.))
#_ (add-instance! weak-mapping obj "ns" "obj")
#_ (get-key-for-instance weak-mapping obj)

(defn get-weakref-for-key [weak-mapping key]
  (clear-gc-references! weak-mapping)
  (let [{:keys [key->instance lock]} weak-mapping]
    (locking lock
      (.get key->instance key))))

#_ (= obj (.get (get-weakref-for-key weak-mapping (get-key-for-instance weak-mapping obj))))
#_ (get-weakref-for-key weak-mapping :foo)

(defn get-instance-for-key [weak-mapping key]
  (when-let [weakref (get-weakref-for-key weak-mapping key)]
    (.get weakref)))

#_ (= obj (get-instance-for-key weak-mapping (get-key-for-instance weak-mapping obj)))
#_ (get-instance-for-key weak-mapping :foo)

(defn has-key [weak-mapping key]
  (boolean (get-instance-for-key weak-mapping key)))

(defn has-instance [weak-mapping instance]
  (boolean (get-key-for-instance weak-mapping instance)))

