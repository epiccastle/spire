set -e

EXIT=0

if [ ! "$FINGERPRINT" ]; then
  if [ ! "$PUBLIC_KEY" ]; then
    PUBLIC_KEY=$(curl -s "$PUBLIC_KEY_URL")
  fi
  FINGERPRINT=$(echo "$PUBLIC_KEY" | gpg | grep -A 1 ^pub | grep '^ ' | tr -d '[:blank:]')
fi

GPG_FINGERPRINT=$(apt-key list "$FINGERPRINT" | grep -A 1 ^pub | grep '^ ' | tr -d '[:blank:]')

if [ "$GPG_FINGERPRINT" ]; then
  apt-key del "$GPG_FINGERPRINT"
  EXIT=-1
fi

exit $EXIT
