# EditorsHub release policy

Standing user instruction: **always release EditorsHub in this repository the
same way the GamersHub app is released.**

Reference pattern: `spideycandy973-web/GamersHub`, e.g. `v6.11.2`.

## Required pattern

1. Bump `appVersionName` (and normally `appVersionCode`) in
   `EditorsHub/app/build.gradle.kts`.
2. Update `RELEASE_NOTES.md` with the user-facing notes for that version.
   GamersHub puts these notes in the PR body; EditorsHub uses
   `RELEASE_NOTES.md` as the committed body source.
3. Commit on `arena/be31b8e1-editorshub` with a message beginning
   `vX.Y.Z: short summary`.
4. Push the commit, then create and push tag `vX.Y.Z` pointing to that commit.
5. `.github/workflows/release.yml` runs on `push: tags: ['v*']`.
6. The workflow validates that the tag matches `versionName`, builds the
   **signed release** APK from source, verifies the signature/version, and
   renames it to exactly one asset: `EditorsHub-vX.Y.Z.apk`.
7. It publishes a stable, non-prerelease, **Latest** release named exactly
   `vX.Y.Z`, with `RELEASE_NOTES.md` as the body and GitHub-generated release
   notes appended.
8. Manual workflow runs publish a `nightly` prerelease, like GamersHub.

## Do not

- Do not update the rolling `apk-latest` release.
- Do not invent custom release titles such as “You released …”.
- Do not attach source archives, checksum files, badging files, or multiple
  APKs. GamersHub releases ship one APK asset.
- Do not substitute a debug APK, an old APK, or a rebuild of a previous
  release unless the user explicitly asks.
- Do not retag/overwrite an existing version.
- Do not require user-provided keys or repository secrets for a release.
- Do not switch to or push to `main`; this session's release tag is created
  from `arena/be31b8e1-editorshub`.

## Signing

Like GamersHub, EditorsHub **commits its release keystore**. The user has no
keys or repository secrets, so the repository contains a generated key at
`keystore/editorshub.p12`:

- Format: PKCS12
- Alias: `editorshub`
- Store/key password: `editorshub` (not a secret)
- `EditorsHub/app/build.gradle.kts` signs release builds with it directly.

No user-configured secrets are required. `.github/workflows/release.yml` uses
GitHub's automatic built-in `GITHUB_TOKEN` to publish; there is no
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, or `KEY_PASSWORD` secret.

**Warning:** because the repository is public, the private key is public.
Anyone can sign APKs with this certificate. This is a convenience key for a
personal project, not a secure production identity. Do not use it for Google
Play or any app where update authenticity matters; see
[`keystore/README.md`](../keystore/README.md) and
[`EditorsHub/docs/RELEASE_SIGNING.md`](../EditorsHub/docs/RELEASE_SIGNING.md).

## Current state

The existing `v4.4.0` release is left as-is. The generated committed release
keystore is now in place, and no user-supplied key or secret is needed. Every
future release follows this policy.
