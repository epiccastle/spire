set -e

STATE=$(service "$NAME" onestatus | awk '{print $3 " " $4}')

if [ "$STATE" == "running as" ]; then
  exit 0
else
  service "$NAME" onestart
  exit -1
fi
