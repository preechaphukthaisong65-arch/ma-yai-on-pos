package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

/**
 * Central barcode-scanning gateway for every POS screen.
 *
 * Screens choose what to do with the returned barcode; this class owns the
 * shared ZXing launch and result-parsing flow. Keeping the existing
 * IntentIntegrator implementation preserves the scanner behavior that is
 * already working in the app.
 */
final class BarcodeScanner {
    private BarcodeScanner() {}

    static void launch(Activity activity) {
        launch(activity, "สแกนบาร์โค้ดสินค้า");
    }

    static void launch(Activity activity, String prompt) {
        IntentIntegrator scan = new IntentIntegrator(activity);
        scan.setPrompt(prompt);
        scan.setBeepEnabled(false);
        scan.setOrientationLocked(true);
        scan.setDesiredBarcodeFormats(IntentIntegrator.ONE_D_CODE_TYPES);
        try {
            scan.initiateScan();
        } catch (RuntimeException e) {
            new AlertDialog.Builder(activity)
                    .setTitle("เปิดกล้องสแกนไม่ได้")
                    .setMessage("ระบบเปิดหน้าสแกนไม่สำเร็จ กรุณาตรวจสอบสิทธิ์กล้องและติดตั้งแอปใหม่หากยังเกิดปัญหา")
                    .setPositiveButton("ตกลง", null)
                    .show();
        }
    }

    static IntentResult parseResult(int requestCode, int resultCode, Intent data) {
        return IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
    }
}
