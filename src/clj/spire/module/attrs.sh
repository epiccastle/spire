set -e

if [ "$RECURSE" ]; then
  if [ ! -f "$FILE" ] && [ ! -d "$FILE" ]; then
    echo -n "File or directory not found." 1>&2
    exit 1
  fi

  FILE_STAT=$(find "$FILE" -exec stat -c '%u %g %a %n' {} \;)
  FILE_ATTRS=$(find "$FILE" -exec lsattr {} \;)

  if [ "$OWNER" ]; then
    find "$FILE" -exec chown "$OWNER" {} \;
  fi

  if [ "$GROUP" ]; then
    find "$FILE" -exec chgrp "$GROUP" {} \;
  fi

  if [ "$MODE" ]; then
    find "$FILE" -type f -exec chmod "$MODE" {} \;

    if [ ! "$DIR_MODE" ]; then
      find "$FILE" -type d -exec chmod "$MODE" {} \;
    fi
  fi

  if [ "$DIR_MODE" ]; then
    find "$FILE" -type d -exec chmod "$DIR_MODE" {} \;
  fi

  if [ "$ATTRS" ]; then
    find "$FILE" -exec chattr "$ATTRS" {} \;
  fi

  NEW_FILE_STAT=$(find "$FILE" -exec stat -c '%u %g %a %n' {} \;)
  NEW_FILE_ATTRS=$(find "$FILE" -exec lsattr {} \;)

  if [ "$FILE_STAT" != "$NEW_FILE_STAT" ] || [ "$FILE_ATTRS" != "$NEW_FILE_ATTRS" ]; then
    exit -1
  fi
else
  if [ ! -f "$FILE" ]; then
    echo -n "File not found." 1>&2
    exit 1
  fi

  FILE_STAT=$(stat -c '%u %g %a' "$FILE")
  FILE_ATTRS=$(lsattr "$FILE" | awk '{print $1}')

  if [ "$OWNER" ]; then
    chown "$OWNER" "$FILE"
  fi

  if [ "$GROUP" ]; then
    chgrp "$GROUP" "$FILE"
  fi

  if [ "$MODE" ]; then
    chmod "$MODE" "$FILE"
  fi

  if [ "$ATTRS" ]; then
    chattr "$ATTRS" "$FILE"
  fi

  NEW_FILE_STAT=$(stat -c '%u %g %a' "$FILE")
  NEW_FILE_ATTRS=$(lsattr "$FILE" | awk '{print $1}')

  if [ "$FILE_STAT" != "$NEW_FILE_STAT" ] || [ "$FILE_ATTRS" != "$NEW_FILE_ATTRS" ]; then
    exit -1
  fi
fi

exit 0
