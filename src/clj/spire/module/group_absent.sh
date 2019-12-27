set -e

GROUP_LINE=$(grep "^${NAME}:" /etc/group || true)

if [ "$GROUP_LINE" ]; then
  groupdel "$NAME"
  EXIT=-1
else
  EXIT=0
fi

exit $EXIT
