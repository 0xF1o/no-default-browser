#!/bin/sh
# Inside the image: the whole build, aapt2 -> javac -> d8 -> zip -> zipalign -> apksigner
set -eu

cd "${SRC:-/src}"

NAME=no-default-browser
KS_DIR=.secrets
KS="$KS_DIR/$NAME.jks"
KS_PASS=$NAME
KS_ALIAS=$NAME
OUT=apk/$NAME.apk

rm -rf build
mkdir -p build/gen build/classes build/dex apk "$KS_DIR"

echo "==> aapt2 compile"
aapt2 compile --dir res -o build/res.zip

echo "==> aapt2 link"
aapt2 link -o build/base.apk -I "$ANDROID_JAR" --manifest AndroidManifest.xml --java build/gen \
  --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK" \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" build/res.zip

echo "==> javac"
javac -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -d build/classes $(find build/gen src -name '*.java')

echo "==> d8"
d8 --release --lib "$ANDROID_JAR" --min-api "$MIN_SDK" --output build/dex $(find build/classes -name '*.class')

echo "==> package"
cp build/base.apk build/unaligned.apk
zip -qj build/unaligned.apk build/dex/classes.dex
zipalign -f 4 build/unaligned.apk build/aligned.apk

if [ ! -f "$KS" ]; then
  echo "==> generating signing key at $KS (keep it, otherwise upgrades need an uninstall)"
  keytool -genkeypair -keystore "$KS" -alias "$KS_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$KS_PASS" -keypass "$KS_PASS" -dname "CN=$NAME" 2>/dev/null
fi

echo "==> apksigner"
apksigner sign --ks "$KS" --ks-pass "pass:$KS_PASS" --ks-key-alias "$KS_ALIAS" --v4-signing-enabled false --out "$OUT" build/aligned.apk
apksigner verify --print-certs "$OUT" | head -3

ls -l "$OUT"
