set -e

if [ ! -f "$FILE" ]; then
  echo -n "File not found." 1>&2
  exit 1
fi

if [ ! -r "$FILE" ]; then
  echo -n "File not readable." 1>&2
  exit 1
fi

LINENUM=$(sed -n "/${REGEX}/=" "$FILE" | head -1)
EXIT=0

# saved state
if [ "$LINENUM" ]; then
  LINECONTENT=$(sed -n "${LINENUM}p" "$FILE")
  IFS="= " read -ra PARTS <<< "$LINECONTENT"
  if [ "${PARTS[1]}" != "$VALUE" ]; then
    sed -i "${LINENUM}d" "$FILE"
    EXIT=-1
  fi
fi

# reload
if [ "$RELOAD" == "true" ]; then
  sysctl -p "$FILE"
  EXIT=-1
fi

if [ "$VALUE" ]; then
  # running state
  RUNNING=$(sysctl "$NAME")
  IFS="= " read -ra PARTS <<< "$RUNNING"
  if [ "${PARTS[1]}" != "$VALUE" ]; then
    sysctl "$NAME=$VALUE"
    EXIT=-1
  else
    sysctl "$NAME"
  fi
fi

exit $EXIT
