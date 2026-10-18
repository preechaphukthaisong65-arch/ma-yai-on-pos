
package com.example.newapp;

public interface CloudService {

    class SyncResult {
        public final boolean success;
        public final String message;
        public final String remoteId;

        private SyncResult(
                boolean success,
                String message,
                String remoteId
        ) {
            this.success = success;
            this.message = message;
            this.remoteId = remoteId;
        }

        public static SyncResult success(
                String message,
                String remoteId
        ) {
            return new SyncResult(true, message, remoteId);
        }

        public static SyncResult failure(String message) {
            return new SyncResult(false, message, null);
        }
    }

    /**
     * ส่งข้อมูลสินค้าไปยังคลาวด์
     * operationId ต้องคงเดิมเมื่อส่งซ้ำ เพื่อช่วยป้องกันข้อมูลซ้ำ
     */
    SyncResult syncProduct(
            String operationId,
            String payloadJson
    );

    /**
     * ส่งข้อมูลการขายไปยังคลาวด์
     */
    SyncResult syncSale(
            String operationId,
            String payloadJson
    );

    /**
     * ส่งประวัติการเคลื่อนไหวสต๊อกไปยังคลาวด์
     */
    SyncResult syncStockMovement(
            String operationId,
            String payloadJson
    );

    /**
     * ตรวจสอบว่าเชื่อมต่อผู้ให้บริการคลาวด์ได้หรือไม่
     */
    boolean isConfigured();
}
