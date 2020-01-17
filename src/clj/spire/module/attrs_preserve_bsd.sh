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

  FILE_TYPE_AND_MODE=$(stat -f '%p' "$FILE")
  FILE_MODE=${FILE_TYPE_AND_MODE:$((${#FILE_TYPE_AND_MODE}-3)):3}
  FILE_ACCESS=$(stat -f '%a' "$FILE")
  FILE_MODIFIED=$(stat -f '%m' "$FILE")

  if [ "$MODE" != "$FILE_MODE" ]; then
    chmod "$MODE" "$FILE"
    EXIT=-1
  fi

  if [ "$ACCESS" != "$FILE_ACCESS" ]; then
    touch -a -t "$ACCESS_STAMP" "$FILE"
    EXIT=-1
  fi

  if [ "$MODIFIED" != "$FILE_MODIFIED" ]; then
    touch -m -t "$MODIFIED_STAMP" "$FILE"
    EXIT=-1
  fi
}
