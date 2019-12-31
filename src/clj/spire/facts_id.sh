: 'This script aims at recognizing all Bourne compatible shells.
   Emphasis is on shells without any version variables.
   Please comment to mascheck@in-ulm.de'
: '$Id: whatshell.sh,v 1.26 2018/02/18 23:38:06 xmascheck Exp xmascheck $'
: 'fixes are tracked on www.in-ulm.de/~mascheck/various/whatshell/'

LC_ALL=C; export LC_ALL
: 'trivial cases first, yet parseable for historic shells'
case $BASH_VERSION in *.*) { echo "bash $BASH_VERSION";exit;};;esac
case $ZSH_VERSION  in *.*) { echo "zsh $ZSH_VERSION";exit;};;esac
case "$VERSION" in *zsh*) { echo "$VERSION";exit;};;esac
case  "$SH_VERSION" in *PD*) { echo "$SH_VERSION";exit;};;esac
case "$KSH_VERSION" in *PD*|*MIRBSD*) { echo "$KSH_VERSION";exit;};;esac
case "$POSH_VERSION" in 0.[1234]|0.[1234].*) \
     { echo "posh $POSH_VERSION, possibly slightly newer, yet<0.5";exit;}
  ;; *.*|*POSH*) { echo "posh $POSH_VERSION";exit;};; esac
case $YASH_VERSION in *.*) { echo "yash $YASH_VERSION";exit;};;esac

: 'traditional Bourne shell'
(eval ': $(:)') 2>/dev/null || {
  case `(:^times) 2>&1` in *0m*):;;
    *)p=' (and pipe check for Bourne shell failed)';;esac
  : 'pre-SVR2: no functions, no echo built-in.'
  (eval 'f(){ echo :; };f') >/dev/null 2>&1 || {
    ( eval '# :' ) 2>/dev/null || { echo '7th edition Bourne shell'"$p";exit;}
    ( : ${var:=value} ) 2>/dev/null ||
    { echo '7th edition Bourne shell, # comments (BSD, or port)'"$p";exit;}
    set x x; shift 2;test "$#" != 0 && { echo 'System III Bourne shell'"$p";exit;}
    { echo 'SVR1 Bourne shell'"$p";exit;}
  }
}; : 'keep syntactical block small for pre-SVR2'

myex(){ echo "$@";exit;} # "exec echo" might call the external command

(eval ': $(:)') 2>/dev/null || {
  (set -m; set +m) 2>/dev/null && {
    priv=0;(priv work)>/dev/null 2>&1 &&
      case `(priv work)2>&1` in *built*|*found*) priv=1;;esac
    read_r=0;(echo|read -r dummy 2>/dev/null) && read_r=1
    a_read=0;unset var;set -a;read var <&-;case `export` in
       *var*) a_read=1;;esac
    case_in=0;(eval 'case x in in) :;;esac')2>/dev/null && case_in=1
    ux=0;a=`(notexistent_cmd) 2>&1`; case $a in *UX*) ux=1;;esac
    case $priv$ux$read_r$a_read$case_in in
       11110) myex 'SVR4.2 MP2 Bourne shell'
    ;; 11010) myex 'SVR4.2 Bourne shell'
    ;; 10010|01010) myex 'SVR4.x Bourne shell (between 4.0 and 4.2)'
    ;; 00111) myex 'SVR4 Bourne shell (SunOS 5 schily variant, before 2016-02-02)'
    ;; 00101) myex 'SVR4 Bourne shell (SunOS 5 heirloom variant)'
    ;; 00001) myex 'SVR4 Bourne shell (SunOS 5 variant)'
    ;; 00000) myex 'SVR4.0 Bourne shell'
    ;; *)     myex 'unknown SVR4 Bourne shell variant' ;;esac
  }
  r=0; case `(read) 2>&1` in *"missing arguments"*) r=1;;esac
  g=0; (set -- -x; getopts x var) 2>/dev/null && g=1
  case $r$g in
     11) myex 'SVR3 Bourne shell'
  ;; 10) myex 'SVR3 Bourne shell (but getopts built-in is missing)'
  ;; 01) myex 'SVR3 Bourne shell (but read built-in does not match)'
  ;; 00) (builtin :) >/dev/null 2>&1 &&
        myex '8th edition (SVR2) Bourne shell'"$p"
        (type :) >/dev/null 2>&1 && myex 'SVR2 Bourne shell'"$p" ||
        myex 'SVR2 shell (but type built-in is missing)'"$p"
  ;;esac
}

case $( (:^times) 2>&1) in *0m*)
  case `eval '(echo $((1+1))) 2>/dev/null'` in
    2) myex 'SVR4 Bourne shell (SunOS 5 schily variant, posix-like, since 2016-05-24)'
  ;;*) myex 'SVR4 Bourne shell (SunOS 5 schily variant, since 2016-02-02, before 2016-05-24)'
  ;;esac
;;esac

type -F >/dev/null 2>&1 &&
  myex 'SVR4 Bourne shell (SunOS 5 schily variant, since 2016-08-08, in posix mode)'

