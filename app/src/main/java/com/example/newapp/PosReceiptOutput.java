package com.example.newapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public final class PosReceiptOutput {
    private PosReceiptOutput() {}

    public static void share(Activity activity, String receipt) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "ใบเสร็จร้านแม่ใหญ่อ่อน POS");
        send.putExtra(Intent.EXTRA_TEXT, receipt);
        activity.startActivity(Intent.createChooser(send, "แชร์ใบเสร็จ"));
    }

    public static void print(Activity activity, String jobName, String receipt) {
        PrintManager manager = (PrintManager) activity.getSystemService(Context.PRINT_SERVICE);
        if (manager == null) throw new IllegalStateException("อุปกรณ์นี้ไม่รองรับระบบพิมพ์");
        WebView web = new WebView(activity);
        web.getSettings().setJavaScriptEnabled(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                view.setWebViewClient(null);
                manager.print(jobName, view.createPrintDocumentAdapter(jobName),
                        new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .setResolution(new PrintAttributes.Resolution("pos", "POS", 300, 300))
                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build());
            }
        });
        web.loadDataWithBaseURL(null, toHtml(receipt), "text/html", "UTF-8", null);
    }

    private static String toHtml(String text) {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;")
                .replace("\n", "<br/>");
        return "<html><head><meta charset=\"utf-8\"><style>body{font-family:sans-serif;font-size:14pt;margin:12mm;} .receipt{white-space:normal;}</style></head><body><div class=\"receipt\">" + escaped + "</div></body></html>";
    }
}
