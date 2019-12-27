set -e

GROUP_LINE=$(grep "^${NAME}:" /etc/group || true)

if [ "$GROUP_LINE" ]; then
  IFS=':' read -r -a PARTS <<< "$GROUP_LINE"

  OPASSWORD="${PARTS[1]}"
  OGID="${PARTS[2]}"
  OUSERLIST="${PARTS[3]}"

  # echo 1 $OUID $USER_ID
  # echo 2 $OGID $GROUP
  # echo 3 $OCOMMENT $COMMENT
  # echo 4 $OHOME_DIR $HOME_DIR
  # echo 5 $OSHELL $SHELL

  ARGS=""
  EXIT=0

  if [ "$GID" ] && [ "$GID" != "$OGID" ]; then
    ARGS="${ARGS} -g '$GID'"
    EXIT=-1
  fi

  if [ "$PASSWORD" ] && [ "$PASSWORD" != "$OPASSWORD" ]; then
    ARGS="${ARGS} -p '$PASSWORD'"
    EXIT=-1
  fi

  if [ "$ARGS" ]; then
    COMMAND="groupmod${ARGS} '$NAME'"
  else
    COMMAND="true"
  fi
else
  # group does not exist
  ARGS=""
  EXIT=0

    if [ "$GID" ] && [ "$GID" != "$OGID" ]; then
    ARGS="${ARGS} -g '$GID'"
    EXIT=-1
  fi

  if [ "$PASSWORD" ] && [ "$PASSWORD" != "$OPASSWORD" ]; then
    ARGS="${ARGS} -p '$PASSWORD'"
    EXIT=-1
  fi

  COMMAND="groupadd${ARGS} '$NAME'"
fi

# echo "command:"
# echo "$COMMAND"
eval $COMMAND
exit $EXIT
