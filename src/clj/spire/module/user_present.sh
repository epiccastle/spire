set -e

PWD_LINE=$(grep "^${NAME}:" /etc/passwd)

if [ "$PWD_LINE" ]; then
  IFS=':' read -r -a PARTS <<< "$PWD_LINE"

  OUID="${PARTS[2]}"
  OGID="${PARTS[3]}"
  OCOMMENT="${PARTS[4]}"
  OHOME="${PARTS[5]}"
  OSHELL="${PARTS[6]}"

  echo 1 $OUID $USER_ID
  echo 2 $OGID $GROUP
  echo 3 $OCOMMENT $COMMENT
  echo 4 $OHOME $HOME
  echo 5 $OSHELL $SHELL

  IFS=':' read -r -a SHADOW <<< $(grep "^${NAME}:" /etc/shadow)

  OPASSWORD="${SHADOW[1]}"
  OLAST_CHANGE="${SHADOW[2]}"
  OMIN_AGE="${SHADOW[3]}"
  OMAX_AGE="${SHADOW[4]}"
  OWARNING="${SHADOW[5]}"
  OINACTIVE="${SHADOW[6]}"
  OEXPIRE="${SHADOW[7]}"

  echo "pass: $OPASSWORD"
  echo "lastchange: $OLAST_CHANGE"
  echo "minage: $OMIN_AGE"
  echo "maxage: $OMAX_AGE"
  echo "warning: $OWARNING"
  echo "inactive: $OINACTIVE"
  echo "exp: $OEXPIRE"
fi
