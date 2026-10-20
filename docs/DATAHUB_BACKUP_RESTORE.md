# Data Hub: Backup and Restore

This work is isolated on `feature/datahub-backup-restore`. It must not change the POS home screen or `MainActivity.java`.

## Current implementation

- SQLite is the local source of truth for records saved through `DataHub`.
- Backup records are stored in Firestore under `dataHubBackups/{storeId}/records/{recordId}`.
- Local records remain pending/failed when an upload fails; WorkManager retries when network connectivity is available.
- Local and cloud revisions are checked to avoid an older upload overwriting a newer revision.
- Restore inserts only missing records and does not overwrite records already present locally.
- The startup screen asks for a store code before attempting the initial restore.

## Build validation

GitHub Actions builds a debug APK for pull requests and uploads it as a workflow artifact. A successful build confirms compilation and packaging only; it does not prove Firebase permissions, cloud restore, offline retry, or device behavior.

## Production blockers — do not skip

### 1. Store access must be securely bound to an owner

A store code entered into the app is **not authentication**. The app currently signs in anonymously. If Firestore rules merely require `request.auth != null`, any signed-in user could potentially access another store's backup by guessing its store ID. Do not deploy permissive rules.

Before production, implement and test an ownership mechanism that works across devices (for example, a real store account or a trusted backend that provisions store membership). Then enforce membership in Firestore Security Rules. Do not put an admin/service-account key in the Android app.

### 2. Verify the real Firebase project

Confirm the Firebase project is the intended production project, enable the required sign-in provider, and deploy reviewed Firestore Security Rules. The presence of `google-services.json` does not prove that rules or project settings are correct.

### 3. Required acceptance tests

- First install with internet and an empty cloud store.
- Restore existing cloud records on a second device using the approved store-account flow.
- Existing local records are never overwritten by restore.
- Offline save, process termination/restart, reconnection, and successful retry.
- Two quick updates to one record while uploads are in flight; newest revision wins.
- A different store account cannot read or write this store's records.
- Large backup sets and partial Firestore failures.
- Install and launch the generated APK on a real Android device.

## Scope

This phase covers the Data Hub, cloud backup, retry, and restore pathway only. Sales, stock, and POS home-screen behavior are outside this phase. Do not merge the pull request or report production-ready until the security blocker and acceptance tests above are resolved.
