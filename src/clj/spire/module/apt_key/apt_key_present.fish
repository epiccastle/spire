set EXIT 0
set GPG_FINGERPRINT (apt-key list "$FINGERPRINT" | grep -A 1 '^pub' | grep '^ ' | tr -d '[:blank:]')

if test -n "$PUBLIC_KEY_URL"
  if test -n "$FINGERPRINT"
    if test "$FINGERPRINT" != "$GPG_FINGERPRINT"
      set PUBLIC_KEY (curl -s "$PUBLIC_KEY_URL")
      set DOWNLOADED_KEY_FINGERPRINT (echo "$PUBLIC_KEY" | gpg | grep -A 1 '^pub' | grep '^ ' | tr -d '[:blank:]')
      if test "$FINGERPRINT" != "$DOWNLOADED_KEY_FINGERPRINT"
        echo "Error: supplied fingerprint and downloaded public key do not match. Downloaded key fingerprint: $DOWNLOADED_KEY_FINGERPRINT" >&2
        exit 1
      end

      if test -n "$KEYRING"
        echo "$PUBLIC_KEY" | apt-key --keyring "$KEYRING" add -
        set EXIT 255
      else
        echo "$PUBLIC_KEY" | apt-key add -
        set EXIT 255
      end
    end
  else
    if test -n "$KEYRING"
      curl "$PUBLIC_KEY_URL" | apt-key --keyring "$KEYRING" add -
      set EXIT 255
    else
      curl "$PUBLIC_KEY_URL" | apt-key add -
      set EXIT 255
    end
  end
else
  if test -z "$FINGERPRINT"
    set FINGERPRINT (echo "$PUBLIC_KEY" | gpg | grep -A 1 '^pub' | grep '^ ' | tr -d '[:blank:]')
  else
    set CHECK_FINGERPRINT (echo "$PUBLIC_KEY" | gpg | grep -A 1 '^pub' | grep '^ ' | tr -d '[:blank:]')
    if test "$CHECK_FINGERPRINT" != "$FINGERPRINT"
      echo "Error: supplied fingerprint and public key do not match. Public key fingerprint: $FINGERPRINT" >&2
      exit 1
    end
  end

  if test -n "$FINGERPRINT"; and test "$FINGERPRINT" != "$GPG_FINGERPRINT"
    if test -n "$KEYRING"
      echo "$PUBLIC_KEY" | apt-key --keyring "$KEYRING" add -
      set EXIT 255
    else
      echo "$PUBLIC_KEY" | apt-key add -
      set EXIT 255
    end
  end
end

exit $EXIT
