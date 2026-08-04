# line_in_file_absent.fish
# Fish shell version of the line-in-file absent script.

# Detect sed in-place editing variant and define helper function.
set __spire_tmp (mktemp 2>/dev/null; or echo "/tmp/spire_sed_detect_$fish_pid")
printf 'x\n' > $__spire_tmp
if sed -i 's/x/y/' $__spire_tmp 2>/dev/null; and test (cat $__spire_tmp) = y
  # GNU sed
  function __spire_sed_delete; sed -i "$argv[1]d" $FILE; end
else if printf 'x\n' > $__spire_tmp; and sed -i '' 's/x/y/' $__spire_tmp 2>/dev/null; and test (cat $__spire_tmp) = y
  # BSD sed
  function __spire_sed_delete; sed -i '' "$argv[1]d" $FILE; end
else
  echo -n "Unable to detect sed variant." >&2
  rm -f $__spire_tmp
  exit 1
end
rm -f $__spire_tmp

if not test -f "$FILE"
  echo -n "File not found." >&2
  exit 1
end

if not test -r "$FILE"
  echo -n "File not readable." >&2
  exit 1
end

set SELECTOR "head -1"
set LINECOUNT (wc -l "$FILE" | awk '{print $1}')

# :absent by linenum
if test -n "$LINENUM"
  if test "$LINENUM" -gt "$LINECOUNT"
    echo -n "No line number $LINENUM in file." >&2
    exit 2
  else if test "$LINENUM" -lt "-$LINECOUNT"
    echo -n "No line number $LINENUM in file." >&2
    exit 2
  else if test "$LINENUM" -lt 0
    set LINENUM (math "$LINECOUNT + $LINENUM + 1")
  end

  __spire_sed_delete "$LINENUM"
  exit 255
end

# :absent by regexp or string-match or line-match
if test -n "$REGEX"; or test -n "$STRING_MATCH"; or test -n "$LINE_MATCH"
  if test -n "$REGEX"
    set LINENUM (sed -n "${REGEX}=" "$FILE" | head -1)
  else if test -n "$STRING_MATCH"
    set LINENUM (grep -n -F "$STRING_MATCH" "$FILE" | cut -d: -f1 | head -1)
  else
    set LINENUM (grep -n -x -F "$LINE_MATCH" "$FILE" | cut -d: -f1 | head -1)
  end

  if test -n "$LINENUM"
    __spire_sed_delete "$LINENUM"
    exit 255
  else
    exit 0
  end
end
