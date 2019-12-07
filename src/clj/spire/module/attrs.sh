set -e

if [ ! -f "$FILE" ]; then
  echo -n "File not found." 1>&2
  exit 1
fi

FILE_OWNER_ID=$(stat -c '%u' "$FILE")
FILE_GROUP_ID=$(stat -c '%g' "$FILE")
FILE_MODE_OCTAL=$(stat -c '%a' "$FILE")
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

NEW_FILE_OWNER_ID=$(stat -c '%u' "$FILE")
NEW_FILE_GROUP_ID=$(stat -c '%g' "$FILE")
NEW_FILE_MODE_OCTAL=$(stat -c '%a' "$FILE")
NEW_FILE_ATTRS=$(lsattr "$FILE" | awk '{print $1}')

if [ "$FILE_OWNER_ID" != "$NEW_FILE_OWNER_ID" ] || [ "$FILE_OWNER_ID" != "$NEW_FILE_OWNER_ID" ] || [ "$FILE_MODE_OCTAL" != "$NEW_FILE_MODE_OCTAL" ] || [ "$FILE_ATTRS" != "$NEW_FILE_ATTRS" ]; then
  exit -1
fi

exit 0
