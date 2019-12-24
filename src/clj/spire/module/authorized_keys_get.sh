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
fi

cat "$AUTHORIZED_KEYS_FILE" || true
exit 0
