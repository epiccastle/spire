set -e

EXIT=0
GPG_FINGERPRINT=$(apt-key list "$FINGERPRINT" | grep -A 1 ^pub | grep '^ ' | tr -d '[:blank:]')

if [ "$PUBLIC_KEY_URL" ]; then
  if [ "$FINGERPRINT" ]; then
    if [ "$FINGERPRINT" != "$GPG_FINGERPRINT" ]; then
      PUBLIC_KEY=$(curl -s "$PUBLIC_KEY_URL")
      DOWNLOADED_KEY_FINGERPRINT=$(echo "$PUBLIC_KEY" | gpg | grep -A 1 ^pub | grep '^ ' | tr -d '[:blank:]')
      if [ "$FINGERPRINT" != "$DOWNLOADED_KEY_FINGERPRINT" ]; then
        echo "Error: supplied fingerprint and downloaded public key do not match. Downloaded key fingerprint: $DOWNLOADED_KEY_FINGERPRINT" 1>&2
        exit 1
      fi

      if [ "$KEYRING" ]; then
        echo "$PUBLIC_KEY" | apt-key --keyring "$KEYRING" add -
        EXIT=-1
      else
        echo "$PUBLIC_KEY" | apt-key add -
        EXIT=-1
      fi
    fi
  else
    if [ "$KEYRING" ]; then
      curl "$PUBLIC_KEY_URL" | apt-key --keyring "$KEYRING" add -
      EXIT=-1
    else
      curl "$PUBLIC_KEY_URL" | apt-key add -
      EXIT=-1
    fi
  fi
else
  if [ ! "$FINGERPRINT" ]; then
    FINGERPRINT=$(echo "$PUBLIC_KEY" | gpg |grep -A 1 ^pub | grep '^ ' | tr -d '[:blank:]')
  else
    CHECK_FINGERPRINT=$(echo "$PUBLIC_KEY" | gpg | grep -A 1 ^pub | grep '^ ' | tr -d '[:blank:]')
    if [ "$CHECK_FINGERPRINT" != "$FINGERPRINT" ]; then
      echo "Error: supplied fingerprint and public key do not match. Public key fingerprint: $FINGERPRINT" 1>&2
      exit 1
    fi
  fi

  if [ "$FINGERPRINT" ] && [ "$FINGERPRINT" != "$GPG_FINGERPRINT" ]; then
    if [ "$KEYRING" ]; then
      echo "$PUBLIC_KEY" | apt-key --keyring "$KEYRING" add -
      EXIT=-1
    else
      echo "$PUBLIC_KEY" | apt-key add -
      EXIT=-1
    fi
  fi
fi

exit $EXIT
