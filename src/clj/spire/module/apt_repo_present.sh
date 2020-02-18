set -e

function ppa_key()
{
  JSON=$(curl -H "Accept: application/json" "https://launchpad.net/api/1.0/~$PPA_OWNER/+archive/$PPA_NAME")
  FINGERPRINT=$(echo "$JSON" | sed "s/,\s*/\n/g" | sed 's/[{}"]//g' | grep signing_key_fingerprint | awk '{print $2}')
  apt-key adv --recv-keys --no-tty --keyserver hkp://keyserver.ubuntu.com:80 "$FINGERPRINT"
}

if [ -e "$FILE" ]; then
  EXISTING_CONTENTS=$(cat "$FILE")
  if [ "$CONTENTS" != "$EXISTING_CONTENTS" ]; then
    if [ "$PPA_NAME" ]; then
      ppa_key
    fi
    mv "$FILE" "$FILE.save"
    echo "$CONTENTS" > "$FILE"
    exit -1
  fi
  exit 0
else
  if [ "$PPA_NAME" ]; then
    ppa_key
  fi
  echo "$CONTENTS" > "$FILE"
  exit -1
fi
