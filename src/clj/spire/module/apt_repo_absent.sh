set -e

EXIT=0
for FILE in $FILES; do
  LINENUM=$(sed -n "${REGEX}=" "$FILE" |  head -1)

  if [ "$LINENUM" ]; then
    EXIT=-1
    cp "$FILE" "${FILE}.save"
    sed -i "${LINENUM}c# ${LINE}" "$FILE"

    LINES_REMAIN=$(cat "$FILE" | grep -v "^\s*#" | grep -v "^\s*$" | wc -l)
    if [ "$LINES_REMAIN" -eq "0" ]; then
       rm "$FILE"
    fi
  fi
done
exit $EXIT
