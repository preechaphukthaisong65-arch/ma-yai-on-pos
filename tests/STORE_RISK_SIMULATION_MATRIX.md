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
| Same single phone goes offline, continues selling, then reconnects | Replay its pending sales in order; each bill is committed once; stock/cash/debt/history reconcile | Needs single-device Android integration test |
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
| Full payment of selected debt bills only | Each selected bill is settled in full; underpayment and overpayment are rejected; balance/status/receipt/history agree | Cloud transaction code exists; needs Firebase emulator integration test |
| Double-tap payment / two devices pay same debt | At most one valid allocation per current bill revision | Needs concurrent transaction test |
| Payment amount exceeds current debt or bill already paid | Reject without changing any record | Guard exists; needs Firebase emulator integration test |
| Network loss before/during/after sale or payment | No silent success; local pending data preserved; no duplicate on retry | Needs Android offline/online integration test |
| Reconnect with identical, missing, conflicting, or duplicate-barcode records | Restore missing only; never overwrite/delete conflict silently | Restore code has conflict checks; needs seeded DB integration test |
| Owner replaces a broken phone or reinstalls app while old phone is unavailable | Restore last cloud-confirmed data; clearly disclose any backup lag; never invent missing local-only sales | Needs backup/restore integration test |
| App force-stop/restart, Android kills process, backup retry | Pending records survive and are retried without duplication | Needs Android lifecycle test |
| Receipt reprint, reports, debt reminders, retention cleanup | No financial records altered by viewing/printing/notifications; retention preserves required history | Needs app-level regression tests |
| Firestore rules deployed to actual Firebase project | Production backend enforces the same rules tested in CI | Not verified; deployment requires project-owner access |

## Known residual risks found during this review

- **Single-device offline recovery:** the primary expected case is the same phone temporarily losing internet, continuing to sell locally, then reconnecting. Offline bills must replay in deterministic order, and bill, receipt, stock, debt, and history effects must be idempotent. A replacement phone is a separate disaster-recovery case: cloud can restore only data that had actually been backed up before the old phone failed; the app must expose backup freshness and cannot promise recovery of unuploaded local-only changes.
- **Orphan barcode reservation:** if Firestore reserves a barcode but local product persistence subsequently fails, the reservation is intentionally not reused or automatically deleted. This avoids accidental duplicate assignment, but consumes that barcode number and needs an explicit, safe recovery procedure. The asynchronous double-tap window was fixed in `BarcodeScreen.java`; fault-injection testing for a local-save failure is still required.

## Safety boundaries

- Automated rules tests use a disposable Firebase emulator project and synthetic records; they do not touch the shop's production data.
- The test suite does not prove that Android UI, SQLite transactions, WorkManager retries, or real Firebase behavior are correct.
- Do not deploy or use as production-ready until the current rules are deployed to the intended Firebase project and the app-level/two-device tests above pass.


## End-to-end shop-day scenario suite (not yet executed on Android)

Use a disposable test store and synthetic products only. Record the initial stock, product price, customer debt, bill count, receipt count, sales total, and audit/history count before each scenario. After each scenario, compare every value with the expected delta. Mark PASS only from runtime evidence (test output or captured database assertions), not source inspection.

Severity: **P0** = can lose money or corrupt stock/debt; **P1** = sale blocked or operational data lost; **P2** = usability/reporting defect.

