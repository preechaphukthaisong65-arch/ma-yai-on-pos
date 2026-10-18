
package com.example.newapp;

public class CloudServiceUnavailable implements CloudService {

    @Override
    public SyncResult syncProduct(
            String operationId,
            String payloadJson
    ) {
        return SyncResult.failure(
                "ยังไม่ได้ตั้งค่าระบบคลาวด์"
        );
    }

    @Override
    public SyncResult syncSale(
            String operationId,
            String payloadJson
    ) {
        return SyncResult.failure(
                "ยังไม่ได้ตั้งค่าระบบคลาวด์"
        );
    }

    @Override
    public SyncResult syncStockMovement(
            String operationId,
            String payloadJson
    ) {
        return SyncResult.failure(
                "ยังไม่ได้ตั้งค่าระบบคลาวด์"
        );
    }

    @Override
    public boolean isConfigured() {
        return false;
    }
}
