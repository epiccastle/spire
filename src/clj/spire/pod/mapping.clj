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

;;
;; a strong ref mapping system
;;

;; sometimes the reference objects on the spire side hold no references
;; due to the lifecycle being managed on the bb side. In these instances
;; we need strong mapping so the GC doesn't clean up the objects underneath
;; us. These objects need deletion triggered from the babashka side
(defn make-strong-mapping []
  {
   ;; map java object instances to keywords
   :instance->key {}

   ;; maps the reference key to the object
   :key->instance {}})

(defn add-strong-instance
  "returns the new mapping insance with instance added"
  [mapping instance key-ns key-prefix]
  (let [{:keys [instance->key key->instance]} mapping]
    (if-let [existing-key (get instance->key instance)]
      mapping
      (let [new-key (make-key key-ns key-prefix)]
        (-> mapping
            (assoc-in [:instance->key instance] new-key)
            (assoc-in [:key->instance new-key] instance))))))

(defn add-strong-instance!
  [mapping-atom instance key-ns key-prefix]
  (-> (swap! mapping-atom add-strong-instance instance key-ns key-prefix)
      (get-in [:instance->key instance])))

(defn get-strong-key-for-instance [mapping instance]
  (get-in mapping [:instance->key instance]))

(defn get-strong-key-for-instance! [mapping-atom instance]
  (get-strong-key-for-instance @mapping-atom instance))

(defn get-strong-instance-for-key [mapping key]
  (get-in mapping [:key->instance key]))

(defn get-strong-instance-for-key! [mapping-atom key]
  (get-strong-instance-for-key @mapping-atom key))

(defn delete-strong-instance [mapping instance]
  (let [key (get-strong-key-for-instance mapping instance)]
    (-> mapping
        (update :instance->key dissoc instance)
        (update :key->instance dissoc key))))

(defn delete-strong-instance! [mapping-atom instance]
  (swap! mapping-atom delete-strong-instance instance))

(defn delete-strong-key [mapping key]
  (let [instance (get-strong-instance-for-key mapping key)]
    (-> mapping
        (update :instance->key dissoc instance)
        (update :key->instance dissoc key))))

(defn delete-strong-key! [mapping-atom key]
  (swap! mapping-atom delete-strong-key key))
