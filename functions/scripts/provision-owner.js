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
  const ownerEmail = required("OWNER_EMAIL").toLowerCase();
  const checkOnly = process.env.CHECK_ONLY === "true";
  if (!validDocumentId(storeId) || ownerEmail.length > 254 || !ownerEmail.includes("@")) {
    throw new Error("STORE_ID or OWNER_EMAIL is invalid.");
  }

  const app = initializeApp({ credential: applicationDefault() });
  const user = await getAuth(app).getUserByEmail(ownerEmail);
  if (user.disabled) throw new Error("Owner account is disabled.");
  if (user.emailVerified !== true) throw new Error("Owner account email must be verified before binding.");

  const db = getFirestore(app);
  const ref = db.collection("storeOwners").doc(storeId);
  await db.runTransaction(async tx => {
    const snap = await tx.get(ref);
    if (snap.exists) {
      if (snap.get("ownerUid") !== user.uid) {
        throw new Error("Store ID is already bound to a different owner UID. Refusing to overwrite.");
      }
      return;
    }
    if (!checkOnly) {
      tx.create(ref, {
        storeId,
        ownerUid: user.uid,
        ownerEmail: user.email || ownerEmail,
        createdAt: FieldValue.serverTimestamp(),
        provisionedBy: "github-actions"
      });
    }
  });

  console.log(checkOnly
    ? "Owner account and store binding preflight passed; no data was changed."
    : `Owner binding verified for store ID "${storeId}".`);
}

main().catch(error => {
  console.error(error.message || error);
  process.exitCode = 1;
});
