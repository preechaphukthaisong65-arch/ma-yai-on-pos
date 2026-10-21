# Store-risk simulation matrix

This matrix distinguishes executable Firestore security-rule simulations from scenarios that still require Android/Firebase integration or physical-device testing. A source review is not counted as a passing runtime test.

## Automated Firestore emulator scenarios

Run with `npm test` from the repository root (the GitHub Actions workflow installs dependencies and runs the Firestore emulator).

| Scenario | Expected safe result | Automated coverage |
|---|---|---|
| Verified owner reads their store backup | Allow | Yes |
| Unverified account accesses backup | Deny | Yes |
| Different account reads/writes the store | Deny | Yes |
| Active device creates a valid backup record | Allow | Yes |
| Superseded device attempts to write | Deny | Yes |
| Device A goes offline, device B takes over, A's queued write reconnects | Deny stale write | Yes |
| Superseded device edits/deletes an existing backup | Deny | Yes |
| Backup path ID and record ID disagree | Deny | Yes |
| Revision stays the same or decreases | Deny | Yes |
| Negative revision or non-object payload | Deny | Yes |
| Store owner identity is changed by client | Deny | Yes |
| Active device reserves a barcode once | Allow first create | Yes |
| Same barcode reservation is created a second time | Deny | Yes |
| Barcode reservation is edited/deleted | Deny | Yes |
| Barcode or backup write occurs without a device session | Deny | Yes |
| Unreviewed Firestore collections are accessed | Deny | Yes |
| Retention cleanup deletes a record belonging to the active device | Allow | Yes |

## Realistic shop workflows requiring app-level verification

These are required before calling the POS production-ready. They are not claimed as passed by the Firestore rules tests.

| Scenario | Required invariant | Verification status |
|---|---|---|
| Cash sale with exact cash / excess cash | Correct total, change, one bill, one receipt, stock decremented once | Needs Android integration test |
| Cash sale with insufficient cash / empty cart | No bill or stock mutation | Needs Android integration test |
| Credit sale for new and existing customer | Correct 5% fee, debt, customer link, stock and history committed together | Needs Android integration test |
| Sale quantity exceeds available stock | Sale rejected; stock never negative | Source contains stock check; needs runtime test |
| Double-tap confirm / process recreation | One sale only; no duplicate bill or stock deduction | Bill-ID and UI guards exist; needs runtime test |
| Receive stock / stocktake / update stock limit | Correct stock and history, no lost update on stale revision | Needs Android integration test |
| Concurrent edits to same product | Compare-and-set rejects stale edit; no partial related records | Needs SQLite concurrency test |
| Full and partial debt payment | Amount allocated once; balance/status/receipt/history agree | Cloud transaction code exists; needs Firebase emulator integration test |
| Double-tap payment / two devices pay same debt | At most one valid allocation per current bill revision | Needs concurrent transaction test |
| Payment amount exceeds current debt or bill already paid | Reject without changing any record | Guard exists; needs Firebase emulator integration test |
| Network loss before/during/after sale or payment | No silent success; local pending data preserved; no duplicate on retry | Needs Android offline/online integration test |
| Reconnect with identical, missing, conflicting, or duplicate-barcode records | Restore missing only; never overwrite/delete conflict silently | Restore code has conflict checks; needs seeded DB integration test |
| Second device logs in while first is offline | One active device; stale writes rejected after reconnection | Rules scenario covered; needs two-device runtime test |
| App force-stop/restart, Android kills process, backup retry | Pending records survive and are retried without duplication | Needs Android lifecycle test |
| Receipt reprint, reports, debt reminders, retention cleanup | No financial records altered by viewing/printing/notifications; retention preserves required history | Needs app-level regression tests |
| Firestore rules deployed to actual Firebase project | Production backend enforces the same rules tested in CI | Not verified; deployment requires project-owner access |

## Safety boundaries

- Automated rules tests use a disposable Firebase emulator project and synthetic records; they do not touch the shop's production data.
- The test suite does not prove that Android UI, SQLite transactions, WorkManager retries, or real Firebase behavior are correct.
- Do not deploy or use as production-ready until the current rules are deployed to the intended Firebase project and the app-level/two-device tests above pass.
