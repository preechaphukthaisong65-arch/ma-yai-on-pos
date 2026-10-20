const { before, beforeEach, after, test } = require("node:test");
const fs = require("node:fs");
const path = require("node:path");
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
} = require("firebase/firestore");

const PROJECT_ID = "demo-ma-yai-on-pos";
const STORE_ID = "store-test-001";
const RECORD_ID = "product-test-001";
const ownerUid = "verified-owner-uid";
const activeDeviceId = "device-a";
let env;

const validRecord = (revision = 1, overrides = {}) => ({
  recordId: RECORD_ID,
  storeId: STORE_ID,
  dataType: "product",
  schemaVersion: 1,
  updatedAt: 1000 + revision,
  revision,
  payload: { name: "Test item", stock: 10 },
  uploadedBy: ownerUid,
  deviceId: activeDeviceId,
  ...overrides,
});

before(async () => {
  env = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync(path.join(__dirname, "..", "firestore.rules"), "utf8"),
    },
  });
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "storeOwners", STORE_ID), { ownerUid });
    await setDoc(doc(db, "storeDeviceSessions", STORE_ID), { storeId: STORE_ID, ownerUid, activeDeviceId });
    await setDoc(
      doc(db, "dataHubBackups", STORE_ID, "records", RECORD_ID),
      validRecord()
    );
  });
});

after(async () => {
  if (env) await env.cleanup();
});

function ownerDb() {
  return env.authenticatedContext(ownerUid, { email_verified: true }).firestore();
}
function unverifiedDb() {
  return env.authenticatedContext(ownerUid, { email_verified: false }).firestore();
}
function otherOwnerDb() {
  return env.authenticatedContext("somebody-else", { email_verified: true }).firestore();
}

test("verified provisioned owner can read the store backup", async () => {
  await assertSucceeds(getDoc(doc(ownerDb(), "dataHubBackups", STORE_ID, "records", RECORD_ID)));
});

test("unverified owner cannot read store backup", async () => {
  await assertFails(getDoc(doc(unverifiedDb(), "dataHubBackups", STORE_ID, "records", RECORD_ID)));
});

test("another verified account cannot read or write this store", async () => {
  const db = otherOwnerDb();
  await assertFails(getDoc(doc(db, "dataHubBackups", STORE_ID, "records", RECORD_ID)));
  await assertFails(setDoc(
    doc(db, "dataHubBackups", STORE_ID, "records", "other-record"),
    validRecord(1, { recordId: "other-record", uploadedBy: "somebody-else" })
  ));
});

test("owner can create a valid new record from the active device", async () => {
  const db = ownerDb();
  const newId = "product-test-002";
  await assertSucceeds(setDoc(
    doc(db, "dataHubBackups", STORE_ID, "records", newId),
    validRecord(1, { recordId: newId })
  ));
});

test("a superseded device cannot create backup records", async () => {\n  const db = ownerDb();\n  await assertFails(setDoc(\n    doc(db, "dataHubBackups", STORE_ID, "records", "stale-device-record"),\n    validRecord(1, { recordId: "stale-device-record", deviceId: "device-b" })\n  ));\n});\n\ntest("record identity cannot disagree with the Firestore path", async () => {
  await assertFails(setDoc(
    doc(ownerDb(), "dataHubBackups", STORE_ID, "records", "different-path-id"),
    validRecord(1)
  ));
});

test("owner may update only with a strictly higher revision", async () => {
  const db = ownerDb();
  const ref = doc(db, "dataHubBackups", STORE_ID, "records", RECORD_ID);
  await assertSucceeds(updateDoc(ref, { revision: 2, updatedAt: 1002 }));
  await assertFails(updateDoc(ref, { revision: 2, updatedAt: 1003 }));
  await assertFails(updateDoc(ref, { revision: 1, updatedAt: 1001 }));
});

test("client cannot claim or modify store ownership", async () => {
  const db = ownerDb();
  await assertFails(setDoc(doc(db, "storeOwners", "attacker-store"), { ownerUid }));
  await assertFails(updateDoc(doc(db, "storeOwners", STORE_ID), { ownerUid: "attacker" }));
  await assertFails(deleteDoc(doc(db, "storeOwners", STORE_ID)));
});

test("active device can reserve a barcode only once", async () => {\n  const db = ownerDb();\n  const reservation = doc(db, "storeBarcodeReservations", STORE_ID, "barcodes", "000000000123");\n  await assertSucceeds(setDoc(reservation, { storeId: STORE_ID, barcode: "000000000123", ownerUid, deviceId: activeDeviceId, createdAt: 1000 }));\n  await assertFails(setDoc(doc(db, "storeBarcodeReservations", STORE_ID, "barcodes", "000000000124"), { storeId: STORE_ID, barcode: "000000000124", ownerUid, deviceId: "device-b", createdAt: 1001 }));\n});\n\ntest("unreviewed collections remain denied", async () => {
  await assertFails(getDocs(collection(ownerDb(), "customers")));
});

test("store owner can delete a matching backup record for guarded retention cleanup", async () => {
  await assertSucceeds(deleteDoc(doc(ownerDb(), "dataHubBackups", STORE_ID, "records", RECORD_ID)));
});
