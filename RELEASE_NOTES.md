# EditorsHub v4.5.2 — first GamersHub-style signed release

This is the first EditorsHub release published with the **GamersHub** pattern:
a `vX.Y.Z` tag triggers a signed release build from source and publishes a
stable **Latest** release named exactly the tag, with one asset:
`EditorsHub-v4.5.2.apk`.

Package `com.editorshub`, version 4.5.2 (version code 21). Built from the native
Kotlin/Compose project in `EditorsHub/`. The version was chosen as the next
patch after the repository's existing `v4.5.1` release.

## Signing

- Signed with the repository's generated key at `keystore/editorshub.p12`.
- Alias `editorshub`; PKCS12; RSA 2048-bit; SHA-256 self-signed.
- No user-supplied key, password, or repository secret was required.
- The key is committed and therefore public; see `keystore/README.md`.
- GitHub's automatic built-in `GITHUB_TOKEN` was used only to publish the
  release.

## Install / update notice

This APK is signed with a **new EditorsHub key**. Android may refuse to update
an existing installation signed with a different key. Back up projects, then
uninstall the previous EditorsHub build before installing this one.

## Verification

- `keytool` verified the committed keystore and alias.
- `apksigner verify --verbose --print-certs` verified the APK signature.
- `aapt dump badging` verified package name, `versionName='4.5.2'`, and
  `versionCode='21'`.
- Unit tests and the signed release build ran in GitHub Actions.
- A no-publish dry run previously built and verified the signed APK
  successfully.

## Scope

- This release does **not** update the rolling `apk-latest` release.
- It is **not** the historical exact-linked Capacitor APK.
- It is a native Kotlin/Compose build; exact pixel-clone parity to the supplied
  Ultra Motion reference is **not** claimed.
