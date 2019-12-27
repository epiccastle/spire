set -e

PWD_LINE=$(grep "^${NAME}:" /etc/passwd || true)

if [ "$PWD_LINE" ]; then
  IFS=':' read -r -a PARTS <<< "$PWD_LINE"

  OUSER_ID="${PARTS[2]}"
  OGID="${PARTS[3]}"
  OCOMMENT="${PARTS[4]}"
  OHOME_DIR="${PARTS[5]}"
  OSHELL="${PARTS[6]}"

  # echo 1 $OUID $USER_ID
  # echo 2 $OGID $GROUP
  # echo 3 $OCOMMENT $COMMENT
  # echo 4 $OHOME_DIR $HOME_DIR
  # echo 5 $OSHELL $SHELL

  IFS=':' read -r -a SHADOW <<< $(grep "^${NAME}:" /etc/shadow)

  OPASSWORD="${SHADOW[1]}"
  OLAST_CHANGE="${SHADOW[2]}"
  OMIN_AGE="${SHADOW[3]}"
  OMAX_AGE="${SHADOW[4]}"
  OWARNING="${SHADOW[5]}"
  OINACTIVE="${SHADOW[6]}"
  OEXPIRE="${SHADOW[7]}"

  # echo "pass: $OPASSWORD"
  # echo "lastchange: $OLAST_CHANGE"
  # echo "minage: $OMIN_AGE"
  # echo "maxage: $OMAX_AGE"
  # echo "warning: $OWARNING"
  # echo "inactive: $OINACTIVE"
  # echo "exp: $OEXPIRE"

  ARGS=""
  EXIT=0

  if [ "$COMMENT" ] && [ "$COMMENT" != "$OCOMMENT" ]; then
    ARGS="${ARGS} -c '$COMMENT'"
    EXIT=-1
  fi

  if [ "$USER_ID" ] && [ "$USER_ID" != "$OUSER_ID" ]; then
    ARGS="${ARGS} -u '$USER_ID'"
    EXIT=-1
  fi

  if [ "$HOME_DIR" ] && [ "$HOME_DIR" != "$OHOME_DIR" ]; then
    ARGS="${ARGS} -d '$HOME_DIR'"
    if [ "$OPTS_MOVE_HOME" ]; then
      ARGS="${ARGS} -m"
    fi
    EXIT=-1
  fi

  if [ "$GROUP" ] && [ "$GROUP" != "$OGROUP" ]; then
    ARGS="${ARGS} -g '$GROUP'"
    EXIT=-1
  fi

  if [ "$PASSWORD" ] && [ "$PASSWORD" != "$OPASSWORD" ]; then
    ARGS="${ARGS} -p '$PASSWORD'"
    EXIT=-1
  fi

  if [ "$SHELL" ] && [ "$SHELL" != "$OSHELL" ]; then
    ARGS="${ARGS} -s '$SHELL'"
    EXIT=-1
  fi

  if [ "$ARGS" ]; then
    COMMAND="usermod${ARGS} '$NAME'"
  else
    COMMAND="true"
  fi
else
  # user does not exist
  ARGS=""
  EXIT=0

  if [ "$COMMENT" ]; then
    ARGS="${ARGS} -c '$COMMENT'"
    EXIT=-1
  fi

  if [ "$USER_ID" ]; then
    ARGS="${ARGS} -u '$USER_ID'"
    EXIT=-1
  fi

  if [ "$HOME_DIR" ]; then
    ARGS="${ARGS} -d '$HOME_DIR'"
    EXIT=-1
  fi

  if [ "$GROUP" ]; then
    ARGS="${ARGS} -g '$GROUP'"
    EXIT=-1
  fi

  if [ "$PASSWORD" ]; then
    ARGS="${ARGS} -p '$PASSWORD'"
    EXIT=-1
  fi

  if [ "$SHELL" ]; then
    ARGS="${ARGS} -s '$SHELL'"
    EXIT=-1
  fi

  COMMAND="useradd${ARGS} '$NAME'"
fi

# echo "command:"
# echo "$COMMAND"
eval $COMMAND
exit $EXIT
