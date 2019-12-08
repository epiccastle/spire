set -e

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

exit 0
