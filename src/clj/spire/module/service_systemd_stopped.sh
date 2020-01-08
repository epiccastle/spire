set -e

STATE=$(service "$NAME" status | grep "Active:" | awk '{print $2}')

if [ "$STATE" == "inactive" ]; then
  exit 0
else
  service "$NAME" stop
  exit -1
fi
