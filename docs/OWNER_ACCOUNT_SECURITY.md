# Owner account and Firebase access provisioning

## Intended model

- One verified email/password Firebase Auth account owns one shop.
- The same owner account can sign in on multiple Android devices.
- Store data stays in the existing SQLite `data_hub.db` / `hub_records` locally and existing legacy Firestore storage path `dataHubBackups/{storeId}/records/{recordId}` used by cloud sync.
- The Android app cannot create or change ownership records. This prevents a person who only knows a store code from claiming its data.
- A new account is not automatically authorized for any shop. A trusted Firebase administrator must provision the owner mapping.

## Required Firebase project setup

1. In Firebase Console, select and verify the intended project. Do not assume the repository's `google-services.json` alone proves the project is correct.
2. Under Authentication > Sign-in method, enable **Email/Password**.
3. Confirm the owner can receive verification emails. New accounts must verify their email before accessing cloud records.
4. Back up the current Firestore data and export the current deployed Security Rules before changing production settings.
5. Review `firestore.rules` with the actual data model and test it in the Firebase Emulator Suite or a non-production project first.
6. Deploy the reviewed rules to the intended Firebase project using the Firebase CLI after authenticating with the correct project. This repository has no checked-in project alias; choose the project explicitly and do not guess its ID. The config file points Firestore rules to `firestore.rules`.
7. In Firebase Console or a trusted Admin SDK process, create `storeOwners/{storeId}` for the existing store. Set the field `ownerUid` to the UID of the verified owner's Firebase Authentication account. Do not allow the Android client to create this document. Do not put service-account credentials in the APK or repository.
8. Confirm the store ID exactly matches the existing cloud-record path `dataHubBackups/{storeId}/records`. Do not rename or migrate existing records as part of account setup.

## Rollout warning for existing installations

The app requires verified email/password sign-in before cloud sync. Automatic cloud restore has been removed. Existing anonymous sessions do not automatically become owner accounts. Existing local data is preserved, but cloud access will be denied until the real owner account is verified, the owner mapping is provisioned, and the restrictive rules are deployed. Do not remove existing local data to resolve a sign-in failure.

## Required security tests before production

- Verified owner can read, create, update, and delete records only in the provisioned store.
- Unverified account is denied.
- Authenticated account without a `storeOwners/{storeId}` mapping is denied.
- A different verified account cannot read, write, list, or delete the owner's store data.
- Client attempts to create/update/delete `storeOwners` are denied.
- A write whose `storeId`, `recordId`, or `uploadedBy` conflicts with its path/current UID is denied.
- Test legacy records and deletion rules against a copy of real-shaped backup data before rollout.
- Test login and cloud sync on the authorized device; automatic restore on a second or replacement device is not supported in the current scope.

## Important limitation

The repository change only adds a proposed rules file and Firebase CLI configuration. It does **not** deploy rules, enable the Firebase Auth provider, provision the owner mapping, or prove the production Firebase project is configured correctly. Those steps require access to the intended Firebase project and must be verified separately. Until then, production readiness is blocked.
