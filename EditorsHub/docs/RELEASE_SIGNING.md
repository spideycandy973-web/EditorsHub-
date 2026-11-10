# EditorsHub release signing

EditorsHub follows the **GamersHub** release pattern: the release keystore is
committed, so no user-supplied key or repository secret is required.

> **Security warning:** the private key in `keystore/editorshub.p12` is public
> because this repository is public. Anyone can sign an APK with this
> certificate. Use it only for this personal project; never reuse it for Google
> Play, banking, or an app where update authenticity matters. See
> `keystore/README.md`.

## Committed key

| Item | Value |
| --- | --- |
| File | `keystore/editorshub.p12` |
| Format | PKCS12 |
| Alias | `editorshub` |
| Store/key password | `editorshub` (not a secret) |
| Key | RSA 2048-bit, SHA-256 self-signed |

`app/build.gradle.kts` configures the `release` signing config directly from
this file, so `assembleRelease` produces a signed `app-release.apk` both locally
and in CI. Debug builds are unaffected.

## Optional private override

If a genuinely private key is added later, create the gitignored file
`EditorsHub/app/keystore.properties` from the `EditorsHub/` project root:

```properties
storeFile=/absolute/path/to/private-release.jks
storeType=JKS
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

The build gives that file priority over the committed key. Do not commit the
properties file or the private keystore.

## GitHub Actions

`.github/workflows/release.yml`:

1. Verifies `keystore/editorshub.p12` with `keytool` and the public password.
2. Runs `:app:testDebugUnitTest` and `:app:assembleRelease`.
3. Verifies the APK with `apksigner verify --verbose --print-certs`.
4. Publishes the single signed APK asset on a `v*` tag push.

This branch's release workflow requires no user-managed secrets. GitHub's
automatic `GITHUB_TOKEN` is used only to create the release. The `main` branch
still has a separate legacy secret-based production workflow; this session does
not modify `main`.

## Local verification

From the repository root:

```bash
keytool -list -v \
  -keystore keystore/editorshub.p12 \
  -storetype PKCS12 \
  -storepass editorshub \
  -alias editorshub

cd EditorsHub
./gradlew :app:assembleRelease
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

If the key is rotated, existing installs must be uninstalled before installing
an APK signed with the new key.
