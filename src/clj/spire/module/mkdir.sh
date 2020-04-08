set -e

if [ -e "$FILE" ]; then
  if [ -d "$FILE" ]; then
    FILE_STAT=$(stat -c "$STAT" "$FILE")

    if [ "$OWNER" ]; then
      chown "$OWNER" "$FILE"
    fi

    if [ "$GROUP" ]; then
      chgrp "$GROUP" "$FILE"
    fi

    if [ "$MODE" ]; then
      chmod "$MODE" "$FILE"
    fi

    NEW_FILE_STAT=$(stat -c "$STAT" "$FILE")

    if [ "$FILE_STAT" != "$NEW_FILE_STAT" ]; then
      exit -1
    fi

    exit 0
  fi

  echo "file exists and is not a directory" >&2
  exit 1

else
  mkdir -p "$FILE"
  if [ "$OWNER" ]; then
    chown "$OWNER" "$FILE"
  fi
  if [ "$GROUP" ]; then
    chgrp "$GROUP" "$FILE"
  fi
  if [ "$MODE" ]; then
    chmod "$MODE" "$FILE"
  fi
  exit -1
fi

exit 0
