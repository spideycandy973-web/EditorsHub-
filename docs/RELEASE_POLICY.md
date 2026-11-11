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

No user-configured secrets are required for releases from this branch.
`.github/workflows/release.yml` uses GitHub's automatic built-in
`GITHUB_TOKEN` to publish; it does not read any signing secret. The separate
legacy `main` workflow is outside this session's branch and is not modified.

**Warning:** because the repository is public, the private key is public.
Anyone can sign APKs with this certificate. This is a convenience key for a
personal project, not a secure production identity. Do not use it for Google
Play or any app where update authenticity matters; see
[`keystore/README.md`](../keystore/README.md) and
[`EditorsHub/docs/RELEASE_SIGNING.md`](../EditorsHub/docs/RELEASE_SIGNING.md).

## Current state

On 2026-11-11 the user rejected the newer `v4.5.3` native/RomKit output and
reaffirmed that the [v4.5.1 release](https://github.com/spideycandy973-web/EditorsHub-/releases/tag/v4.5.1)
is the main app because its UI/design is accurate. Two decisions were applied:

- **App/design:** the active branch remains a **native Kotlin/Compose** app. It
  uses the exact v4.5.1 `--um-*` tokens and restored v4.5.1 web/Capacitor source
  as its design reference. It is not a return to a WebView/PWA runtime. The
  native `versionName` is `4.5.1` (version code 21 retained).
- **Release state:** the `v4.5.3` release and tag were deleted. `v4.5.1` is
  **Latest**, renamed exactly `v4.5.1`, with one asset:
  `EditorsHub-Motion.apk` (SHA-256
  `70c597016f0a2d88cc2966af5329b3780c2791e18bd09a34c1a8331a39d115b8`). Its
  extra `SHA256SUMS.txt` was removed. No new release, tag, or APK was created.

The Latest asset is therefore the **historical Capacitor APK** from the linked
release, while the active branch source is a **native Compose implementation of
that design**. This is intentional per the user's selection; it is not a claim
that the Latest APK is a native build. The release asset name is still
`EditorsHub-Motion.apk` rather than `EditorsHub-v4.5.1.apk`, and the existing
release body still describes the Capacitor app. `v4.5.2` remains a historical
non-Latest release. Do not retag or overwrite `v4.5.1`.

The default branch `main` still contains the newer RomKit/native code at
`2432927`; this session works only on `arena/be31b8e1-editorshub` and does not
modify or push to `main`. Future stable releases from this branch still follow
the GamersHub pattern above, but require explicit new authorization.
