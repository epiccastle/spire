set EXIT 0

if test -z "$FINGERPRINT"
  if test -z "$PUBLIC_KEY"
    set PUBLIC_KEY (curl -s "$PUBLIC_KEY_URL")
  end
  set FINGERPRINT (echo "$PUBLIC_KEY" | gpg | grep -A 1 '^pub' | grep '^ ' | tr -d '[:blank:]')
end

set GPG_FINGERPRINT (apt-key list "$FINGERPRINT" | grep -A 1 '^pub' | grep '^ ' | tr -d '[:blank:]')

if test -n "$GPG_FINGERPRINT"
  apt-key del "$GPG_FINGERPRINT"
  set EXIT 255
end

exit $EXIT
