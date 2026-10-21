# Local-first cloud synchronization

## Current behavior

- Local records are stored in the existing SQLite database `data_hub.db`, table `hub_records`.
- The app saves locally first, then attempts to synchronize pending records to Firestore.
- Pending records retain their local sync state and WorkManager retries when connectivity is available.
- Uploads validate record identity and revisions to reduce stale overwrites.
- Server-authoritative sale reconciliation, debt payments, and barcode reservations remain separate operations.
- The app no longer performs an automatic cloud restore during startup or provides a restore workflow.
- Android system backup is disabled with `android:allowBackup="false"`; the backup-rule XML resources and backup/restore-only classes have been removed.
- For compatibility with existing Firestore rules and Cloud Functions, synchronized records still use the legacy path `dataHubBackups/{storeId}/records/{recordId}`. This path name is retained as a storage contract; the Android app no longer exposes backup or restore functionality.

## Device migration limitation

A fresh installation does not automatically download records from Firestore. Do not promise migration or disaster recovery to a replacement device while restore remains disabled. Any future cross-device data transfer must be specified and explicitly approved separately.

## Validation requirements

- Build the APK from the exact pull request commit.
- Test offline local writes, reconnection, retry, and duplicate prevention.
- Test server-authoritative sale and debt-payment paths against the deployed Firebase project.
- Confirm owner membership and active-device rules with Firestore Emulator tests and the actual deployed rules.
- Install and test on a physical Android device before claiming device-level success.

A successful APK build confirms compilation and packaging only. It does not prove cloud permissions, end-to-end synchronization, or real-device behavior.
