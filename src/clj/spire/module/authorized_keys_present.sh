set -e

if [ "$USER" ]; then
  id -u "$USER" >& /dev/null
  if [ $? -ne 0 ]; then
    echo -n "User not found." 1>&2
    exit 1
  fi

  HOME_DIR=$(bash -c "echo ~${USER}")
  AUTHORIZED_KEYS_DIR="${HOME_DIR}/.ssh"
  AUTHORIZED_KEYS_FILE="${HOME_DIR}/.ssh/authorized_keys"
  GROUP=$(id -gn "${USER}")

  if [ ! -d "$AUTHORIZED_KEYS_DIR" ]; then
    mkdir -p "$AUTHORIZED_KEYS_DIR"
    chown "$USER:$GROUP" "$AUTHORIZED_KEYS_DIR"
    chmod 700 "$AUTHORIZED_KEYS_DIR"
  fi

  if [ ! -f "$AUTHORIZED_KEYS_FILE" ]; then
    touch "$AUTHORIZED_KEYS_FILE"
    chown "$USER:$GROUP" "$AUTHORIZED_KEYS_FILE"
    chmod 664 "$AUTHORIZED_KEYS_FILE"
  fi
fi

KEY_START=$(echo "$KEY" | cut -d " " -f 1-2)
KEY_FOUND=$(grep -F -n "$KEY_START" "$AUTHORIZED_KEYS_FILE" || true)

if [ "$OPTIONS" ]; then
  KEY_LINE="$OPTIONS $KEY"
else
  KEY_LINE="$KEY"
fi

if [ "$KEY_FOUND" ]; then
  # key already in file
  LINENUM=$(echo "$KEY_FOUND" | cut -d ":" -f 1)
  EXISTING=$(sed "${LINENUM}q;d" "$AUTHORIZED_KEYS_FILE")

  if [ "$KEY_LINE" == "$EXISTING" ]; then
    EXIT=0
  else
    sed -i "${LINENUM}c${KEY_LINE}" "$AUTHORIZED_KEYS_FILE"
    EXIT=-1
  fi

else
  # not found
  echo "$KEY_LINE" >> "$AUTHORIZED_KEYS_FILE"
  EXIT=-1
fi

cat "$AUTHORIZED_KEYS_FILE"
exit $EXIT
