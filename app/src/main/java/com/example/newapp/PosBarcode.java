package com.example.newapp;

import android.app.Activity;
import android.content.Intent;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

/** Shared barcode scan/result handling used by all screens that scan products or receipts. */
final class PosBarcode {
    private PosBarcode() {}

    static void launch(Activity activity, String prompt) {
        PosUi.scan(activity, prompt);
    }

    static IntentResult parseResult(int requestCode, int resultCode, Intent data) {
        return IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
    }

    static String trimmed(String barcode) {
        return barcode == null ? null : barcode.trim();
    }
}
