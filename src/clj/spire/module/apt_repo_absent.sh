set -e

for FILE in $FILES; do
  LINENUMS=$(sed -n "${REGEX}=" "$FILE" |  head -1)

  if [ "$LINENUMS" ]; then
    echo "found: $FILE at line $LINENUMS"
  fi
done
