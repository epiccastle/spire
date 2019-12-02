REGEX="%s"
FILE="%s"
LINENUM="%s"
LINE="%s"
AFTER="%s"
BEFORE="%s"

if [ ! -f "$FILE" ]; then
  echo -n "File not found." 1>&2
  exit 1
fi

if [ ! -r "$FILE" ]; then
  echo -n "File not readable." 1>&2
  exit 1
fi

LINECOUNT=$(wc -l "$FILE" | awk '{print $1}')

# :present by linenum
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

  sed -i "${LINENUM}c${LINE}" "$FILE"
  exit 0
fi

# :present by regexp
if [ "$REGEX" ]; then
  LINENUM=$(sed -n "${REGEX}=" "$FILE" | head -1)
  if [ "$LINENUM" ]; then
    sed -i "${LINENUM}c${LINE}" "$FILE"
    exit 0
  elif [ "$AFTER" ]; then
    MATCHPOINT=$(sed -n "${AFTER}=" "$FILE" | tail -1)
    if [ "$MATCHPOINT" ]; then
      sed -i "${MATCHPOINT}a${LINE}" "$FILE"
      exit 0
    else
      sed -i "\$a${LINE}" "$FILE"
      exit 0
    fi
  elif [ "$BEFORE" ]; then
    MATCHPOINT=$(sed -n "${BEFORE}=" "$FILE" | tail -1)
    if [ "$MATCHPOINT" ]; then
      sed -i "${MATCHPOINT}i${LINE}" "$FILE"
      exit 0
    else
      sed -i "\$a${LINE}" "$FILE"
      exit 0
    fi
  else
    sed -i "\$a${LINE}" "$FILE"
    exit 0
  fi
fi

echo "script error" 1>&2
exit 1
