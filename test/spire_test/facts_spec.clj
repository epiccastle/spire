(ns spire-test.facts-spec
  (:require [clojure.spec.alpha :as s]))

;; ---------------------------------------------------------------
;; Shell
;; ---------------------------------------------------------------

(s/def :shell/type keyword?)
(s/def :shell/version string?)
(s/def :shell/shell string?)
(s/def :shell/login-shell string?)
(s/def :shell/canonical-path string?)

(s/def ::shell
  (s/keys :req-un [:shell/type :shell/version :shell/shell
                    :shell/login-shell :shell/canonical-path]))

;; ---------------------------------------------------------------
;; OS
;; ---------------------------------------------------------------

(s/def :os/family keyword?)

(s/def :kernel/name string?)
(s/def :kernel/release string?)
(s/def :kernel/version string?)
(s/def :os/kernel
  (s/keys :req-un [:kernel/name :kernel/release :kernel/version]))

(s/def :os/machine string?)

(s/def :distro/id keyword?)
(s/def :distro/name string?)
(s/def :distro/release string?)
(s/def :distro/codename (s/nilable keyword?))
(s/def :distro/description string?)
(s/def :distro/build string?)
(s/def :os/distro
  (s/keys :req-un [:distro/id :distro/name :distro/release :distro/codename]
          :opt-un [:distro/description :distro/build]))

(s/def ::os
  (s/keys :req-un [:os/family :os/kernel :os/machine :os/distro]))

;; ---------------------------------------------------------------
;; Hardware / CPU
;; ---------------------------------------------------------------

(s/def :cpu/model string?)
(s/def :cpu/cores pos-int?)
(s/def :cpu/flags (s/coll-of string? :kind set?))
(s/def :cpu/architecture string?)

(s/def :hardware/cpu
  (s/keys :req-un [:cpu/model :cpu/cores :cpu/flags :cpu/architecture]))

;; ---------------------------------------------------------------
;; Hardware / Memory
;; ---------------------------------------------------------------

(s/def :memory/total nat-int?)
(s/def :memory/swap nat-int?)
(s/def :hardware/memory
  (s/keys :req-un [:memory/total :memory/swap]))

;; ---------------------------------------------------------------
;; Hardware / Disks
;; ---------------------------------------------------------------

(s/def :disk/name string?)
(s/def :disk/size nat-int?)
(s/def :disk/type keyword?)
(s/def ::disk (s/keys :req-un [:disk/name :disk/size :disk/type]))
(s/def :hardware/disks (s/coll-of ::disk :kind vector?))

;; ---------------------------------------------------------------
;; Hardware / Virtualization
;; ---------------------------------------------------------------

(s/def :virtualization/is-virtual? boolean?)
(s/def :virtualization/type (s/nilable keyword?))
(s/def :hardware/virtualization
  (s/keys :req-un [:virtualization/is-virtual? :virtualization/type]))

(s/def ::hardware
  (s/keys :req-un [:hardware/cpu :hardware/memory
                    :hardware/disks :hardware/virtualization]))

;; ---------------------------------------------------------------
;; Users
;; ---------------------------------------------------------------

(s/def :id-name/id (some-fn int? string?)) ;; int on unix, SID string on windows
(s/def :id-name/name string?)
(s/def ::id-name (s/keys :req-un [:id-name/id :id-name/name]))

(s/def :users/gid ::id-name)
(s/def :users/uid ::id-name)
(s/def :users/groups (s/coll-of ::id-name :kind vector?))
(s/def :users/group-ids (s/coll-of (some-fn int? string?) :kind set?)) ;; int on unix, SID string on windows
(s/def :users/group-names (s/coll-of string? :kind set?))

(s/def ::users
  (s/keys :req-un [:users/gid :users/uid :users/groups
                    :users/group-ids :users/group-names]))

;; ---------------------------------------------------------------
;; Filesystem
;; ---------------------------------------------------------------

(s/def :fs/device string?)
(s/def :fs/mount-point string?)
(s/def :fs/type (s/nilable string?))   ;; nil on some windows volumes
(s/def :fs/options (s/nilable string?)) ;; nil on windows
(s/def :fs/size (s/nilable nat-int?))  ;; nil if unknown
(s/def :fs/used (s/nilable nat-int?))   ;; nil if unknown
(s/def :fs/available (s/nilable nat-int?)) ;; nil if unknown
(s/def :fs/capacity (s/double-in :min 0.0 :max 1.0 :NaN? false :infinite? false))

(s/def ::filesystem-entry
  (s/keys :req-un [:fs/device :fs/mount-point :fs/type :fs/options]
          :opt-un [:fs/size :fs/used :fs/available :fs/capacity]))

(s/def :filesystem/filesystems (s/coll-of ::filesystem-entry :kind vector?))
(s/def :filesystem/features (s/map-of keyword? any?))

(s/def ::filesystem
  (s/keys :req-un [:filesystem/filesystems :filesystem/features]))

;; ---------------------------------------------------------------
;; Network
;; ---------------------------------------------------------------

(s/def :addr/address string?)
(s/def :addr/prefix nat-int?)
(s/def ::ipv4-addr (s/keys :req-un [:addr/address :addr/prefix]))
(s/def ::ipv6-addr (s/keys :req-un [:addr/address :addr/prefix]))

(s/def :iface/name string?)
(s/def :iface/mac (s/nilable string?))
(s/def :iface/mtu nat-int?)  ;; 0 is valid (e.g. gif0, stf0, UHC* pseudo-devices)
(s/def :iface/status keyword?)
(s/def :iface/loopback? boolean?)
(s/def :iface/ipv4 (s/coll-of ::ipv4-addr :kind vector?))
(s/def :iface/ipv6 (s/coll-of ::ipv6-addr :kind vector?))

(s/def ::interface
  (s/keys :req-un [:iface/name :iface/mac :iface/mtu :iface/status
                    :iface/loopback? :iface/ipv4 :iface/ipv6]))

(s/def :network/interfaces (s/map-of string? ::interface))

;; TODO: fix this: key differs: :network (Linux) vs :gateway (macOS)
(s/def :gateway/network string?)
(s/def :gateway/gateway string?)

(s/def :gateway/interface string?)
(s/def :network/default-gateway
  (s/keys :req-un [:gateway/interface]
          :opt-un [:gateway/network :gateway/gateway]))

(s/def :dns/nameservers (s/coll-of string? :kind vector?))
(s/def :dns/search (s/coll-of string? :kind vector?))
(s/def :network/dns (s/keys :req-un [:dns/nameservers :dns/search]))

(s/def :network/hostname string?)

(s/def ::network
  (s/keys :req-un [:network/hostname :network/interfaces
                    :network/default-gateway :network/dns]))

;; ---------------------------------------------------------------
;; Paths (arbitrary set of tool-name -> filesystem-path)
;; ---------------------------------------------------------------

(s/def ::paths (s/map-of keyword? string?))

;; ---------------------------------------------------------------
;; Top-level record
;; ---------------------------------------------------------------

(s/def ::system
  (s/keys :req-un [::shell ::os ::hardware ::users ::filesystem
                    ::network ::paths]))

 ;; The outer structure is `{"host:port" {...system-map...}}`.
;; Spec the host key as any string and validate the value against ::system.
(s/def ::host-key string?)
(s/def ::provisioning-response
  (s/map-of ::host-key ::system :count 1))
