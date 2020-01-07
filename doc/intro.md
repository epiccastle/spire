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

Run the blueprint with `spire` to connect and then report the type of system it is. When it asks "Are you sure you want to continue connecting?" answer by typing `yes` and hitting enter.

    $ spire wireguard.clj
    The authenticity of host 'X.X.X.X' can't be established.
    RSA key fingerprint is 43:d6:ed:1e:86:26:f2:5a:8a:ed:06:35:99:a3:6f:8b.
    Are you sure you want to continue connecting? yes
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
    (ppa :present "ppa:wireguard/wireguard")
    (apt :update)
    (apt :install "wireguard"))
```

Let's run this to install wireguard...

    $ spire wireguard.clj

Lets generate a key. We will run this on the server for now. `wireguard.clj` becomes:

```clojure
(ssh "root@X.X.X.X"
    (ppa :present "ppa:wireguard/wireguard")
    (apt :update)
    (apt :install "wireguard")

    (shell {:cmd "umask 077 && wg genkey | tee privatekey | wg pubkey > publickey"
            :creates ["privatekey" "publickey"]})
    )
```
