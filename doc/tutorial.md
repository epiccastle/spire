# Spire Tutorial

Install spire to your path

```shell-session
$ curl -O https://raw.githubusercontent.com/epiccastle/spire/master/scripts/install
$ bash install
```

### Create A Cloud Server

You can use your favourite cloud provider to create a linux Virtual Private Server. In this tutorial we take you through the use of Digital Ocean to create this server

[ create $5/month instance ]

 1. Create an account and login

 2. Upload your ssh public key (normally `~/.ssh/id_rsa.pub`) to digital ocean account

 3. Click on "Create" and then on "Droplets"

 4. Select the following:
     image: Ubuntu 18.04.3 (LTS) x64
     plan: standard - $5/mo
     datacenter region: choose a region where you want your VPN traffic to appear from
     additional option: Monitoring (IPv6)
     authentication: SSH keys, then select your key
     hostname: choose a name like countryname.mydomain.com where country name is the location endpoint you have chosen, and mydomain.com is your domain name. If you do not have a domain name you can put anything here.

 5. Click "Create Droplet"

 6. Wait for the machine to be created

 7. Write down the machines IP number. The machine I built has an IP of 159.203.119.225. Anywhere you see 159.203.119.225 from now on, **write your real IP number instead**

### Write A Blueprint To Provision The Cloud Server

Create the following blueprint. Create a file `wireguard.clj` with the following contents:

```clojure
(ssh "root@159.203.119.225"
    (get-fact [:system]))
```

Replace `159.203.119.225` with the IP number of your new cloud machine.

Run the blueprint with `spire` to connect and then report the type of system it is. When it asks "Are you sure you want to continue connecting?" answer by typing `y` and hitting enter.

```shell-session
$ spire wireguard.clj
The authenticity of host '159.203.119.225' can't be established.
RSA key fingerprint is 43:d6:ed:1e:86:26:f2:5a:8a:ed:06:35:99:a3:6f:8b.
Are you sure you want to continue connecting? y
{:codename :bionic,
 :description "Ubuntu 18.04.3 LTS",
 :distro :ubuntu,
 :os :linux,
 :platform :x86_64,
 :release "18.04",
 :shell :bash}
```

#### Install wireguard on the server

Now we know we can connect, lets provision the machine.

