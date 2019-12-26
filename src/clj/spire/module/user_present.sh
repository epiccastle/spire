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
fi
