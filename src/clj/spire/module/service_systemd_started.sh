set -e

STATE=$(service "$NAME" status | grep "Active:" | awk '{print $2}')

if [ "$STATE" == "active" ]; then
  exit 0
else
  service "$NAME" start
  exit -1
fi
