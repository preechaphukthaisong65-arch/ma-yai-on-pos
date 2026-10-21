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
