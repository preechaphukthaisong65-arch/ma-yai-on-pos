const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const crypto = require("node:crypto");

initializeApp();
const db = getFirestore();
const BACKUPS = "dataHubBackups";

function fail(code, message) { throw new HttpsError(code, message); }
function validId(value) {
  return typeof value === "string" && value.trim().length > 0 && value.length <= 160
    && !value.includes("/");
}
function finitePositive(value) { return Number.isFinite(value) && value > 0; }
function asRecord(storeId, recordId, dataType, revision, payload, extra = {}) {
  return { storeId, recordId, dataType, schemaVersion: 1,
    revision: Math.max(1, revision), updatedAt: Date.now(), payload, ...extra };
}

exports.commitPosSale = onCall({ maxInstances: 10 }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid || request.auth?.token?.email_verified !== true) {
    fail("unauthenticated", "เจ้าของร้านต้องเข้าสู่ระบบและยืนยันอีเมลก่อน");
  }
  const input = request.data || {};
  const storeId = input.storeId, deviceId = input.deviceId, billId = input.billId;
  const paymentType = input.paymentType, received = input.cashReceived;
  const idempotencyKey = input.idempotencyKey, items = input.items;
  if (![storeId, deviceId, billId, idempotencyKey].every(validId)) {
    fail("invalid-argument", "รหัสร้าน เครื่อง บิล หรือ idempotency ไม่ถูกต้อง");
  }
  if (!["cash", "credit"].includes(paymentType)) fail("invalid-argument", "ประเภทการชำระไม่ถูกต้อง");
  if (!Array.isArray(items) || items.length < 1 || items.length > 100) {
    fail("invalid-argument", "จำนวนรายการสินค้าต้องอยู่ระหว่าง 1–100");
  }
  if (paymentType === "cash" && (!Number.isFinite(received) || received < 0)) {
    fail("invalid-argument", "จำนวนเงินสดไม่ถูกต้อง");
  }
  const normalized = [], seen = new Set();
  for (const item of items) {
    if (!validId(item?.productRecordId) || !finitePositive(item?.quantity)
        || item.quantity > 100000 || !Number.isInteger(item?.localRevision)
        || item.localRevision < 0 || seen.has(item.productRecordId)) {
      fail("invalid-argument", "รายการสินค้าซ้ำหรือจำนวนไม่ถูกต้อง");
    }
    seen.add(item.productRecordId);
    normalized.push({ productRecordId: item.productRecordId, quantity: item.quantity, localRevision: item.localRevision });
  }
  const customerId = typeof input.customerRecordId === "string" ? input.customerRecordId : "";
  const inputCustomer = input.customer && typeof input.customer === "object" ? input.customer : null;
  if (customerId && !validId(customerId)) fail("invalid-argument", "รหัสลูกค้าไม่ถูกต้อง");
  if (paymentType === "credit" && (!customerId || !inputCustomer
      || inputCustomer.recordId !== customerId
      || typeof inputCustomer.firstName !== "string" || !inputCustomer.firstName.trim()
      || typeof inputCustomer.lastName !== "string" || !inputCustomer.lastName.trim())) {
    fail("invalid-argument", "ข้อมูลลูกค้าสำหรับขายเชื่อไม่ครบถ้วน");
  }
  const requestHash = crypto.createHash("sha256").update(JSON.stringify({
    storeId, deviceId, billId, paymentType, cashReceived: paymentType === "cash" ? received : 0,
    customerRecordId: customerId, items: normalized.map(x => ({ productRecordId: x.productRecordId, quantity: x.quantity }))
  })).digest("hex");

  const ownerRef = db.collection("storeOwners").doc(storeId);
  const sessionRef = db.collection("storeDeviceSessions").doc(storeId);
  const records = db.collection(BACKUPS).doc(storeId).collection("records");
  const billRef = records.doc(billId), receiptId = "receipt:" + billId;
  const receiptRef = records.doc(receiptId), saleHistoryId = "sale_history:" + billId;
  const saleHistoryRef = records.doc(saleHistoryId);
  const productRefs = normalized.map(x => records.doc(x.productRecordId));
  const stockHistoryRefs = normalized.map((x, i) => records.doc("stock_history:sale:" + billId + ":" + i));
  const customerRef = customerId ? records.doc(customerId) : null;

  return db.runTransaction(async tx => {
    const ownerSnap = await tx.get(ownerRef), sessionSnap = await tx.get(sessionRef);
    if (!ownerSnap.exists || ownerSnap.get("ownerUid") !== uid) fail("permission-denied", "บัญชีนี้ไม่มีสิทธิ์ในร้าน");
    if (!sessionSnap.exists || sessionSnap.get("ownerUid") !== uid
        || sessionSnap.get("activeDeviceId") !== deviceId) {
      fail("permission-denied", "เครื่องนี้ไม่ได้เป็นเครื่องที่มีสิทธิ์ใช้งานร้าน");
    }
    const priorBill = await tx.get(billRef);
    if (priorBill.exists) {
      if (priorBill.get("idempotencyKey") !== idempotencyKey || priorBill.get("requestHash") !== requestHash) {
        fail("already-exists", "รหัสบิลนี้ถูกใช้กับคำขออื่นแล้ว");
      }
      // Idempotent retry: return the committed bill and its related records without
      // applying a second stock decrement. Local expectedRevision values are CAS
      // guards only; they never affect server authorization or stock validation.
      const priorProducts = [];
      for (const ref of productRefs) priorProducts.push(await tx.get(ref));
      const priorReceipt = await tx.get(receiptRef);
      const priorAudit = await tx.get(saleHistoryRef);
      const priorStockHistory = [];
      for (const ref of stockHistoryRefs) priorStockHistory.push(await tx.get(ref));
      const retryRecords = [];
      for (let i = 0; i < priorProducts.length; i++) {
        if (priorProducts[i].exists) retryRecords.push({
          ...priorProducts[i].data(), expectedRevision: normalized[i].localRevision
        });
      }
      for (const snap of priorStockHistory) if (snap.exists) retryRecords.push(snap.data());
      if (priorReceipt.exists) retryRecords.push(priorReceipt.data());
      if (priorAudit.exists) retryRecords.push(priorAudit.data());
      retryRecords.push(priorBill.data());
      return { alreadyCommitted: true, bill: priorBill.data(), records: retryRecords };
    }
    const productSnaps = [];
    for (const ref of productRefs) productSnaps.push(await tx.get(ref));
    const customerSnap = customerRef ? await tx.get(customerRef) : null;
    let customerPayload = customerSnap?.exists ? (customerSnap.get("payload") || {}) : null;
    let customerToCreate = null;
    if (paymentType === "credit") {
      if (customerSnap?.exists) {
        if (customerSnap.get("storeId") !== storeId || customerSnap.get("dataType") !== "credit_customer") {
          fail("failed-precondition", "ข้อมูลลูกค้าไม่ตรงกับร้านนี้");
        }
      } else {
        // A new offline-created customer may not have reached Firestore yet.
        // Check server-side names before creating its stable local record ID.
        const customersQuery = records.where("dataType", "==", "credit_customer");
        const customersSnapshot = await tx.get(customersQuery);
        const first = inputCustomer.firstName.trim().toLocaleLowerCase("th");
        const last = inputCustomer.lastName.trim().toLocaleLowerCase("th");
        for (const existing of customersSnapshot.docs) {
          if (existing.id === customerId || existing.get("storeId") !== storeId) continue;
          const p = existing.get("payload") || {};
          if (String(p.firstName || "").trim().toLocaleLowerCase("th") === first
              && String(p.lastName || "").trim().toLocaleLowerCase("th") === last) {
            fail("failed-precondition", "พบลูกค้าชื่อเดียวกันในข้อมูลกลาง กรุณาซิงก์และเลือกบัญชีลูกค้าเดิมก่อน");
          }
        }
        customerPayload = {
          firstName: inputCustomer.firstName.trim(),
          lastName: inputCustomer.lastName.trim(),
          createdAt: Number(inputCustomer.createdAt) || Date.now()
        };
        customerToCreate = asRecord(storeId, customerId, "credit_customer", 1, customerPayload,
          { uploadedBy: uid, deviceId });
      }
    }

    const now = Date.now(), nextProducts = [], lineItems = [];
    let subtotal = 0;
    for (let i = 0; i < productSnaps.length; i++) {
      const snap = productSnaps[i], item = normalized[i];
      if (!snap.exists || snap.get("storeId") !== storeId || snap.get("dataType") !== "product") {
        fail("failed-precondition", "ไม่พบสินค้าในระบบกลาง: " + item.productRecordId);
      }
      const p = snap.get("payload"), price = p && p.price, stock = p && p.stockQuantity;
      if (!Number.isFinite(price) || price < 0 || !Number.isFinite(stock) || stock < item.quantity) {
        fail("failed-precondition", "สต๊อกไม่เพียงพอหรือข้อมูลสินค้าไม่ถูกต้อง: " + item.productRecordId);
      }
      const lineTotal = Math.round((price * item.quantity + Number.EPSILON) * 100) / 100;
      subtotal = Math.round((subtotal + lineTotal + Number.EPSILON) * 100) / 100;
      nextProducts.push({ ref: productRefs[i], baseRevision: Number(snap.get("revision")) || 0, record: asRecord(storeId, item.productRecordId, "product",
        (Number(snap.get("revision")) || 0) + 1,
        { ...p, stockQuantity: stock - item.quantity, updatedAt: now }, { uploadedBy: uid, deviceId }) });
      lineItems.push({ productRecordId: item.productRecordId, name: String(p.name || ""),
        barcode: String(p.barcode || ""), quantity: item.quantity, unit: String(p.unit || "ชิ้น"),
        unitPrice: price, lineTotal });
    }
    const serviceFee = paymentType === "credit" ? Math.round((subtotal * 0.05 + Number.EPSILON) * 100) / 100 : 0;
    const total = Math.round((subtotal + serviceFee + Number.EPSILON) * 100) / 100;
    if (paymentType === "cash" && received < total) fail("failed-precondition", "เงินสดที่รับมาต่ำกว่ายอดขาย");

    customerPayload = customerPayload || {};
    const customerName = customerId ? (String(customerPayload.firstName || "") + " " + String(customerPayload.lastName || "")).trim() : "";
    const billPayload = { billId, createdAt: now, paymentType,
      paymentStatus: paymentType === "cash" ? "จ่ายแล้ว" : "ค้างชำระ",
      subtotal, serviceFee, total, debt: paymentType === "credit" ? total : 0,
      remainingBalance: paymentType === "credit" ? total : 0,
      cashReceived: paymentType === "cash" ? received : 0,
      change: paymentType === "cash" ? Math.round((received - total + Number.EPSILON) * 100) / 100 : 0,
      customerRecordId: customerId, customerName,
      customerFirstName: String(customerPayload.firstName || ""),
      customerLastName: String(customerPayload.lastName || ""), items: lineItems };
    const billRecord = asRecord(storeId, billId, "sale_bill", 1, billPayload,
      { uploadedBy: uid, deviceId, idempotencyKey, requestHash });
    const receiptPayload = { receiptId, billId,
      receiptType: paymentType === "credit" ? "ใบเสร็จขายเชื่อ" : "ใบเสร็จรับเงิน",
      createdAt: now, subtotal, serviceFee, total, printStatus: "pending", items: lineItems,
      cashReceived: billPayload.cashReceived, change: billPayload.change,
      paymentStatus: billPayload.paymentStatus, customerRecordId: customerId, customerName };
    const receiptRecord = asRecord(storeId, receiptId, "receipt", 1, receiptPayload, { uploadedBy: uid, deviceId });
    const auditPayload = { billId, eventType: "sale_confirmed", paymentType,
      paymentStatus: billPayload.paymentStatus, total, createdAt: now,
      customerRecordId: customerId, saleSnapshot: billPayload };
    const auditRecord = asRecord(storeId, saleHistoryId, "sale_history", 1, auditPayload, { uploadedBy: uid, deviceId });

    // All reads are completed before writes; Firestore retries this transaction on contention.
    if (customerToCreate) tx.create(customerRef, customerToCreate);
    for (let i = 0; i < nextProducts.length; i++) {
      tx.set(nextProducts[i].ref, nextProducts[i].record);
      const oldStock = Number(productSnaps[i].get("payload").stockQuantity);
      const newStock = nextProducts[i].record.payload.stockQuantity;
      const hId = stockHistoryRefs[i].id;
      tx.create(stockHistoryRefs[i], asRecord(storeId, hId, "stock_history", 1, {
        historyId: hId, productRecordId: normalized[i].productRecordId,
        productName: lineItems[i].name, barcode: lineItems[i].barcode,
        transactionType: "ขายสินค้า", beforeQuantity: oldStock, afterQuantity: newStock,
        delta: newStock - oldStock, changedAt: now, note: "ขายบิล " + billId
      }, { uploadedBy: uid, deviceId }));
    }
    tx.create(billRef, billRecord);
    tx.create(receiptRef, receiptRecord);
    tx.create(saleHistoryRef, auditRecord);
    return { alreadyCommitted: false, bill: billRecord,
      records: [...(customerToCreate ? [customerToCreate] : []), ...nextProducts.map((x, i) => ({ ...x.record, expectedRevision: normalized[i].localRevision })), ...stockHistoryRefs.map((ref, i) => ({
        storeId, recordId: ref.id, dataType: "stock_history", schemaVersion: 1, revision: 1,
        updatedAt: now, payload: { historyId: ref.id, productRecordId: normalized[i].productRecordId,
          productName: lineItems[i].name, barcode: lineItems[i].barcode, transactionType: "ขายสินค้า",
          beforeQuantity: Number(productSnaps[i].get("payload").stockQuantity),
          afterQuantity: nextProducts[i].record.payload.stockQuantity,
          delta: nextProducts[i].record.payload.stockQuantity - Number(productSnaps[i].get("payload").stockQuantity),
          changedAt: now, note: "ขายบิล " + billId }, uploadedBy: uid, deviceId
      })), billRecord, receiptRecord, auditRecord] };
  });
});
