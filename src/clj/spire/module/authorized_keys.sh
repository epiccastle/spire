set -e

if [ "$USER" ]; then
  id -u "$USER" >& /dev/null
  if [ $? -ne 0 ]; then
    echo -n "User not found." 1>&2
    exit 1
  fi

  AUTHORIZED_KEYS_FILE=$(bash -c "echo ~$USER/.ssh/authorized_keys")
fi

cat $AUTHORIZED_KEYS_FILE
exit 0