| ID | Realistic event / fault injection | Expected safe result | Severity | Status |
|---|---|---|---|---|
| FLOW-01 | Customer selects one in-stock product and pays exact cash | One bill, one receipt, total correct, stock -quantity, change 0 | P0 | Not run on Android |
| FLOW-02 | Customer pays more than total | Change equals received minus total; sale committed once | P0 | Not run on Android |
| FLOW-03 | Cash received is less than total | Reject confirmation; no bill, receipt, stock or sales-total mutation | P0 | Not run on Android |
| FLOW-04 | Cart is empty or contains quantity zero/negative | Block sale; no financial or stock mutation | P0 | Not run on Android |
| FLOW-05 | Quantity exceeds stock; stock is exactly equal to requested quantity | First case rejects without mutation; second succeeds and leaves zero stock | P0 | Not run on Android |
| FLOW-06 | Product is disabled/deleted or price changed after it entered the cart | Server validates current product state/price; reject stale cart or require explicit recalculation | P0 | Not run on Android |
| FLOW-07 | Same product added several times or quantity edited repeatedly | Canonical quantity and total correct; no lost/duplicated line | P0 | Not run on Android |
| FLOW-08 | User taps confirm twice rapidly | One idempotent sale; stock decremented once | P0 | Not run on Android |
| FLOW-09 | App is killed immediately before, during, and after submit | On reopen, outcome can be determined; retry never creates a second sale | P0 | Not run on Android |
| FLOW-10 | Server commits sale but response is lost; owner retries same request | Return the existing committed bill/receipt; no second stock deduction | P0 | Static regression assertion only; runtime not run |
| FLOW-11 | Retry uses the same idempotency key but changed item/quantity/price | Reject request-hash mismatch; do not mutate records | P0 | Not run on Android |
| FLOW-12 | Network drops before request reaches server | Show pending/failed, preserve local intent; never falsely report server-confirmed sale | P0 | Not run on Android |
| FLOW-13 | Network drops after server commit but before response | Recover the same committed result on retry; one bill and one stock deduction | P0 | Not run on Android |
| FLOW-14 | Several offline sales of the same SKU occur within one millisecond | Stable unique order and revisions; after reconnect, no duplicate or lost decrement | P0 | Not run on Android |
| FLOW-15 | Device A makes offline sales, then device B takes over; A reconnects | Must reconcile each sale with stock/money invariants or quarantine for explicit resolution; never silently discard or partially apply | P0 | Known gap; takeover reconciliation not implemented |
| FLOW-16 | Old device attempts a brand-new sale after it has been superseded | Server rejects new stale-device write; UI must make outcome clear and preserve pending evidence for resolution | P0 | Not run end-to-end |
| FLOW-17 | Two requests sell the final unit concurrently | At most one succeeds; stock never below zero; rejected request has no partial bill | P0 | Not run concurrently |
| FLOW-18 | Two devices submit the same sale simultaneously | One committed bill only; duplicate request returns same outcome | P0 | Not run concurrently |
| FLOW-19 | Credit sale to new and existing customer | Correct fee per configured policy, debt and customer link; bill, receipt, stock and history committed consistently | P0 | Not run on Android |
| FLOW-20 | Full payment of selected bill(s) is tapped twice or submitted from two sessions; partial payment is attempted | One full settlement only; partial or excess amount rejected; balance/status/receipt/history remain consistent | P0 | Not run concurrently |
| FLOW-21 | Debt payment exceeds remaining debt or targets already-paid bill | Reject without changing debt, cash, receipt or history | P0 | Not run on Android |
| FLOW-22 | Receive stock and sell same SKU during overlapping writes | No lost update; final stock equals initial + accepted receipts - accepted sales | P0 | Not run concurrently |
| FLOW-23 | Stocktake/edit races with a sale based on an old revision | One conflicting mutation rejects/reloads; no silent overwrite | P0 | Not run concurrently |
| FLOW-24 | Restore after reinstall or new device login | Recover only cloud-confirmed records; preserve identity and links; no duplicate bills/products/payments | P0 | Not run on Android |
| FLOW-25 | Restore encounters same ID with different payload/revision | Preserve both evidence and quarantine conflict; never silently overwrite/delete | P0 | Not run on Android |
| FLOW-26 | Duplicate barcode reservation or local save failure after reservation | No duplicate barcode; reservation failure is visible and has documented recovery | P1 | Fault injection not run |
| FLOW-27 | App process is killed with pending offline queue; device restarts | Queue survives, retries idempotently, and does not reorder dependent stock/debt mutations | P0 | Not run on Android |
| FLOW-28 | Owner signs out, unverified account logs in, or a different account tries store access | Enforce verified owner identity and active-device authorization; no data leakage or writes | P0 | Rules tests cover parts; app flow not run |
| FLOW-29 | Wrong system clock, time-zone change, duplicate timestamps | Correctness relies on IDs/revisions/server transaction order, not client clock alone | P0 | Not run |
| FLOW-30 | View bill/report, re-open receipt, export data or trigger reminder | Read-only actions do not change stock, cash, debt or sales totals | P1 | Not run on Android |
| FLOW-31 | Cancellation/refund/void (if feature exists) | Explicit compensating transaction and audit trail; no silent deletion of a committed sale | P0 | Feature coverage must be confirmed |
| FLOW-32 | App is upgraded while local pending data exists | Migration preserves records and idempotency identifiers; no queue loss | P0 | Not run on Android |
| FLOW-33 | Firestore permission denied, timeout, unavailable service, malformed response | Fail closed for online commit; preserve local pending evidence; show actionable status | P0 | Not run end-to-end |
| FLOW-34 | End-of-day totals compared with bills, receipts, payments, stock movement and customer debt | All totals reconcile exactly; any mismatch is surfaced, not hidden | P0 | Not run on Android |

## Required evidence for every P0 test

1. Test case ID, app commit SHA, device/Android version, emulator/backend version, and starting fixture.
2. Exact action sequence and injected fault (if any).
3. Expected versus actual bill count, receipt count, stock, sales total, cash/change, debt, and history.
4. Logs/assertions showing whether the server committed, rejected, or quarantined the operation.
5. Repeat run and retry run to establish idempotency.
6. Result must be one of PASS, FAIL, BLOCKED, or NOT RUN. Never treat NOT RUN or source-pattern checks as PASS.

## Release gate

Production release is blocked while any P0 scenario is FAIL, BLOCKED, or NOT RUN. In particular, offline device takeover with pending sales, two-device concurrent final-unit sales, lost-response idempotency, and uninstall/reinstall restore require real integration evidence. Hardware receipt-printer and physical barcode-printer tests are intentionally outside this suite; barcode lookup/generation and stored sale records remain in scope.


## Corrected operating assumptions for Ma Yai On POS

- The normal checkout flow uses one physical phone at a time. Do not treat two simultaneous checkout devices as a routine shop workflow.
- The local database is the working source for day-to-day screens and offline continuity. Cloud is used to protect and restore records. However, code currently also contains a server-committed sale path; verify the actual sale call path before changing the consistency model. Do not assume “cloud backup only” unless the app code has been changed and integration-tested accordingly.
- Scanning a barcode proves only that a product identifier was read. It does not prove positive stock. Before confirming a sale, validate the current local stock and requested quantity, prevent duplicate scan events from accidentally multiplying quantity, and make the final sale + stock decrement a single local database transaction.
- The main real-world scenarios are: scan an existing item; unknown barcode; repeated scan; change quantity; remove item; insufficient cash; exact cash; excess cash/change; app killed before/after save; phone offline before/during/after sale; reconnect/retry; backup failure; low storage; database migration; restore after reinstall; restore on replacement phone; and end-of-day reconciliation.
- Cloud backup is not equivalent to a real-time replica. Record and show the last successful backup time/status. A phone failure can lose changes created after that successful backup unless there is a separately verified durable recovery path.
