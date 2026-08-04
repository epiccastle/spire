# line_in_file_present.fish
# Fish shell version of the line-in-file present script.

# Detect sed in-place editing variant and define helper functions.
set __spire_tmp (mktemp 2>/dev/null; or echo "/tmp/spire_sed_detect_$fish_pid")
printf 'x\n' > $__spire_tmp
if sed -i 's/x/y/' $__spire_tmp 2>/dev/null; and test (cat $__spire_tmp) = y
  # GNU sed: text may follow a/i/c on the same line, -i takes no arg
  function __spire_sed_change; sed -i "$argv[1]c$argv[2]" $FILE; end
  function __spire_sed_append; sed -i "$argv[1]a$argv[2]" $FILE; end
  function __spire_sed_insert; sed -i "$argv[1]i$argv[2]" $FILE; end
  function __spire_sed_insert1; sed -i "1i$argv[1]" $FILE; end
  function __spire_sed_append_eof; sed -i "\$a$argv[1]" $FILE; end
else if printf 'x\n' > $__spire_tmp; and sed -i '' 's/x/y/' $__spire_tmp 2>/dev/null; and test (cat $__spire_tmp) = y
  # BSD sed: text must be on the line after a/i/c\, -i requires an arg
  function __spire_sed_change; sed -i '' "$argv[1]c\\
$argv[2]\\
" $FILE; end
  function __spire_sed_append; sed -i '' "$argv[1]a\\
$argv[2]\\
" $FILE; end
  function __spire_sed_insert; sed -i '' "$argv[1]i\\
$argv[2]\\
" $FILE; end
  function __spire_sed_insert1; sed -i '' "1i\\
$argv[1]\\
" $FILE; end
  function __spire_sed_append_eof; sed -i '' "\$a\\
$argv[1]\\
" $FILE; end
else
  echo -n "Unable to detect sed variant." >&2
  rm -f $__spire_tmp
  exit 1
end
rm -f $__spire_tmp

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

# :present by linenum
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

  set LINECONTENT (sed -n "${LINENUM}p" "$FILE")
  if test "$LINECONTENT" = "$LINE"
    exit 0
  else
    __spire_sed_change "$LINENUM" "$SEDLINE"
    exit 255
  end
end

# :present by regexp or string-match or line-match
if test -n "$REGEX"; or test -n "$STRING_MATCH"; or test -n "$LINE_MATCH"
  if test -n "$REGEX"
    set LINENUMS (sed -n "${REGEX}=" "$FILE" | __spire_select)
  else if test -n "$STRING_MATCH"
    set LINENUMS (grep -n -F "$STRING_MATCH" "$FILE" | __spire_select | cut -d: -f1)
  else
    set LINENUMS (grep -n -x -F "$LINE_MATCH" "$FILE" | __spire_select | cut -d: -f1)
  end

  if test (count $LINENUMS) -gt 0
    set REVERSE
    for i in $LINENUMS
      set REVERSE $i $REVERSE
    end
    set EXIT 0
    for LINENUM in $REVERSE
      set LINECONTENT (sed -n "${LINENUM}p" "$FILE")
      if test "$LINECONTENT" != "$LINE"
        __spire_sed_change "$LINENUM" "$SEDLINE"
        set EXIT 255
      end
    end
    exit $EXIT
  else
    if test -n "$AFTER"
      set MATCHPOINTS (sed -n "${AFTER}=" "$FILE" | __spire_select)
      set REVERSE
      for i in $MATCHPOINTS
        set REVERSE $i $REVERSE
      end
      set EXIT 0
      for LINENUM in $REVERSE
        set LINECONTENT (sed -n (math "$LINENUM + 1")p "$FILE")
        if test "$LINECONTENT" != "$LINE"
          __spire_sed_append "$LINENUM" "$SEDLINE"
          set EXIT 255
        end
      end
      exit $EXIT
    else if test -n "$BEFORE"
      set MATCHPOINTS (sed -n "${BEFORE}=" "$FILE" | __spire_select)
      set REVERSE
      for i in $MATCHPOINTS
        set REVERSE $i $REVERSE
      end
      set EXIT 0
      for LINENUM in $REVERSE
        if test "$LINENUM" -gt 1
          set LINECONTENT (sed -n (math "$LINENUM - 1")p "$FILE")
        else
          set LINECONTENT ""
        end
        if test "$LINECONTENT" != "$LINE"
          __spire_sed_insert "$LINENUM" "$SEDLINE"
          set EXIT 255
        end
      end
      exit $EXIT
    else
      if test "$INSERTAT" = "bof"
        __spire_sed_insert1 "$SEDLINE"
        exit 255
      else
        __spire_sed_append_eof "$SEDLINE"
        exit 255
      end
    end
  end
end

echo "script error" >&2
exit 1
