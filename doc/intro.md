# Introduction to spire

TODO: write [great documentation](http://jacobian.org/writing/what-to-write/)

## Tutorial

Install spire to your path

    $ bash <(curl -s https://raw.githubusercontent.com/epiccastle/spire/master/install)

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

 7. Write down the machines IP number. We'll call this X.X.X.X from now on. Anywhere you see X.X.X.X from now on, write this real IP number instead

### Write A Blueprint To Provision The Cloud Server

Create the following blueprint. Create a file `wireguard.clj` with the following contents:

```clojure
(ssh "root@X.X.X.X"
    (get-fact [:system]))
```

Replace `X.X.X.X` with the IP number of your new cloud machine.

Run the blueprint with `spire` to connect and then report the type of system it is. When it asks "Are you sure you want to continue connecting?" answer by typing `y` and hitting enter.

    $ spire wireguard.clj
    The authenticity of host 'X.X.X.X' can't be established.
    RSA key fingerprint is 43:d6:ed:1e:86:26:f2:5a:8a:ed:06:35:99:a3:6f:8b.
    Are you sure you want to continue connecting? y
    {:codename :bionic,
     :description "Ubuntu 18.04.3 LTS",
     :distro :ubuntu,
     :os :linux,
     :platform :x86_64,
     :release "18.04",
     :shell :bash}

Now we know we can connect, lets provision the machine.

The installation instructions for wireguard [https://www.wireguard.com/install/] tell us we need to install a `wireguard` package from a ppa. Let's do that now. Change the `wireguard.clj` to read:

```clojure
(ssh "root@X.X.X.X"
    (apt-repo :present "ppa:wireguard/wireguard")
    (apt :update)
    (apt :install "wireguard"))
```

Let's run this to install wireguard...

    $ spire wireguard.clj

Lets generate a key pair for the server and return it. We will run this on the server for now. `wireguard.clj` becomes:

```clojure
(require '[clojure.string :as string])

(ssh "root@X.X.X.X"
    (apt-repo :present "ppa:wireguard/wireguard")
    (apt :update)
    (apt :install "wireguard")
    (shell {:cmd "umask 077 && wg genkey | tee privatekey | wg pubkey > publickey"
            :creates ["privatekey" "publickey"]})
    {:private-key (string/trim (:out (get-file "privatekey")))
     :public-key (string/trim (:out (get-file "publickey")))})
```

```
$ spire wireguard.clj
(apt-repo :present "ppa:wireguard/wireguard") root@X.X.X.X:22
(apt :update) root@X.X.X.X:22
(apt :install "wireguard") root@X.X.X.X:22
(shell {:creates ["privatekey" "publickey"], :cmd "umask 077 && wg genkey | tee privatekey | wg pubkey > publickey"}) root@X.X.X.X:22
(get-file "privatekey") root@X.X.X.X:22
(get-file "publickey") root@X.X.X.X:22
{:private-key "sMMp11XSZcPqAs4F62mNr5u1j8eXDe7aG5KHrt37Gmg=",
 :public-key "90uBkuU1tAMgR/qYwXrz+nZYFUx5qJbIVnv3AxE2DAo="}
```

We will need to use these keys in setting up our local client. Lets connect to localhost and generate some client keys. We can break out some of our wireguard installer into some functions now to avoid repeating ourselves. Change `wireguard.clj` to:

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

(let [server-keys (ssh "root@X.X.X.X"
                       (install)
                       (generate-keypair))
      client-keys (ssh "root@localhost"
                       (install)
                       (generate-keypair))]
  {:server server-keys
   :client client-keys})
```

Now running this gives:

```
$ spire wireguard.clj
(apt-repo :present "ppa:wireguard/wireguard") root@X.X.X.X:22 root@localhost:22
(apt :update) root@X.X.X.X:22 root@localhost:22
(apt :install "wireguard") root@X.X.X.X:22 root@localhost:22
(shell {:creates ["privatekey" "publickey"], :cmd "umask 077 && wg genkey | tee privatekey | wg pubkey > publickey"}) root@X.X.X.X:22 root@localhost:22
(get-file "privatekey") root@X.X.X.X:22 root@localhost:22
(get-file "publickey") root@X.X.X.X:22 root@localhost:22
{:client {:private-key "YJaxgsPuQsWijT0lbcMCjDzBuC7OkDk7RK5DTUunpl0=",
          :public-key "NcOb0sNKGf4uXwH4W90geHVd7/eGyW8zYESfx9KZSR8="},
 :server {:private-key "sMMp11XSZcPqAs4F62mNr5u1j8eXDe7aG5KHrt37Gmg=",
          :public-key "90uBkuU1tAMgR/qYwXrz+nZYFUx5qJbIVnv3AxE2DAo="}}
```

Now we have all the information we need to setup both the client and server configurations. Lets set up the server first.

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

(let [server-keys (ssh "root@139.59.92.63"
                       (install)
                       (generate-keypair))
      client-keys (ssh "root@localhost"
                       (install)
                       (generate-keypair))]
  (ssh "root@139.59.92.63"
       (upload {:content (selmer "wireguard-server.conf"
                                 {:wan-ip "139.59.92.63"
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
                                         :endpoint "139.59.92.63"}})
                :dest "/etc/wireguard/vpn-tunnel.conf"
                :mode 0600})))
```

You will need to write the server config templates.

In the same directory, put the following in `wireguard-server.conf`:

```ini
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

```ini
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

```
$ spire wireguard.clj
(apt-repo :present "ppa:wireguard/wireguard") root@139.59.92.63:22 root@localhost:22
(apt :update) root@139.59.92.63:22 root@localhost:22
(apt :install "wireguard") root@139.59.92.63:22 root@localhost:22
(shell {:creates ["privatekey" "publickey"], :cmd "umask 077 && wg genkey | tee privatekey | wg pubkey > publickey"}) root@139.59.92.63:22 root@localhost:22
(get-file "privatekey") root@139.59.92.63:22 root@localhost:22
(get-file "publickey") root@139.59.92.63:22 root@localhost:22
(upload {:content "[Interface]\nAddress = 139.59.92.63\nPrivateKey = sMMp11XSZcPqAs4F62mNr5u1j8eXDe7aG5KHrt37Gmg=\nListenPort = 51820\n\nPostUp = iptables -A FORWARD -i %i
(sysctl :present {:name "net.ipv4.ip_forward", :value "1"}) root@139.59.92.63:22
(service :restarted {:name "wg-quick@wg0"}) root@139.59.92.63:22
(upload {:content "[Interface]\nAddress = 10.20.30.40/24\nListenPort = 51820\nPrivateKey = YJaxgsPuQsWijT0lbcMCjDzBuC7OkDk7RK5DTUunpl0=\n\n[Peer]\nPublicKey = \nAllowedIPs
{:attr-result {:result :ok}, :copy-result {:result :changed}, :result :changed}
```

Your setup is now complete. Try and start up the tunnel with

```
$ sudo service wg-quick@vpn-tunnel start
```

Now check that your vpn tunnel is working by opening a browser and going to [https://whatismypublicip.com/]

You should see your web browser is being seen by the internet with an IP of X.X.X.X in the remote country you started the server in!

Congratulations! You have built your own personal vpn service!
