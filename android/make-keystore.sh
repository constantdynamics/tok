#!/usr/bin/env bash
# Maakt een upload-keystore + keystore.properties voor het signeren van de release-AAB.
# BEWAAR de keystore en het wachtwoord goed: zonder deze kun je geen app-updates uploaden.
set -euo pipefail
cd "$(dirname "$0")"

KS="upload-keystore.jks"
if [ -f "$KS" ]; then
  echo "Er bestaat al een $KS — die laat ik staan."
  echo "Verwijder 'm handmatig als je opnieuw wilt beginnen."
  exit 0
fi

read -rsp "Kies een wachtwoord voor de keystore: " PW; echo
read -rp  "Je naam (komt in het certificaat, bv. Voornaam Achternaam): " CN

keytool -genkeypair -v \
  -keystore "$KS" -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PW" -keypass "$PW" \
  -dname "CN=${CN:-tok}, O=tok, C=NL"

umask 077
cat > keystore.properties <<EOF
storeFile=$KS
storePassword=$PW
keyAlias=upload
keyPassword=$PW
EOF

echo
echo "Klaar:"
echo "  - $KS                 (de keystore — NIET kwijtraken, NIET in git)"
echo "  - keystore.properties (door Gradle gebruikt bij het signeren)"
echo
echo "Bouw nu de release-AAB met:   ./gradlew bundleRelease"
