set -e

EXIT=0

function set_file
{
  MODE="$1"
  ACCESS="$2"
  ACCESS_STAMP="$3"
  MODIFIED="$4"
  MODIFIED_STAMP="$5"
  FILE="$6"

  echo "set_file: $FILE"

  FILE_MODE=$(stat -c '%a' "$FILE")
  FILE_ACCESS=$(stat -c '%X' "$FILE")
  FILE_MODIFIED=$(stat -c '%Y' "$FILE")

  if [ "$MODE" != "$FILE_MODE" ]; then
    chmod "$MODE" "$FILE"
    EXIT=-1
  fi

  if [ "$ACCESS" != "$FILE_ACCESS" ]; then
    touch -a -d "$ACCESS_STAMP" "$FILE"
    EXIT=-1
  fi

  if [ "$MODIFIED" != "$FILE_MODIFIED" ]; then
    touch -m -d "$MODIFIED_STAMP" "$FILE"
    EXIT=-1
  fi
}
