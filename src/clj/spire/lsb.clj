(ns spire.lsb
  (:require [clojure.string :as string]))

;; utils for parsing files on the destination to work out the linux distro info

(defn lsb-redhat [{:keys [redhat-release
                          debian-version
                          uname-s]
                   :as args}]
  (let [[_ desc codename] (re-matches #"([^\(]+)\(([^\)]+)\)" redhat-release)
        words (string/split desc #"\s+")
        ]
    {:distro (-> words first string/lower-case keyword)
     :description redhat-release
     :codename (-> codename (string/replace #"\s+" "") string/lower-case keyword)
     :release (-> desc string/trim (string/split #"release\s+") last)
     }))

#_ (lsb-redhat {:redhat-release "Fedora release 31 (Thirty One)"})
#_ (lsb-redhat {:redhat-release "Fedora release 30 (Thirty)"})
#_ (lsb-redhat {:redhat-release "CentOS Linux release 8.1.1911 (Core)"})

(defn lsb-debian [{:keys [redhat-release
                          debian-version
                          uname-s]
                   :as args}]

  )

#_ (lsb-debian {:debian-version "stretch/sid"
                :uname-s "Linux"
                :dpkg-origins-default "Vendor: Ubuntu
Vendor-URL: http://www.ubuntu.com/
Bugs: https://bugs.launchpad.net/ubuntu/+filebug
Parent: Debian
"
                :distro-info
                {
                 :debian "version,codename,series,created,release,eol
1.1,Buzz,buzz,1993-08-16,1996-06-17,1997-06-05
1.2,Rex,rex,1996-06-17,1996-12-12,1998-06-05
1.3,Bo,bo,1996-12-12,1997-06-05,1999-03-09
2.0,Hamm,hamm,1997-06-05,1998-07-24,2000-03-09
2.1,Slink,slink,1998-07-24,1999-03-09,2000-10-30
2.2,Potato,potato,1999-03-09,2000-08-15,2003-07-30
3.0,Woody,woody,2000-08-15,2002-07-19,2006-06-30
3.1,Sarge,sarge,2002-07-19,2005-06-06,2008-03-30
4.0,Etch,etch,2005-06-06,2007-04-08,2010-02-15
5.0,Lenny,lenny,2007-04-08,2009-02-14,2012-02-06
6.0,Squeeze,squeeze,2009-02-14,2011-02-06,2014-05-31
7,Wheezy,wheezy,2011-02-06,2013-05-04,2016-04-26
8,Jessie,jessie,2013-05-04,2015-04-25,2018-06-06
9,Stretch,stretch,2015-04-25,2017-06-17
10,Buster,buster,2017-06-17
11,Bullseye,bullseye,2019-08-01
12,Bookworm,bookworm,2021-08-01
,Sid,sid,1993-08-16
,Experimental,experimental,1993-08-16
"
                 :ubuntu "version,codename,series,created,release,eol,eol-server,eol-esm
4.10,Warty Warthog,warty,2004-03-05,2004-10-20,2006-04-30
5.04,Hoary Hedgehog,hoary,2004-10-20,2005-04-08,2006-10-31
5.10,Breezy Badger,breezy,2005-04-08,2005-10-12,2007-04-13
6.06 LTS,Dapper Drake,dapper,2005-10-12,2006-06-01,2009-07-14,2011-06-01
6.10,Edgy Eft,edgy,2006-06-01,2006-10-26,2008-04-25
7.04,Feisty Fawn,feisty,2006-10-26,2007-04-19,2008-10-19
7.10,Gutsy Gibbon,gutsy,2007-04-19,2007-10-18,2009-04-18
8.04 LTS,Hardy Heron,hardy,2007-10-18,2008-04-24,2011-05-12,2013-05-09
8.10,Intrepid Ibex,intrepid,2008-04-24,2008-10-30,2010-04-30
9.04,Jaunty Jackalope,jaunty,2008-10-30,2009-04-23,2010-10-23
9.10,Karmic Koala,karmic,2009-04-23,2009-10-29,2011-04-29
10.04 LTS,Lucid Lynx,lucid,2009-10-29,2010-04-29,2013-05-09,2015-04-29
10.10,Maverick Meerkat,maverick,2010-04-29,2010-10-10,2012-04-10
11.04,Natty Narwhal,natty,2010-10-10,2011-04-28,2012-10-28
11.10,Oneiric Ocelot,oneiric,2011-04-28,2011-10-13,2013-05-09
12.04 LTS,Precise Pangolin,precise,2011-10-13,2012-04-26,2017-04-26,2017-04-26,2019-04-26
12.10,Quantal Quetzal,quantal,2012-04-26,2012-10-18,2014-05-16
13.04,Raring Ringtail,raring,2012-10-18,2013-04-25,2014-01-27
13.10,Saucy Salamander,saucy,2013-04-25,2013-10-17,2014-07-17
14.04 LTS,Trusty Tahr,trusty,2013-10-17,2014-04-17,2019-04-25,2019-04-25,2022-04-25
14.10,Utopic Unicorn,utopic,2014-04-17,2014-10-23,2015-07-23
15.04,Vivid Vervet,vivid,2014-10-23,2015-04-23,2016-01-23
15.10,Wily Werewolf,wily,2015-04-23,2015-10-22,2016-07-22
16.04 LTS,Xenial Xerus,xenial,2015-10-22,2016-04-21,2021-04-21,2021-04-21,2024-04-21
16.10,Yakkety Yak,yakkety,2016-04-21,2016-10-13,2017-07-20
17.04,Zesty Zapus,zesty,2016-10-13,2017-04-13,2018-01-13
17.10,Artful Aardvark,artful,2017-04-13,2017-10-19,2018-07-19
18.04 LTS,Bionic Beaver,bionic,2017-10-19,2018-04-26,2023-04-26,2023-04-26,2028-04-26
18.10,Cosmic Cuttlefish,cosmic,2018-04-26,2018-10-18,2019-07-18
19.04,Disco Dingo,disco,2018-10-18,2019-04-18,2020-01-18
19.10,Eoan Ermine,eoan,2019-04-18,2019-10-17,2020-07-17
20.04 LTS,Focal Fossa,focal,2019-10-17,2020-04-23,2025-04-23,2025-04-23,2030-04-23
"

                 }
                })

(defn lsb-release [{:keys [redhat-release
                           debian-version
                           uname-s]
                    :as args}]
  (cond
    redhat-release (lsb-redhat args)
    debian-version (lsb-debian args))
  )


(defn process-csv [csv]
  (let [[header & lines] (->> (string/split-lines csv)
                              (map #(string/split % #",")))]
    (->> lines
         (map (fn [line]
                (->> line
                     (map (fn [k v]
                            [(keyword k) v])
                          header)
                     (into {})))))))


#_ (process-debian-distro-info "version,codename,series,created,release,eol,eol-server,eol-esm
4.10,Warty Warthog,warty,2004-03-05,2004-10-20,2006-04-30
5.04,Hoary Hedgehog,hoary,2004-10-20,2005-04-08,2006-10-31
5.10,Breezy Badger,breezy,2005-04-08,2005-10-12,2007-04-13
6.06 LTS,Dapper Drake,dapper,2005-10-12,2006-06-01,2009-07-14,2011-06-01
6.10,Edgy Eft,edgy,2006-06-01,2006-10-26,2008-04-25
7.04,Feisty Fawn,feisty,2006-10-26,2007-04-19,2008-10-19
7.10,Gutsy Gibbon,gutsy,2007-04-19,2007-10-18,2009-04-18
8.04 LTS,Hardy Heron,hardy,2007-10-18,2008-04-24,2011-05-12,2013-05-09
8.10,Intrepid Ibex,intrepid,2008-04-24,2008-10-30,2010-04-30
9.04,Jaunty Jackalope,jaunty,2008-10-30,2009-04-23,2010-10-23
9.10,Karmic Koala,karmic,2009-04-23,2009-10-29,2011-04-29
10.04 LTS,Lucid Lynx,lucid,2009-10-29,2010-04-29,2013-05-09,2015-04-29
10.10,Maverick Meerkat,maverick,2010-04-29,2010-10-10,2012-04-10
11.04,Natty Narwhal,natty,2010-10-10,2011-04-28,2012-10-28
11.10,Oneiric Ocelot,oneiric,2011-04-28,2011-10-13,2013-05-09
12.04 LTS,Precise Pangolin,precise,2011-10-13,2012-04-26,2017-04-26,2017-04-26,2019-04-26
12.10,Quantal Quetzal,quantal,2012-04-26,2012-10-18,2014-05-16
13.04,Raring Ringtail,raring,2012-10-18,2013-04-25,2014-01-27
13.10,Saucy Salamander,saucy,2013-04-25,2013-10-17,2014-07-17
14.04 LTS,Trusty Tahr,trusty,2013-10-17,2014-04-17,2019-04-25,2019-04-25,2022-04-25
14.10,Utopic Unicorn,utopic,2014-04-17,2014-10-23,2015-07-23
15.04,Vivid Vervet,vivid,2014-10-23,2015-04-23,2016-01-23
15.10,Wily Werewolf,wily,2015-04-23,2015-10-22,2016-07-22
16.04 LTS,Xenial Xerus,xenial,2015-10-22,2016-04-21,2021-04-21,2021-04-21,2024-04-21
16.10,Yakkety Yak,yakkety,2016-04-21,2016-10-13,2017-07-20
17.04,Zesty Zapus,zesty,2016-10-13,2017-04-13,2018-01-13
17.10,Artful Aardvark,artful,2017-04-13,2017-10-19,2018-07-19
18.04 LTS,Bionic Beaver,bionic,2017-10-19,2018-04-26,2023-04-26,2023-04-26,2028-04-26
18.10,Cosmic Cuttlefish,cosmic,2018-04-26,2018-10-18,2019-07-18
19.04,Disco Dingo,disco,2018-10-18,2019-04-18,2020-01-18
19.10,Eoan Ermine,eoan,2019-04-18,2019-10-17,2020-07-17
20.04 LTS,Focal Fossa,focal,2019-10-17,2020-04-23,2025-04-23,2025-04-23,2030-04-23
")

#_ (process-csv
    "version,codename,series,created,release,eol
1.1,Buzz,buzz,1993-08-16,1996-06-17,1997-06-05
1.2,Rex,rex,1996-06-17,1996-12-12,1998-06-05
1.3,Bo,bo,1996-12-12,1997-06-05,1999-03-09
2.0,Hamm,hamm,1997-06-05,1998-07-24,2000-03-09
2.1,Slink,slink,1998-07-24,1999-03-09,2000-10-30
2.2,Potato,potato,1999-03-09,2000-08-15,2003-07-30
3.0,Woody,woody,2000-08-15,2002-07-19,2006-06-30
3.1,Sarge,sarge,2002-07-19,2005-06-06,2008-03-30
4.0,Etch,etch,2005-06-06,2007-04-08,2010-02-15
5.0,Lenny,lenny,2007-04-08,2009-02-14,2012-02-06
6.0,Squeeze,squeeze,2009-02-14,2011-02-06,2014-05-31
7,Wheezy,wheezy,2011-02-06,2013-05-04,2016-04-26
8,Jessie,jessie,2013-05-04,2015-04-25,2018-06-06
9,Stretch,stretch,2015-04-25,2017-06-17
10,Buster,buster,2017-06-17
11,Bullseye,bullseye,2019-08-01
12,Bookworm,bookworm,2021-08-01
,Sid,sid,1993-08-16
,Experimental,experimental,1993-08-16
")
