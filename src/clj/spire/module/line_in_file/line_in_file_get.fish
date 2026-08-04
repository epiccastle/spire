# line_in_file_get.fish
# Fish shell version of the line-in-file get script.
# (get is read-only so no sed in-place variant detection is needed.)

# Define selector function based on SELECTOR value
switch "$SELECTOR"
  case 'head -1'
    function __spire_select; head -1; end
  case 'tail -1'
    function __spire_select; tail -1; end
  case '*'
    function __spire_select; cat; end
end

if not test -f "$FILE"
  echo -n "File not found." >&2
  exit 1
end

if not test -r "$FILE"
  echo -n "File not readable." >&2
  exit 1
end

set LINECOUNT (wc -l "$FILE" | awk '{print $1}')

# :get by linenum
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
  echo "$LINENUM"
  sed -n "${LINENUM}p" "$FILE"
  exit 0
end

# :get by regexp or string-match or line-match
if test -n "$REGEX"; or test -n "$STRING_MATCH"; or test -n "$LINE_MATCH"
  if test -n "$REGEX"
    set LINENUMS (sed -n "${REGEX}=" "$FILE" | __spire_select)
  else if test -n "$STRING_MATCH"
    set LINENUMS (grep -n -F "$STRING_MATCH" "$FILE" | cut -d: -f1 | __spire_select)
  else
    set LINENUMS (grep -n -x -F "$LINE_MATCH" "$FILE" | cut -d: -f1 | __spire_select)
  end

  if test (count $LINENUMS) -gt 0
    set SED_LP_CMD (echo "$LINENUMS" | sed 's/ /p;/g' | sed 's/$/p;/g')
    set LINECONTENTS (sed -n "$SED_LP_CMD" "$FILE")
    echo "$LINENUMS"
    echo "$LINECONTENTS"
    exit 0
  else
    echo -n "no match"
    exit 0
  end
end

echo "script error" >&2
exit 1
