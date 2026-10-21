const { initializeApp, applicationDefault } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");

function required(name) {
  const value = process.env[name];
  if (typeof value !== "string" || !value.trim()) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value.trim();
}

function validDocumentId(value) {
  return value.length <= 160 && !value.includes("/") && value !== "." && value !== "..";
}

async function main() {
  const storeId = required("STORE_ID");
  const ownerUid = required("OWNER_UID");
  if (!validDocumentId(storeId) || !validDocumentId(ownerUid)) {
    throw new Error("STORE_ID or OWNER_UID is not a valid Firestore document identifier.");
  }

  const app = initializeApp({ credential: applicationDefault() });
  const auth = getAuth(app);
  const user = await auth.getUser(ownerUid);
  if (user.disabled) throw new Error("Owner account is disabled.");
  if (user.emailVerified !== true) throw new Error("Owner account email must be verified before binding.");

  const db = getFirestore(app);
  const ref = db.collection("storeOwners").doc(storeId);
  await db.runTransaction(async tx => {
    const snap = await tx.get(ref);
    if (snap.exists) {
      if (snap.get("ownerUid") !== ownerUid) {
        throw new Error("Store ID is already bound to a different owner UID. Refusing to overwrite.");
      }
      return;
    }
    tx.create(ref, {
      storeId,
      ownerUid,
      ownerEmail: user.email || null,
      createdAt: FieldValue.serverTimestamp(),
      provisionedBy: "github-actions"
    });
  });

  console.log(`Owner binding verified for store ID "${storeId}".`);
}

main().catch(error => {
  console.error(error.message || error);
  process.exitCode = 1;
});
