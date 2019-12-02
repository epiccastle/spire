if [ ! -f "$FILE" ]; then
  echo -n "File not found." 1>&2
  exit 1
fi

if [ ! -r "$FILE" ]; then
  echo -n "File not readable." 1>&2
  exit 1
fi

LINECOUNT=$(wc -l "$FILE" | awk '{print $1}')

# :get by linenum
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
  echo $LINENUM
  sed -n "${LINENUM}p" "$FILE"
  exit 0
fi

# :get by regexp
if [ "$REGEX" ]; then
  LINENUMS=$(sed -n "${REGEX}=" "$FILE")
  if [ "$LINENUMS" ]; then
    SED_LP_CMD=$(echo $LINENUMS | sed 's/ /p;/g' | sed 's/$/p;/g')
    LINECONTENTS=$(sed -n "$SED_LP_CMD" "$FILE")
    echo $LINENUMS
    echo "$LINECONTENTS"
    exit 0
  else
    echo -n "no match"
    exit 0
  fi
fi

echo "script error" 1>&2
exit 1
