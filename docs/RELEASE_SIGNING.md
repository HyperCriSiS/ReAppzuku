# Android release signing

ReAppzuku releases must use one stable Android signing identity. A GitHub Actions runner-generated debug certificate is not a release identity and must never be used for published updateable APKs.

## Current migration state

The historical `ondemand-test` APK was signed with a runner-local Android debug certificate. A later CI runner produced a different debug certificate, so Android cannot install that later build as an in-place update of the published test APK.

Runtime evidence from signing check `34002061736`:

- current CI debug certificate SHA-256: `e1707452c2a2c9dbf7f0289a1d0a7369a0e37bdd381fe0723107ec065e539a8d`
- published `ondemand-test` certificate SHA-256: `c8a91da2bf949ee3053d5cec88f2f3ff5a218e5fbce80150e6bde4796bbde021`

Both certificates identify themselves as Android Debug certificates, but their fingerprints differ. This is expected for independently generated debug keystores.

The first APK published with the new stable release key therefore requires a one-time uninstall/reinstall for users of the historical random-debug-signed test APK. After that migration, the stable release key must be retained permanently so future APKs can update in place.

## Required GitHub Actions secrets

The manual `Signed ReAppzuku release` workflow requires all of these repository secrets:

- `ANDROID_SIGNING_KEYSTORE_BASE64` — base64 encoding of the complete release keystore file.
- `ANDROID_SIGNING_KEY_ALIAS` — alias of the signing key inside the keystore.
- `ANDROID_SIGNING_KEYSTORE_PASSWORD` — keystore password.
- `ANDROID_SIGNING_KEY_PASSWORD` — private-key password.
- `ANDROID_SIGNING_CERT_SHA256` — expected SHA-256 digest of the signing certificate, with or without colons.

The keystore itself must never be committed to the repository. Keep an offline backup of the keystore and credentials in at least two independently protected locations. Losing this key prevents normal Android updates for installations signed with it.

## Generating the fingerprint

After creating the release keystore, derive the certificate fingerprint locally with Android build tools or Java `keytool`. Store the normalized SHA-256 certificate digest in `ANDROID_SIGNING_CERT_SHA256`.

The workflow independently extracts the signing certificate from the produced APK with `apksigner` and refuses the build if the actual fingerprint does not match the pinned secret.

## Workflow safety model

`.github/workflows/signed-release.yml` is manual-only and fail-closed:

1. It checks out the explicitly selected source ref.
2. It requires every signing secret before building.
3. It runs unit tests, lint and `assembleRelease`.
4. It decodes the keystore only into the ephemeral runner temp directory.
5. It zip-aligns, signs and verifies the APK.
6. It compares the APK certificate SHA-256 with the pinned expected fingerprint.
7. It creates an APK SHA-256 checksum.
8. It publishes nothing by default.
9. Publication requires both `publish=true` and the exact confirmation text `RELEASE`.
10. Signing material is removed from runner temp in an `always()` cleanup step.

A publication request without a tag or without the exact confirmation text fails before checkout/build.

## First stable-key release procedure

Before the first stable-key release:

1. Generate and back up the dedicated ReAppzuku release keystore outside GitHub.
2. Add the five repository secrets above.
3. Run `Signed ReAppzuku release` with `publish=false` against the intended source ref.
4. Confirm that signing and certificate verification pass.
5. Record the expected certificate SHA-256 in release documentation.
6. Perform an install/update test with two APKs signed by that same key.
7. Only then dispatch the workflow again with the intended release tag, `publish=true`, and confirmation `RELEASE`.

Do not reuse the historical random debug certificate as the long-term release identity even if its private debug keystore can be recovered. The migration should establish a deliberately managed release key once and keep that identity stable thereafter.