# Almquist shell aka ash
(typeset -i var) 2>/dev/null || {
  case $SHELLVERS in "ash 0.2") myex 'original ash';;esac
  test "$1" = "debug" && debug=1
  n=1; case `(! :) 2>&1` in *not*) n=0;;esac
  b=1; case `echo \`:\` ` in '`:`') b=0;;esac
  g=0; { set -- -x; getopts x: var
         case $OPTIND in 2) g=1;;esac;} >/dev/null 2>&1
  p=0; (eval ': ${var#value}') 2>/dev/null && p=1
  r=0; ( (read</dev/null)) 2>/dev/null; case $? in 0|1|2)
          var=`(read</dev/null)2>&1`; case $var in *arg*) r=1;;esac
        ;;esac
  v=1; set x; case $10 in x0) v=0;;esac
  t=0; (PATH=;type :) >/dev/null 2>&1 && t=1
  test -z "$debug" || echo debug '$n$b$g$p$r$v$t: ' $n$b$g$p$r$v$t
  case $n$b$g$p$r$v$t in
     00*) myex 'early ash (4.3BSD, 386BSD 0.0-p0.2.3/NetBSD 0.8)'
  ;; 010*) myex 'early ash (ash-0.2 port, Slackware 2.1-8.0,'\
        '386BSD p0.2.4, NetBSD 0.9)'
  ;; 1110100) myex 'early ash (Minix 2.x-3.1.2)'
  ;; 1000000) myex 'early ash (4.4BSD Alpha)'
  ;; 1100000) myex 'early ash (4.4BSD)'
  ;; 11001*) myex 'early ash (4.4BSD Lite, early NetBSD 1.x, BSD/OS 2.x)'
  ;; 1101100) myex 'early ash (4.4BSD Lite2, BSD/OS 3 ff)'
  ;; 1101101) myex 'ash (FreeBSD, Cygwin pre-1.7, Minix 3.1.3 ff)'
  ;; esac
  e=0; case `(PATH=;exp 0)2>&1` in 0) e=1;;esac
  n=0; case y in [^x]) n=1;;esac
  r=1; case `(PATH=;noexist 2>/dev/null) 2>&1` in
        *not*) r=0 ;; *file*) r=2 ;;esac
  f=0; case `eval 'for i in x;{ echo $i;}' 2>/dev/null` in x) f=1;;esac
  test -z "$debug" || echo debug '$e$n$r$a$f: ' $e$n$r$a$f
  case $e$n$r$f in
     1100) myex 'ash (dash 0.3.8-30 - 0.4.6)'
  ;; 1110) myex 'ash (dash 0.4.7 - 0.4.25)'
  ;; 1010) myex 'ash (dash 0.4.26 - 0.5.2)'
  ;; 0120|1120|0100) myex 'ash (Busybox 0.x)'
  ;; 0110) myex 'ash (Busybox 1.x)'
  ;;esac
  a=0; case `eval 'x=1;(echo $((x)) )2>/dev/null'` in 1) a=1;;esac
  x=0; case `f(){ echo $?;};false;f` in 1) x=1;;esac
  c=0; case `echo -e '\x'` in *\\x) c=1;;esac
  test -z "$debug" || echo debug '$e$n$r$f$a$x$c: ' $e$n$r$f$a$x$c
  case $e$n$r$f$a$x$c in
     1001010) myex 'ash (Slackware 8.1 ff, dash 0.3.7-11 - 0.3.7-14)'
  ;; 10010??) myex 'ash (dash 0.3-1 - 0.3.7-10, NetBSD 1.2 - 3.1/4.0)'
  ;; 10011*)  myex 'ash (NetBSD 3.1/4.0 ff)'
  ;; 00101*)  myex 'ash (dash 0.5.5.1 ff)'
  ;; 00100*)  myex 'ash (dash 0.5.3-0.5.5)'
  ;;      *)  myex 'ash (unknown)'
  ;;esac
}

savedbg=$! # save unused $! for a later check

# Korn shell ksh93, $KSH_VERSION not implemented before 93t'
# protected: fatal substitution error in non-ksh
( eval 'test "x${.sh.version}" != x' ) 2>/dev/null &
wait $! && { eval 'PATH=;case $(XtInitialize 2>&1) in Usage*)
    DTKSH=" (dtksh/CDE variant)";;esac
    myex "ksh93 ${.sh.version}${DTKSH}"'; }

# Korn shell ksh86/88
_XPG=1;test "`typeset -Z2 x=0; echo $x`" = '00' && {
  case `print -- 2>&1` in *"bad option"*)
    myex 'ksh86 Version 06/03/86(/a)';; esac
  test "$savedbg" = '0'&& myex 'ksh88 Version (..-)11/16/88 (1st release)'
  test ${x-"{a}"b} = '{ab}' && myex 'ksh88 Version (..-)11/16/88a'
  case "`for i in . .; do echo ${i[@]} ;done 2>&1`" in
    "subscript out of range"*)
    myex 'ksh88 Version (..-)11/16/88b or c' ;; esac
  test "`whence -v true`" = 'true is an exported alias for :' &&
    myex 'ksh88 Version (..-)11/16/88d'
  test "`(cd /dev/null 2>/dev/null; echo $?)`" != '1' &&
    myex 'ksh88 Version (..-)11/16/88e'
  test "`(: $(</file/notexistent); echo x) 2>/dev/null`" = '' &&
    myex 'ksh88 Version (..-)11/16/88f'
   case `([[ "-b" > "-a" ]]) 2>&1` in *"bad number"*) \
    myex 'ksh88 Version (..-)11/16/88g';;esac # fixed in OSR5euc
  test "`cd /dev;cd -P ..;pwd 2>&1`" != '/' &&
    myex 'ksh88 Version (..-)11/16/88g' # fixed in OSR5euc
  test "`f(){ typeset REPLY;echo|read;}; echo dummy|read; f;
     echo $REPLY`" = "" && myex 'ksh88 Version (..-)11/16/88h'
  test $(( 010 )) = 8 &&
    myex 'ksh88 Version (..-)11/16/88i (posix octal base)'
  myex 'ksh88 Version (..-)11/16/88i'
}

echo 'unknown shell'
