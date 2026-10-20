package com.example.newapp.datahub;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

/** Server-backed single-active-install lease. Offline devices cannot learn of takeover until reconnect. */
public final class DeviceSessionManager {
    public interface ResultCallback {
        void onResult(boolean success, String message);
    }

    public interface ActiveCallback {
        void onActiveChanged(boolean active);
    }

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    private DocumentReference sessionDoc(String storeId) {
        return firestore.collection("storeDeviceSessions").document(storeId);
    }

    /** Call only after successful password authentication and verified-email check. */
    public void claim(String storeId, String deviceId, ResultCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        String store = storeId == null ? "" : storeId.trim();
        String device = deviceId == null ? "" : deviceId.trim();
        if (user == null || !user.isEmailVerified() || store.isEmpty() || device.isEmpty()) {
            callback.onResult(false, "บัญชีหรือรหัสร้านไม่ถูกต้อง");
            return;
        }
        DocumentReference ownerRef = firestore.collection("storeOwners").document(store);
        DocumentReference sessionRef = sessionDoc(store);
        firestore.runTransaction(transaction -> {
            DocumentSnapshot owner = transaction.get(ownerRef);
            if (!owner.exists() || !user.getUid().equals(owner.getString("ownerUid"))) {
                throw new IllegalStateException("บัญชีนี้ไม่ได้รับสิทธิ์ในร้านที่ระบุ");
            }
            Map<String, Object> session = new HashMap<>();
            session.put("storeId", store);
            session.put("ownerUid", user.getUid());
            session.put("activeDeviceId", device);
            session.put("updatedAt", System.currentTimeMillis());
            transaction.set(sessionRef, session);
            return null;
        }).addOnSuccessListener(unused -> callback.onResult(true, "เครื่องนี้ได้รับสิทธิ์ใช้งานแล้ว"))
          .addOnFailureListener(error -> callback.onResult(false,
                  error.getLocalizedMessage() == null ? "ยึดสิทธิ์เครื่องไม่สำเร็จ" : error.getLocalizedMessage()));
    }

    public ListenerRegistration watch(String storeId, String deviceId, ActiveCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || storeId == null || storeId.trim().isEmpty() || deviceId == null) {
            callback.onActiveChanged(false);
            return null;
        }
        return sessionDoc(storeId.trim()).addSnapshotListener((snapshot, error) -> {
            if (error != null || snapshot == null) {
                // Preserve cached/offline work when connectivity is unavailable.
                // The listener will re-check the server session when connectivity returns.
                return;
            }
            if (!snapshot.exists()) {
                callback.onActiveChanged(false);
                return;
            }
            callback.onActiveChanged(user.getUid().equals(snapshot.getString("ownerUid"))
                    && deviceId.equals(snapshot.getString("activeDeviceId")));
        });
    }
}
