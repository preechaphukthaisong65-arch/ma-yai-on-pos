# Android release signing

The CI workflow always builds and tests a debug APK. A signed release APK is produced only when the repository owner configures a stable Android signing key in GitHub Actions secrets.

## Required repository secrets

- `ANDROID_KEYSTORE_BASE64`: base64-encoded contents of the release keystore.
- `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: key alias.
- `ANDROID_KEY_PASSWORD`: key password.

Create the keystore once on a trusted computer and store a secure offline backup. Do not commit the keystore or passwords to the repository or paste them into chat. Example key generation command (choose a strong password and keep it private):

```sh
keytool -genkeypair -v -keystore maeyai-on-pos-release.keystore -alias maeyai-on-pos -keyalg RSA -keysize 2048 -validity 10000
base64 -w 0 maeyai-on-pos-release.keystore > maeyai-on-pos-release.keystore.base64
```

Add the four values under **GitHub repository → Settings → Secrets and variables → Actions → New repository secret**. The release key must be preserved for every future update. If a different key is used, Android will normally reject an in-place update and users may need a migration plan; uninstalling the old app can erase local POS data.

The signing key cannot be recovered from an APK. This workflow does not make a previously installed APK update-compatible unless the same signing identity is used.
