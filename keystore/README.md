# EditorsHub release keystore

This directory intentionally contains a generated signing keystore so releases
do **not** require any user-supplied key or repository secret. The standing
instruction is to release EditorsHub the same way GamersHub does, and GamersHub
commits its keystore.

## Contents

| Item | Value |
| --- | --- |
| File | `keystore/editorshub.p12` |
| Format | PKCS12 |
| Alias | `editorshub` |
| Store password | `editorshub` |
| Key password | `editorshub` |
| Key | RSA 2048-bit |
| Signature | SHA-256 with RSA |
| Valid | 2026-11-10 → 2054-03-28 |
| SHA-256 of file | `6cf47b140b2cb58848d9150763c6d3214b8297a701fac768db1199e4f0cca4da` |
| Certificate SHA-256 | `D8:AB:32:57:40:59:0D:21:10:BC:39:10:03:65:86:1A:F3:2D:59:83:A7:4D:52:3C:73:B5:10:86:7D:AA:D6:88` |

The password is **not a secret**: the private key is committed alongside it.
`EditorsHub/app/build.gradle.kts` references this file directly for release
builds. `.github/workflows/release.yml` verifies the alias with `keytool` and
then verifies the produced APK with `apksigner`.

## No repository secrets required

The release workflow uses only GitHub's automatic built-in `GITHUB_TOKEN` to
publish the release. No `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, or
`KEY_PASSWORD` secrets need to be configured.

## Security warning

Because this repository is public, the private key is public. **Anyone can sign
an APK with this certificate.** This is acceptable only for a personal project
where the signing identity is not a security boundary.

- Do not use this key for Google Play, banking, or any app where update
  authenticity matters.
- Do not reuse this key for another app.
- If the key is ever rotated, existing installs must be uninstalled before
  installing an APK signed with the new key.
- If a truly private signing key is added later, create
  `EditorsHub/app/keystore.properties` (gitignored) to override this committed
  key; see `EditorsHub/docs/RELEASE_SIGNING.md`.
