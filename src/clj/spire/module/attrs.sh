set -e

if [ ! -f "$FILE" ]; then
  echo -n "File not found." 1>&2
  exit 1
fi

FILE_OWNER_NAME=$(stat -c '%U' "$FILE")
FILE_OWNER_ID=$(stat -c '%u' "$FILE")
FILE_GROUP_NAME=$(stat -c '%G' "$FILE")
FILE_GROUP_ID=$(stat -c '%g' "$FILE")
FILE_MODE_OCTAL=$(stat -c '%a' "$FILE")
FILE_ATTRS=$(lsattr "$FILE" | awk '{print $1}')

EXIT=0

if [ "$OWNER_NAME" ] && [ "$OWNER_NAME" != "$FILE_OWNER_NAME" ]; then
  chown "$OWNER_NAME" "$FILE"
  EXIT=-1
elif [ "$OWNER_ID" ] && [ "$OWNER_ID" != "$FILE_OWNER_ID" ]; then
  chown "$OWNER_ID" "$FILE"
  EXIT=-1
fi

if [ "$GROUP_NAME" ] && [ "$GROUP_NAME" != "$FILE_GROUP_NAME" ]; then
  chgrp "$GROUP_NAME" "$FILE"
  EXIT=-1
elif [ "$GROUP_ID" ] && [ "$GROUP_ID" != "$FILE_GROUP_ID" ]; then
  chgrp "$GROUP_ID" "$FILE"
  EXIT=-1
fi

if [ "$MODE_OCTAL" ] && [ "$MODE_OCTAL" != "$FILE_MODE_OCTAL" ]; then
  chmod "$MODE_OCTAL" "$FILE"
  EXIT=-1
elif [ "$MODE_FLAGS" ]; then
  chmod "$MODE_FLAGS" "$FILE"
  NEW_MODE=$(stat -c '%a' "$FILE")
  if [ "$NEW_MODE" != "$FILE_MODE_OCTAL" ]; then
    EXIT=-1
  fi
fi

if [ "$ATTRS" ] && [ "$ATTRS" != "$FILE_ATTRS" ]; then
  chattr "$ATTRS" "$FILE"
  EXIT=-1
fi

exit $EXIT
