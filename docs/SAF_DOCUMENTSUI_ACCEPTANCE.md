# SAF / DocumentsUI permission acceptance

Status: **NOT RUN — manual/interactive device acceptance still required**

## Software evidence already available

PR #104 adds non-destructive API24/API37 checks for ungranted cross-UID `content://` read/write denial, unsupported URI-scheme rejection, refusal to take/release a persistable permission without an existing grant and absence of newly retained grants after a one-shot synthetic backup read. These checks do **not** establish successful real DocumentsUI grants or user/provider revocation.

ReAppzuku currently uses `CreateDocument` for export and `OpenDocument` for import, immediately consuming the selected URI. It intentionally does not call `takePersistableUriPermission` and does not save the URI for background reuse. Therefore a new user selection should be required for later operations.

## Authorized manual matrix (disposable installation and data only)

1. **One-shot export (API24 and API37):** Choose *Export* in Settings and select a document location via the real DocumentsUI UI. Verify the resulting JSON can be read, no persistent URI permission has silently appeared, and the app cannot later reopen that URI without a newly valid grant. Preserve the backup in the test location only.
2. **One-shot import:** Using an isolated test profile with a known reversible setting, select a valid backup through *OpenDocument*, import it, and verify the expected setting. Repeat with a rejected/invalid backup to verify complete rollback. The standard backup-v7 tests are supportive but not a substitute for this interactive flow.
3. **User/provider revocation between selection and I/O:** With a controlled provider that supports removing access, select a test document, revoke access **before** the backup stream opens, and verify a denied read/write is reported and that preferences/Room policies remain unchanged. Distinguish a removed or renamed file (`FileNotFoundException`) from an actual URI grant revocation (`SecurityException`).
4. **Persistable grant lifecycle in a separate disposable harness:** Launch real `ACTION_OPEN_DOCUMENT`, retain the result's authorized read flags using `takePersistableUriPermission`, restart the harness, verify access, then `releasePersistableUriPermission` and verify the appropriate later access denial. **This lifecycle is not implemented in ReAppzuku's current one-shot backup UI.** Run it only to validate Android/provider behavior, not as evidence of an app feature.
5. **Provider variety:** Repeat on a platform DocumentsProvider and at least one different provider/OEM device when authorized. Record Android version, provider, exact grant type and whether permission revocation is exposed in that provider's UI.

## Exit criteria

Record explicit PASS/FAIL and reproducible device logs for every scenario. Do not infer real persisted-grant or revocation behavior from exported synthetic test providers. Do not alter personal documents, production settings or real backups. The matrix remains OPEN until separately verified.
