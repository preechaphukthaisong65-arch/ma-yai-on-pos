# Server-authoritative online sales

## What the callable function guarantees

The `commitPosSale` callable function is the authoritative path for online sales. It checks the verified Firebase user, `storeOwners/{storeId}.ownerUid`, and `storeDeviceSessions/{storeId}.activeDeviceId` inside a Firestore transaction. It reads the current server product records, uses server-side prices, validates available stock and cash received, and commits product stock updates, stock history, sale bill, receipt, and sale audit in one transaction. Firestore retries contended transactions; a stable bill ID/idempotency key prevents a retry from decrementing stock a second time.

The app applies the returned records to SQLite with expected-revision compare-and-set checks. If local state has diverged, it does not overwrite it silently and displays a reconciliation warning.

## Important boundary

- This protects **online sales routed through the callable function**. Offline sales still use the existing local SQLite transaction and remain pending.
- It does not yet safely import every stale-device offline sale/debt/payment batch. Reconnect conflict detection remains fail-closed and preserves the local records, but a conflicting batch can remain unresolved until a dedicated reconciliation/import flow is implemented.
- A Firebase Functions deployment and enabling App Check are not performed by this repository change. Do not claim production readiness until the function is deployed to the confirmed project and the app is configured for the deployed region / App Check policy.
- The callable function currently uses the default Functions region. If the project deploys to another region, configure the Android Functions client to that same region before rollout.
- Existing legacy cloud records must have the expected record shape: `dataType=product`, `payload.price`, and `payload.stockQuantity`. Test against a copy of real-shaped data before production rollout.

## Deployment checklist

1. Confirm the intended Firebase project with the shop owner; do not infer it from `google-services.json`.
2. Export/back up production Firestore and record current deployed rules.
3. Configure Firebase Authentication Email/Password and verify the owner account.
4. Provision `storeOwners/{storeId}.ownerUid` using a trusted admin process.
5. Deploy reviewed Firestore Rules and Functions to the explicitly selected project; never store service-account credentials in the repository or APK.
6. Configure and enforce App Check after the Android app has a valid production provider configuration.
7. Test two-device contention for the last unit, duplicate taps, network loss after commit, same-bill retry, stale local revision, credit sale, and rejected cash amount in a staging project.
8. Verify products, stock ledger, sale bill, receipt and sale history agree after each test.
9. Only then enable the server sale path in production.
