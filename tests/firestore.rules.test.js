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

test("a superseded device cannot create backup records", async () => {
  const db = ownerDb();
  await assertFails(setDoc(
    doc(db, "dataHubBackups", STORE_ID, "records", "stale-device-record"),
    validRecord(1, { recordId: "stale-device-record", deviceId: "device-b" })
  ));
});

test("record identity cannot disagree with the Firestore path", async () => {
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

test("active device can reserve a barcode only once", async () => {
  const db = ownerDb();
  const reservation = doc(db, "storeBarcodeReservations", STORE_ID, "barcodes", "000000000123");
  await assertSucceeds(setDoc(reservation, { storeId: STORE_ID, barcode: "000000000123", ownerUid, deviceId: activeDeviceId, createdAt: 1000 }));
  await assertFails(setDoc(reservation, { storeId: STORE_ID, barcode: "000000000123", ownerUid, deviceId: activeDeviceId, createdAt: 1002 }));
  await assertFails(setDoc(doc(db, "storeBarcodeReservations", STORE_ID, "barcodes", "000000000124"), { storeId: STORE_ID, barcode: "000000000124", ownerUid, deviceId: "device-b", createdAt: 1001 }));
});

test("unreviewed collections remain denied", async () => {
  await assertFails(getDocs(collection(ownerDb(), "customers")));
});

test("store owner can delete a matching backup record for guarded retention cleanup", async () => {
  await assertSucceeds(deleteDoc(doc(ownerDb(), "dataHubBackups", STORE_ID, "records", RECORD_ID)));
});


test("after device B takes over, queued offline writes from device A are rejected", async () => {
  const db = ownerDb();
  await assertSucceeds(updateDoc(doc(db, "storeDeviceSessions", STORE_ID), {
    activeDeviceId: "device-b",
  }));
  await assertFails(setDoc(
    doc(db, "dataHubBackups", STORE_ID, "records", "queued-from-offline-a"),
    validRecord(1, { recordId: "queued-from-offline-a", deviceId: "device-a" })
  ));
});

test("after device takeover, the old device cannot mutate an existing backup", async () => {
  const db = ownerDb();
  await assertSucceeds(updateDoc(doc(db, "storeDeviceSessions", STORE_ID), {
    activeDeviceId: "device-b",
  }));
  await assertFails(updateDoc(
    doc(db, "dataHubBackups", STORE_ID, "records", RECORD_ID),
    { revision: 2, updatedAt: 1002, deviceId: "device-a" }
  ));
});

test("device session cannot be deleted to bypass single-device enforcement", async () => {
  await assertFails(deleteDoc(doc(ownerDb(), "storeDeviceSessions", STORE_ID)));
});

test("invalid revisions and malformed backup payloads are rejected", async () => {
  const db = ownerDb();
  await assertFails(setDoc(
    doc(db, "dataHubBackups", STORE_ID, "records", "negative-revision"),
    validRecord(1, { recordId: "negative-revision", revision: -1 })
  ));
  await assertFails(setDoc(
    doc(db, "dataHubBackups", STORE_ID, "records", "non-map-payload"),
    validRecord(1, { recordId: "non-map-payload", payload: "not-an-object" })
  ));
  await assertFails(setDoc(
    doc(db, "dataHubBackups", STORE_ID, "records", "wrong-store"),
    validRecord(1, { recordId: "wrong-store", storeId: "another-store" })
  ));
});

test("barcode reservations cannot be edited or deleted after creation", async () => {
  const db = ownerDb();
  const reservation = doc(db, "storeBarcodeReservations", STORE_ID, "barcodes", "000000000555");
  await assertSucceeds(setDoc(reservation, {
    storeId: STORE_ID,
    barcode: "000000000555",
    ownerUid,
    deviceId: activeDeviceId,
    createdAt: 1000,
  }));
  await assertFails(updateDoc(reservation, { createdAt: 2000 }));
  await assertFails(deleteDoc(reservation));
});

test("a stale device cannot delete another device's backup during cleanup", async () => {
  const db = ownerDb();
  await assertSucceeds(updateDoc(doc(db, "storeDeviceSessions", STORE_ID), {
    activeDeviceId: "device-b",
  }));
  await assertFails(deleteDoc(doc(db, "dataHubBackups", STORE_ID, "records", RECORD_ID)));
});

test("reservation and backup writes fail when the active-device session is missing", async () => {
  await env.withSecurityRulesDisabled(async (context) => {
    await deleteDoc(doc(context.firestore(), "storeDeviceSessions", STORE_ID));
  });
  const db = ownerDb();
  await assertFails(setDoc(
    doc(db, "dataHubBackups", STORE_ID, "records", "no-session"),
    validRecord(1, { recordId: "no-session" })
  ));
  await assertFails(setDoc(
    doc(db, "storeBarcodeReservations", STORE_ID, "barcodes", "000000000777"),
    { storeId: STORE_ID, barcode: "000000000777", ownerUid, deviceId: activeDeviceId, createdAt: 1000 }
  ));
});
