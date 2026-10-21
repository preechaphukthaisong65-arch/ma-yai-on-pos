# Concurrency risk reduction and remaining production blockers

## Changes in this branch

- `DataHub.saveRecordsAtomically` carries an optional `expectedRevision` into the existing SQLite transaction.
- `LocalStore.saveRecordsAtomically` checks that expected revision against the row in the same SQLite transaction before replacing it. A stale local screen now fails the whole batch instead of overwriting a newer local record.
- Stock receiving, stocktake, stock-limit changes, sale stock decrements, credit-bill payment updates, and product price changes supply the revision they read.
- Sale confirmation rejects duplicate product IDs in one sale request, preventing repeated lines from independently calculating stock from the same starting quantity.
- Firestore rules now require a strictly higher revision for updates, in addition to the client's Firestore transaction check.
- Firestore Emulator tests cover verified-owner access, cross-owner denial, record/path identity, revision monotonicity, ownership protection, default-deny behavior, and the retention delete path.

No SQLite schema, database filename, existing store ID, or existing record path was changed. No existing records were migrated or deleted by these code changes.

## What these changes do not solve

**Multi-device business operations are still not globally atomic.** The local SQLite compare-and-set only coordinates writes inside one device. Two devices can still read the same stock or credit-bill balance and independently create distinct local transactions before either sees the other's change. The Firestore record revision rule prevents stale/equal revision overwrites, but it cannot atomically validate and commit the complete business operation (bill + receipt + stock + ledger) across devices.

Do not treat the Android client as an authoritative stock or debt ledger while multiple devices can sell or accept payments concurrently. Production use with concurrent devices remains blocked until a trusted backend operation atomically validates and commits each sale, stock movement, and credit payment, with idempotency keys and server-side inventory/balance checks. A Cloud Function or other trusted service must be designed against the current data model, deployed to the correct Firebase project, and tested before the app routes these operations through it.

Other unresolved risks:
- Barcode uniqueness is checked against local records only; simultaneous creation on different devices can still collide. Allocate/claim barcode values in a trusted global transaction before relying on cross-device uniqueness.
- Customer lookup by first and last name can be ambiguous. Payment workflows should select a stable customer record ID and show a disambiguating field rather than silently selecting the first matching name.
- The owner mapping and Firestore rules remain source configuration until a trusted administrator deploys them and provisions the verified owner's UID in the intended Firebase project.
- Emulator tests do not prove the production Firebase project has the same rules, authentication settings, owner mapping, or data shape.
- Device-level acceptance tests and financial reconciliation tests are still required.

## Release gate

Do not mark production-ready until all of the following are evidenced:
1. Android Build and Firestore Emulator security tests pass on the same latest commit.
2. Firebase rules are deployed to the confirmed project after export/backup of the current rules and data.
3. The existing store is provisioned to the verified owner UID without changing store IDs or deleting data.
4. Server-side atomic sale, stock, and payment operations are implemented and tested with concurrent requests and retries.
5. Two-device tests cover simultaneous sale of the last units, receiving/counting stock, barcode creation, partial/full debt payment, network loss/retry, and restore.
6. Reports, receipts, stock ledgers, and outstanding balances reconcile after those tests.
