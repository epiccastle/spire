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
  PRE_GROUPS=$(groups "$NAME")
  OGROUP=$(echo "$PRE_GROUPS" | cut -d: -f2 | awk '{print $1}')
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
    if [ "$MOVE_HOME" ]; then
      ARGS="${ARGS} -m"
      MOVING_HOME=true
    fi
    EXIT=-1
  fi

  if [ "$CREATE_HOME" ] && [ ! "$MOVING_HOME" ]; then
    # user already exists and we are being asked to create one
    eval $(useradd -D | grep SKEL)
    if [ "$HOME_DIR" ]; then
      SET_HOME_DIR="$HOME_DIR"
    else
      SET_HOME_DIR="$OHOME_DIR"
    fi
    if [ ! -d "$SET_HOME_DIR" ]; then
      # but they have no home dir
      if [ "$USER_ID" ]; then
        SET_USER_ID="$USER_ID"
      else
        SET_USER_ID="$OUSER_ID"
      fi
      if [ "$GROUP" ]; then
        SET_GID="$GROUP"
      else
        SET_GID="$OGID"
      fi
      cp -r "$SKEL" "$SET_HOME_DIR"
      chown -R "$SET_USER_ID:$SET_GID" "$SET_HOME_DIR"
      EXIT=-1
    fi
  fi

  if [ "$GROUP" ] && [ "$GROUP" != "$OGROUP" ]; then
    ARGS="${ARGS} -g '$GROUP'"
    EXIT=-1
  fi

  if [ "$GROUPSET" ]; then
    ARGS="${ARGS} -G '$GROUPSET'"
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
  PRE_GROUPS=""
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

  if [ "$GROUPSET" ]; then
    ARGS="${ARGS} -G '$GROUPSET'"
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

  if [ "$CREATE_HOME" ]; then
    ARGS="${ARGS} -m"
    EXIT=-1
  fi

  COMMAND="useradd${ARGS} '$NAME'"
fi

# echo "command:"
# echo "$COMMAND"
eval $COMMAND
POST_GROUPS=$(groups "$NAME")
if [ "$PRE_GROUPS" != "$POST_GROUPS" ]; then
  EXIT=-1
fi
exit $EXIT
