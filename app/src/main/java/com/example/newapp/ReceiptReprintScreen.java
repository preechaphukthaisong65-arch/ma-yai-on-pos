package com.example.newapp;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.print.PrintJob;
import android.print.PrintManager;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.Locale;

public class ReceiptReprintScreen extends Activity {
    private StockSalesRepository repo;
    private LinearLayout root;
    private String focusId;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        repo = new StockSalesRepository(this);
        focusId = getIntent().getStringExtra("billId");
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (repo != null) {
            refreshPrintStatuses();
            render();
        }
    }

    private void render() {
        root = POSUi.page(this, "ใบเสร็จ / พิมพ์ซ้ำ");
        List<JSONObject> rows = repo.records("receipt");
        boolean found = false;
        for (JSONObject r : rows) {
            JSONObject p = r.optJSONObject("payload");
            if (p == null) continue;
            String bill = p.optString("billId", "");
            String receiptId = r.optString("recordId");
            if (focusId != null && !focusId.isEmpty()
                    && !focusId.equals(bill) && !focusId.equals(receiptId)) continue;
            found = true;
            double total = p.optDouble("total", 0);
            root.addView(POSUi.text(this, p.optString("receiptType", "ใบเสร็จ")
                    + "\nเลขที่: " + p.optString("receiptId", receiptId)
                    + "\nยอด: " + String.format(Locale.US, "%.2f", total) + " บาท"
                    + "\nสถานะพิมพ์: " + statusLabel(p.optString("printStatus", "pending")), 15));
            POSUi.button(this, root, "พิมพ์ใบเสร็จ / พิมพ์ซ้ำ", () -> printReceipt(p, r));
        }
        if (!found) root.addView(POSUi.text(this, "ไม่พบใบเสร็จที่บันทึกไว้", 16));
        POSUi.button(this, root, "กลับ", this::finish);
    }

    private String statusLabel(String status) {
        switch (status) {
            case "queued": return "รอพิมพ์";
            case "printing": return "กำลังพิมพ์";
            case "completed": return "พิมพ์สำเร็จ (ระบบพิมพ์ยืนยันแล้ว)";
            case "failed": return "พิมพ์ไม่สำเร็จ — สามารถพิมพ์ซ้ำ";
            case "print_dialog_opened": return "เปิดหน้าพิมพ์แล้ว (ยังไม่ยืนยันผล)";
            default: return "รอพิมพ์";
        }
    }

    private void refreshPrintStatuses() {
        try {
            PrintManager manager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
            if (manager == null) return;
            List<PrintJob> jobs = manager.getPrintJobs();
            for (JSONObject r : repo.records("receipt")) {
                JSONObject p = r.optJSONObject("payload");
                if (p == null || !p.has("printJobId")) continue;
                String expectedId = p.optString("printJobId", "");
                for (PrintJob job : jobs) {
                    if (job == null || job.getId() == null
                            || !expectedId.equals(job.getId().toString())) continue;
                    String status = job.isFailed() || job.isCancelled() ? "failed"
                            : job.isCompleted() ? "completed"
                            : job.isQueued() || job.isBlocked() ? "queued" : "printing";
                    if (!status.equals(p.optString("printStatus", ""))) {
                        JSONObject updated = new JSONObject(p.toString());
                        updated.put("printStatus", status);
                        if (job.isFailed()) updated.put("printError", "Android print job failed");
                        repo.saveRecord("receipt", r.optString("recordId"), updated);
                    }
                    break;
                }
            }
        } catch (Exception ignored) {
            // Do not infer print success if Android does not provide a job result.
        }
    }

    private void printReceipt(JSONObject p, JSONObject record) {
        try {
            String html = html(p);
            WebView web = new WebView(this);
            web.getSettings().setJavaScriptEnabled(false);
            web.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null);
            PrintManager manager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
            String jobName = "ใบเสร็จ-" + p.optString("receiptId", "POS");
            PrintJob job = manager.print(jobName, web.createPrintDocumentAdapter(jobName),
                    new android.print.PrintAttributes.Builder().build());
            JSONObject updated = new JSONObject(p.toString());
            updated.put("printStatus", "print_dialog_opened");
            updated.put("lastPrintRequestedAt", System.currentTimeMillis());
            if (job != null && job.getId() != null) {
                updated.put("printJobId", job.getId().flattenToString());
                updated.put("printStatus", "queued");
            }
            repo.saveRecord("receipt", record.optString("recordId"), updated);
            Toast.makeText(this, "เปิดหน้าพิมพ์แล้ว ระบบจะบันทึกสถานะเมื่อ Android ส่งผลกลับมา หากพิมพ์ไม่สำเร็จให้พิมพ์ซ้ำจากใบเสร็จเดิม", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "เปิดหน้าพิมพ์ไม่สำเร็จ ใบเสร็จเดิมยังถูกเก็บไว้และสามารถพิมพ์ซ้ำได้", Toast.LENGTH_LONG).show();
        }
    }

    private String html(JSONObject p) {
        StringBuilder s = new StringBuilder("<html><meta charset='utf-8'><body style='font-family:sans-serif;width:280px'>");
        s.append("<h3>ร้านแม่ใหญ่อ่อน</h3><p>").append(escape(p.optString("receiptType", "ใบเสร็จ"))).append("</p>");
        s.append("<p>เลขที่ ").append(escape(p.optString("receiptId", ""))).append("</p>");
        JSONArray items = p.optJSONArray("items");
        if (items != null) for (int i = 0; i < items.length(); i++) {
            JSONObject x = items.optJSONObject(i);
            if (x == null) continue;
            s.append("<p>").append(escape(x.optString("name", "สินค้า"))).append(" × ").append(x.optDouble("quantity", 0))
                    .append("<br>").append(String.format(Locale.US, "%.2f", x.optDouble("lineTotal", 0))).append(" บาท</p>");
        }
        if (p.has("customerName")) s.append("<p>ลูกค้า: ").append(escape(p.optString("customerName", ""))).append("</p>");
        if (p.has("subtotal")) s.append("<p>ยอดสินค้า: ").append(String.format(Locale.US, "%.2f", p.optDouble("subtotal", 0))).append(" บาท</p>");
        if (p.optDouble("serviceFee", 0) > 0) s.append("<p>ค่าบริการ: ").append(String.format(Locale.US, "%.2f", p.optDouble("serviceFee", 0))).append(" บาท</p>");
        JSONArray billIds = p.optJSONArray("billIds");
        if (billIds != null) {
            s.append("<p>ชำระบิลเดิม:</p>");
            for (int i = 0; i < billIds.length(); i++) s.append("<p>").append(escape(billIds.optString(i, ""))).append("</p>");
        }
        s.append("<hr><h3>รวม ").append(String.format(Locale.US, "%.2f", p.optDouble("total", 0))).append(" บาท</h3>");
        if (p.optDouble("cashReceived", 0) > 0) s.append("<p>รับเงิน: ").append(String.format(Locale.US, "%.2f", p.optDouble("cashReceived", 0))).append(" บาท</p>");
        if (p.optDouble("cashReceived", 0) > 0) s.append("<p>เงินทอน: ").append(String.format(Locale.US, "%.2f", p.optDouble("change", 0))).append(" บาท</p>");
        s.append("<p>").append(escape(p.optString("paymentStatus", ""))).append("</p></body></html>");
        return s.toString();
    }

    private String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
