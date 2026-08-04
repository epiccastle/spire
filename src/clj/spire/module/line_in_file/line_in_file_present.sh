# line_in_file_present.sh
# Combined POSIX script supporting both GNU and BSD sed.

# Detect sed in-place editing variant and define helper functions.
__spire_tmp=$(mktemp 2>/dev/null || echo "/tmp/spire_sed_detect_$$")
printf 'x\n' > "$__spire_tmp"
if sed -i 's/x/y/' "$__spire_tmp" 2>/dev/null && [ "$(cat "$__spire_tmp")" = "y" ]; then
  # GNU sed: text may follow a/i/c on the same line, -i takes no arg
  __spire_sed_change() { sed -i "${1}c${2}" "$FILE"; }
  __spire_sed_append() { sed -i "${1}a${2}" "$FILE"; }
  __spire_sed_insert() { sed -i "${1}i${2}" "$FILE"; }
  __spire_sed_insert1() { sed -i "1i${1}" "$FILE"; }
  __spire_sed_append_eof() { sed -i "\$a${1}" "$FILE"; }
elif printf 'x\n' > "$__spire_tmp" && sed -i '' 's/x/y/' "$__spire_tmp" 2>/dev/null && [ "$(cat "$__spire_tmp")" = "y" ]; then
  # BSD sed: text must be on the line after a/i/c\, -i requires an arg
  __spire_sed_change() { sed -i '' "${1}c\\
${2}\\
" "$FILE"; }
  __spire_sed_append() { sed -i '' "${1}a\\
${2}\\
" "$FILE"; }
  __spire_sed_insert() { sed -i '' "${1}i\\
${2}\\
" "$FILE"; }
  __spire_sed_insert1() { sed -i '' "1i\\
${1}\\
" "$FILE"; }
  __spire_sed_append_eof() { sed -i '' "\$a\\
${1}\\
" "$FILE"; }
else
  echo -n "Unable to detect sed variant." 1>&2
  rm -f "$__spire_tmp"
  exit 1
fi
rm -f "$__spire_tmp"

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

  LINECONTENT=$(sed -n "${LINENUM}p" "$FILE")
  if [ "$LINECONTENT" = "$LINE" ]; then
    exit 0
  else
    __spire_sed_change "$LINENUM" "$SEDLINE"
    exit -1
  fi
fi

# :present by regexp or string-match or line-match
if [ "$REGEX" ] || [ "$STRING_MATCH" ] || [ "$LINE_MATCH" ]; then
  if [ "$REGEX" ]; then
    LINENUMS=$(sed -n "${REGEX}=" "$FILE" | $SELECTOR)
  elif [ "$STRING_MATCH" ]; then
    LINENUMS=$(grep -n -F "${STRING_MATCH}" "$FILE" | $SELECTOR | cut -d: -f1)
  else
    LINENUMS=$(grep -n -x -F "${LINE_MATCH}" "$FILE" | $SELECTOR | cut -d: -f1)
  fi

  if [ "$LINENUMS" ]; then
    REVERSE=""
    for i in ${LINENUMS}; do REVERSE="${i} ${REVERSE}"; done
    EXIT=0
    for LINENUM in $REVERSE; do
      LINECONTENT=$(sed -n "${LINENUM}p" "$FILE")
      if [ "$LINECONTENT" != "$LINE" ]; then
        __spire_sed_change "$LINENUM" "$SEDLINE"
        EXIT=-1
      fi
    done
    exit $EXIT
  else
    if [ "$AFTER" ]; then
      MATCHPOINTS=$(sed -n "${AFTER}=" "$FILE" | $SELECTOR)
      REVERSE=""
      for i in ${MATCHPOINTS}; do REVERSE="${i} ${REVERSE}"; done
      EXIT=0
      for LINENUM in $REVERSE; do
        LINECONTENT=$(sed -n "$((LINENUM+1))p" "$FILE")
        if [ "$LINECONTENT" != "$LINE" ]; then
          __spire_sed_append "$LINENUM" "$SEDLINE"
          EXIT=-1
        fi
      done
      exit $EXIT
    elif [ "$BEFORE" ]; then
      MATCHPOINTS=$(sed -n "${BEFORE}=" "$FILE" | $SELECTOR)
      REVERSE=""
      for i in ${MATCHPOINTS}; do REVERSE="${i} ${REVERSE}"; done
      EXIT=0
      for LINENUM in $REVERSE; do
        LINECONTENT=$(if [ "$LINENUM" -gt 1 ]; then sed -n "$((LINENUM-1))p" "$FILE"; fi)
        if [ "$LINECONTENT" != "$LINE" ]; then
          __spire_sed_insert "$LINENUM" "$SEDLINE"
          EXIT=-1
        fi
      done
      exit $EXIT
    else
      if [ "$INSERTAT" = "bof" ]; then
        __spire_sed_insert1 "$SEDLINE"
        exit -1
      else
        __spire_sed_append_eof "$SEDLINE"
        exit -1
      fi
    fi
  fi
fi

echo "script error" 1>&2
exit 1
