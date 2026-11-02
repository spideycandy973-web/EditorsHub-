# Secure release signing

**Debug signing is never used for a public production release.** CI publishes an *unsigned*
release APK/AAB by default; no keystore, password or secret is committed to this repository.

## Local signed build

1. Generate a key (keep it outside the repo):
   ```bash
   keytool -genkeypair -v -keystore ~/motion-studio-release.jks \
     -alias motionstudio -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Create `MotionStudio/app/keystore.properties` (gitignored):
   ```properties
   storeFile=/absolute/path/to/motion-studio-release.jks
   storePassword=YOUR_STORE_PASSWORD
   keyAlias=motionstudio
   keyPassword=YOUR_KEY_PASSWORD
   ```
3. Build:
   ```bash
   cd MotionStudio
   ./gradlew :app:assembleRelease :app:bundleRelease
   ```
   `app/build.gradle.kts` detects the properties file, creates the `release` signing config and
   applies it. Without the file, artifacts are intentionally unsigned.

## Signed release in GitHub Actions

`android-release.yml` (workflow_dispatch) expects these repository secrets:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 motion-studio-release.jks` |
| `KEYSTORE_PASSWORD` | store password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

The workflow decodes the keystore into the runner workspace, writes a temporary
`keystore.properties`, builds signed APK + AAB, uploads them to a release, and never commits the
key. Rotate the key if it is ever exposed.

## Verification

```bash
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
keytool -list -v -keystore motion-studio-release.jks
```
