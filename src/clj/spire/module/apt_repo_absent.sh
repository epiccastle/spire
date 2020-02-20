set -e

for FILE in $FILES; do
  sed -n "${REGEX}=" "$FILE"
done
