set -e

STATE=$(service "$NAME" onestatus | awk '{print $3 " " $4}')

if [ "$STATE" == "not running." ]; then
  exit 0
else
  service "$NAME" onestop
  exit -1
fi
