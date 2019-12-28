set +e

LSB_RELEASE=$(lsb_release -a 2>/dev/null)
IFS=$'\n' read -rd '' -a PARTS <<< "$LSB_RELEASE"
for PART in "${PARTS[@]}"; do
  IFS=$'\t:\r' read -r -a BITS <<< "$PART"
  VAR="${BITS[0]}"
  VAL="${BITS[1]}"
  if [ "$VAR" == "Distributor ID" ]; then
    OS="linux"
    DISTRO="$VAL"
  fi
  if [ "$VAR" == "Release" ]; then
    RELEASE="$VAL"
  fi
  if [ "$VAR" == "Codename" ]; then
    CODENAME="$VAL"
  fi
done

echo "OS=$OS"
echo "DISTRO=$DISTRO"
echo "RELEASE=$RELEASE"
echo "CODENAME=$CODENAME"
