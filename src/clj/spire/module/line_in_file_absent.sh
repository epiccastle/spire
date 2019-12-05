if [ ! -f "$FILE" ]; then
  echo -n "File not found." 1>&2
  exit 1
fi

if [ ! -r "$FILE" ]; then
  echo -n "File not readable." 1>&2
  exit 1
fi

LINECOUNT=$(wc -l "$FILE" | awk '{print $1}')

# :absent by linenum
if [ "$LINENUM" ]; then
  if [ "$LINENUM" -gt "$LINECOUNT" ]; then
    echo -n "No line number $LINENUM in file." 1>&2
    exit 2
  elif [ "$LINENUM" -lt "-$LINECOUNT" ]; then
    echo -n "No line number $LINENUM in file." 1>&2
    exit 2
  elif [ "$LINENUM" -lt 0 ]; then
    LINENUM=$((LINECOUNT + LINENUM + 1))
  fi

  sed -i "${LINENUM}d${LINE}" "$FILE"
  exit 0
fi

# :absent by regexp
if [ "$REGEX" ]; then
  LINENUM=$(sed -n "${REGEX}=" "$FILE" | head -1)
  if [ "$LINENUM" ]; then
    sed -i "${LINENUM}d${LINE}" "$FILE"
    exit 0
  else
    exit 0
  fi
fi
