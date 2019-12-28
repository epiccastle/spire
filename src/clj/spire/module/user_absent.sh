set -e

PWD_LINE=$(grep "^${NAME}:" /etc/passwd || true)
EXIT=0

if [ "$PWD_LINE" ]; then
  userdel "$NAME"
  EXIT=-1
else
  EXIT=0
fi

exit $EXIT