The installation instructions for wireguard [https://www.wireguard.com/install/] tell us we need to install a `wireguard` package from a ppa. Let's do that now. Change the `wireguard.clj` to read:

```clojure
(ssh "root@159.203.119.225"
    (apt-repo :present "ppa:wireguard/wireguard")
    (apt :update)
    (apt :install "wireguard"))
```

Let's run this to install wireguard...

```shell-session
$ spire wireguard.clj
wireguard.clj:2 (apt-repo :present "ppa:wireguard/wireguard") root@159.203.119.225
wireguard.clj:3 (apt :update) root@159.203.119.225
wireguard.clj:4 (apt :install "wireguard") root@159.203.119.225
{:err "",
 :exit 0,
...
}
```

#### Generate server keypair

Lets generate a key pair for the server and return it. We will run this on the server for now. `wireguard.clj` becomes:

```clojure
(require '[clojure.string :as string])

(ssh "root@159.203.119.225"
    (apt-repo :present "ppa:wireguard/wireguard")
    (apt :update)
    (apt :install "wireguard")
    (shell {:cmd "umask 077 && wg genkey | tee privatekey | wg pubkey > publickey"
            :creates ["privatekey" "publickey"]})
    {:private-key (string/trim (:out (get-file "privatekey")))
     :public-key (string/trim (:out (get-file "publickey")))})
```

```shell-session
$ spire wireguard.clj
wireguard.clj:4 (apt-repo :present "ppa:wireguard/wireguard") root@159.203.119.225
wireguard.clj:5 (apt :update) root@159.203.119.225
wireguard.clj:6 (apt :install "wireguard") root@159.203.119.225
wireguard.clj:7 (shell {:creates ["privatekey" "publickey"], :cmd "umask 077 && wg genkey | tee privatekey | wg pu
wireguard.clj:9 (get-file "privatekey") root@159.203.119.225
wireguard.clj:10 (get-file "publickey") root@159.203.119.225
{:private-key "8M5di1Sahxqe0hlnQMAKTN4YRx9mUMPC9upfGr9BcE8=",
 :public-key "rKjdEHcNNClS5cyhPpAx0/eKaswhxmJHrFMwx+5ZAn4="}
```

We will need to use these keys in setting up our local client.

#### Generate client keypair

** Note: This local installation requires you to be running ubuntu linux on the local client **

Lets connect to localhost and generate some client keys. We can break out some of our wireguard installer into some functions now to avoid repeating ourselves. Change `wireguard.clj` to:

```clojure
(require '[clojure.string :as string])

(defn install []
  (apt-repo :present "ppa:wireguard/wireguard")
  (apt :update)
  (apt :install "wireguard"))

(defn generate-keypair []
  (shell {:cmd "umask 077 && wg genkey | tee privatekey | wg pubkey > publickey"
          :creates ["privatekey" "publickey"]})
  {:private-key (string/trim (:out (get-file "privatekey")))
   :public-key (string/trim (:out (get-file "publickey")))})

(let [server-keys (ssh "root@159.203.119.225"
                       (install)
                       (generate-keypair))
      client-keys (ssh "root@localhost"
                       (install)
                       (generate-keypair))]
  {:server server-keys
   :client client-keys})
```

Now running this gives:

```shell-session
$ spire wireguard.clj
wireguard.clj:4 (apt-repo :present "ppa:wireguard/wireguard") root@159.203.119.225 root@localhost
wireguard.clj:5 (apt :update) root@159.203.119.225 root@localhost
wireguard.clj:6 (apt :install "wireguard") root@159.203.119.225 root@localhost
wireguard.clj:9 (shell {:creates ["privatekey" "publickey"], :cmd "umask 077 && wg genkey | tee privatekey | wg pu
wireguard.clj:11 (get-file "privatekey") root@159.203.119.225 root@localhost
wireguard.clj:12 (get-file "publickey") root@159.203.119.225 root@localhost
{:client {:private-key "YJaxgsPuQsWijT0lbcMCjDzBuC7OkDk7RK5DTUunpl0=",
          :public-key "NcOb0sNKGf4uXwH4W90geHVd7/eGyW8zYESfx9KZSR8="},
 :server {:private-key "8M5di1Sahxqe0hlnQMAKTN4YRx9mUMPC9upfGr9BcE8=",
          :public-key "rKjdEHcNNClS5cyhPpAx0/eKaswhxmJHrFMwx+5ZAn4="}}
```

#### Complete the setup of the client and server

Now we have all the information we need to setup both the client and server configurations.

Change `wireguard.clj` to read:

```clojure
(require '[clojure.string :as string])

(defn install []
  (apt-repo :present "ppa:wireguard/wireguard")
  (apt :update)
  (apt :install "wireguard"))

(defn generate-keypair []
  (shell {:cmd "umask 077 && wg genkey | tee privatekey | wg pubkey > publickey"
          :creates ["privatekey" "publickey"]})
  {:private (string/trim (:out (get-file "privatekey")))
   :public (string/trim (:out (get-file "publickey")))})

(let [server-keys (ssh "root@159.203.119.225"
                       (install)
                       (generate-keypair))
      client-keys (ssh "root@localhost"
                       (install)
                       (generate-keypair))]
  (ssh "root@159.203.119.225"
       (upload {:content (selmer "wireguard-server.conf"
                                 {:wan-ip "159.203.119.225"
                                  :private (:private server-keys)
                                  :peers [{:name "my desktop"
                                           :public (:public client-keys)
                                           :allowed-ips "10.20.30.40/32"
                                           :keepalive "120"}]})
                :dest "/etc/wireguard/wg0.conf"
                :mode 0600})
       (sysctl :present {:name "net.ipv4.ip_forward" :value "1"})
       (service :restarted {:name "wg-quick@wg0"}))
  (ssh "root@localhost"
       (upload {:content (selmer "wireguard-client.conf"
                                 {:wan-ip "10.20.30.40/24"
                                  :private (:private client-keys)
                                  :peer {:public (:public server-keys)
                                         :endpoint "159.203.119.225"}})
                :dest "/etc/wireguard/vpn-tunnel.conf"
                :mode 0600})))
```

You will need to write the server config templates.

In the same directory, put the following in `wireguard-server.conf`:

```jinja2
[Interface]
Address = {{ wan-ip }}
PrivateKey = {{ private }}
ListenPort = 51820

PostUp = iptables -A FORWARD -i %i -j ACCEPT; iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE
PostDown = iptables -D FORWARD -i %i -j ACCEPT; iptables -t nat -D POSTROUTING -o eth0 -j MASQUERADE

{% for peer in peers %}
# {{ peer.name }}
[Peer]
PublicKey = {{ peer.public }}
{% if peer.endpoint %}
Endpoint = {{ peer.endpoint }}
{% endif %}
{% if peer.allowed-ips %}
AllowedIPs = {{ peer.allowed-ips }}
{% endif %}
{% if peer.keepalive %}
PersistentKeepalive = {{ peer.keepalive }}
{% endif %}
{% endfor %}
```

Also put the following client config in `wireguard-client.conf`:

```jinja2
[Interface]
Address = {{ wan-ip }}
ListenPort = 51820
PrivateKey = {{ private }}

[Peer]
PublicKey = {{ peer.public }}
AllowedIPs = 0.0.0.0/0
Endpoint = {{ peer.endpoint }}:51820
PersistentKeepalive = 30
```

Now build the blueprint to finish the setup:

```shell-session
$ spire wireguard.clj
wireguard.clj:4 (apt-repo :present "ppa:wireguard/wireguard") root@159.203.119.225 root@localhost
wireguard.clj:5 (apt :update) root@159.203.119.225 root@localhost
wireguard.clj:6 (apt :install "wireguard") root@159.203.119.225 root@localhost
wireguard.clj:9 (shell {:creates ["privatekey" "publickey"], :cmd "umask 077 && wg genkey | tee privatekey | wg pu
wireguard.clj:11 (get-file "privatekey") root@159.203.119.225 root@localhost
wireguard.clj:12 (get-file "publickey") root@159.203.119.225 root@localhost
wireguard.clj:21 (upload {:content (selmer "wireguard-server.conf" {:private (:private server-keys), :peers [{:all
wireguard.clj:30 (sysctl :present {:name "net.ipv4.ip_forward", :value "1"}) root@159.203.119.225
wireguard.clj:31 (service :restarted {:name "wg-quick@wg0"}) root@159.203.119.225
wireguard.clj:33 (upload {:content (selmer "wireguard-client.conf" {:private (:private client-keys), :peer {:publi
{:attr-result {:result :ok}, :copy-result {:result :changed}, :result :changed}
```

Your setup is now complete. Try and start up the tunnel with

```shell-session
$ sudo service wg-quick@vpn-tunnel start
```

Now check that your vpn tunnel is working by opening a browser and going to [whatismypublicip.com](https://whatismypublicip.com/)

You should see your web browser is being seen by the internet with an IP of 159.203.119.225 in the remote country you started the server in!

Congratulations! You have built your own personal VPN service!
