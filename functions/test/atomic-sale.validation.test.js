const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

test("atomic sale handler includes required authorization and transaction gates", () => {
  const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
  for (const pattern of [
    /request\.auth\?\.token\?\.email_verified !== true/,
    /storeOwners/, /storeDeviceSessions/, /db\.runTransaction/,
    /idempotencyKey/, /stock < item\.quantity/,
    /tx\.create\(billRef/, /tx\.create\(receiptRef/,
    /tx\.create\(saleHistoryRef/, /transactionType: "ขายสินค้า"/
  ]) assert.match(source, pattern);
});

test("sale input is bounded and price is read from server product records", () => {
  const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
  assert.match(source, /items\.length > 100/);
  assert.match(source, /const p = snap\.get\("payload"\), price = p && p\.price/);
  assert.match(source, /received < total/);
  assert.match(source, /serviceFee = paymentType === "credit"/);
});

test("server rejects stale product revisions and protects customer identity", () => {
  const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
  assert.match(source, /Number\(snap\.get\("revision"\)\) \|\| 0\) !== item\.localRevision/);
  assert.match(source, /customersQuery = records\.where\("dataType", "==", "credit_customer"\)/);
  assert.match(source, /enforceAppCheck: true/);
  assert.match(source, /requestHash/);
});

test("idempotent committed sale recovery is read-only and precedes the active-device gate", () => {
  const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
  const transaction = source.indexOf("return db.runTransaction(async tx =>");
  const ownerCheck = source.indexOf('ownerSnap.get("ownerUid") !== uid', transaction);
  const priorBill = source.indexOf("if (priorBill.exists)", transaction);
  const idempotentReturn = source.indexOf("return { alreadyCommitted: true", priorBill);
  const activeDeviceGate = source.indexOf("const sessionSnap = await tx.get(sessionRef)", transaction);
  assert.ok(transaction >= 0 && ownerCheck > transaction);
  assert.ok(priorBill > ownerCheck && idempotentReturn > priorBill);
  assert.ok(activeDeviceGate > idempotentReturn);
  assert.match(source.slice(priorBill, activeDeviceGate), /requestHash/);
  assert.match(source.slice(priorBill, activeDeviceGate), /tx\.get\(priorReceipt\)|tx\.get\(receiptRef\)/);
});
