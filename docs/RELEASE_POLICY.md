# EditorsHub release policy

Standing user instruction: **always release EditorsHub in this repository the
same way the GamersHub app is released.**

Reference pattern: `spideycandy973-web/GamersHub`, e.g. `v6.11.2`.

## Required pattern

1. Bump `versionName` (and normally `versionCode`) in
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
- Do not switch to or push to `main`; this session's release tag is created
  from `arena/be31b8e1-editorshub`.

## Signing

GamersHub commits a PKCS12 keystore. EditorsHub must **not** commit a private
key. The release workflow uses these repository secrets instead:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

If they are missing, the workflow fails with a clear error instead of silently
publishing a debug-signed APK.

## Current state

The existing `v4.4.0` release is left as-is. Every future release follows this
policy.
