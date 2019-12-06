if [ ! -f "$FILE" ]; then
  echo -n "File not found." 1>&2
  exit 1
fi

if [ ! -r "$FILE" ]; then
  echo -n "File not readable." 1>&2
  exit 1
fi

SELECTOR="head -1"
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

  LINECONTENT=$(sed -n "${LINENUM}p" "$FILE")
  if [ "$LINECONTENT" == "$LINE" ]; then
      exit 0
  else
      sed -i "${LINENUM}c${LINE}" "$FILE"
      exit -1
  fi
fi

# :present by regexp
if [ "$REGEX" ]; then
  LINENUM=$(sed -n "${REGEX}=" "$FILE" | $SELECTOR)
  LINECONTENT=$(sed -n "${LINENUM}p" "$FILE")
  if [ "$LINECONTENT" == "$LINE" ]; then
    exit 0
  else
    if [ "$LINENUM" ]; then
      sed -i "${LINENUM}c${LINE}" "$FILE"
      exit -1
    elif [ "$AFTER" ]; then
      MATCHPOINT=$(sed -n "${AFTER}=" "$FILE" | $SELECTOR)
      if [ "$MATCHPOINT" ]; then
        sed -i "${MATCHPOINT}a${LINE}" "$FILE"
        exit -1
      else
        sed -i "\$a${LINE}" "$FILE"
        exit -1
      fi
    elif [ "$BEFORE" ]; then
      MATCHPOINT=$(sed -n "${BEFORE}=" "$FILE" | $SELECTOR)
      if [ "$MATCHPOINT" ]; then
        sed -i "${MATCHPOINT}i${LINE}" "$FILE"
        exit -1
      else
        sed -i "\$a${LINE}" "$FILE"
        exit -1
      fi
    else
      sed -i "\$a${LINE}" "$FILE"
      exit -1
    fi
  fi
fi

echo "script error" 1>&2
exit 1
