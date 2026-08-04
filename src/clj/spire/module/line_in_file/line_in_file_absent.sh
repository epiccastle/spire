# line_in_file_absent.sh
# Combined POSIX script supporting both GNU and BSD sed.

# Detect sed in-place editing variant and define helper function.
__spire_tmp=$(mktemp 2>/dev/null || echo "/tmp/spire_sed_detect_$$")
printf 'x\n' > "$__spire_tmp"
if sed -i 's/x/y/' "$__spire_tmp" 2>/dev/null && [ "$(cat "$__spire_tmp")" = "y" ]; then
  # GNU sed
  __spire_sed_delete() { sed -i "${1}d" "$FILE"; }
elif printf 'x\n' > "$__spire_tmp" && sed -i '' 's/x/y/' "$__spire_tmp" 2>/dev/null && [ "$(cat "$__spire_tmp")" = "y" ]; then
  # BSD sed
  __spire_sed_delete() { sed -i '' "${1}d" "$FILE"; }
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

SELECTOR="head -1"
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

  __spire_sed_delete "$LINENUM"
  exit -1
fi

# :absent by regexp or string-match or line-match
if [ "$REGEX" ] || [ "$STRING_MATCH" ] || [ "$LINE_MATCH" ]; then
  if [ "$REGEX" ]; then
    LINENUM=$(sed -n "${REGEX}=" "$FILE" | $SELECTOR)
  elif [ "$STRING_MATCH" ]; then
    LINENUM=$(grep -n -F "${STRING_MATCH}" "$FILE" | cut -d: -f1 | $SELECTOR)
  else
    LINENUM=$(grep -n -x -F "${LINE_MATCH}" "$FILE" | cut -d: -f1 | $SELECTOR)
  fi

  if [ "$LINENUM" ]; then
    __spire_sed_delete "$LINENUM"
    exit -1
  else
    exit 0
  fi
fi
