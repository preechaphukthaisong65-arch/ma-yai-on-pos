package com.example.newapp.printer;

import java.util.Locale;

public class ReceiptFormatter {

    /**
     * สร้างข้อความ HTML สำหรับพิมพ์ใบเสร็จขนาด 80 มม.
     */
    public static String formatReceiptHtml(String receiptNumber, String soldAt, double total, String paymentMethod) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html><html><head>");
        html.append("<meta charset=\"UTF-8\">");
        html.append("<style>");
        html.append("@page{size:auto;margin:4mm;}");
        html.append("body{font-family:sans-serif;color:#111;margin:0;padding:0;font-size:12px;}");
        html.append(".receipt{width:100%;max-width:80mm;margin:0 auto;}");
        html.append("h2{text-align:center;font-size:18px;margin:0 0 4px;}");
        html.append(".center{text-align:center;}");
        html.append(".line{border-top:1px dashed #555;margin:8px 0;}");
        html.append("table{width:100%;border-collapse:collapse;}");
        html.append("td{padding:3px 0;vertical-align:top;}");
        html.append(".right{text-align:right;}");
        html.append(".total{font-size:15px;font-weight:bold;}");
        html.append("</style></head><body>");

        html.append("<div class=\"receipt\">");
        html.append("<h2>ร้านแม่ใหญ่อ่อน</h2>");
        html.append("<div class=\"center\">ใบเสร็จรับเงิน</div>");
        html.append("<div class=\"line\"></div>");

        html.append("<div>เลขที่: ").append(receiptNumber != null ? receiptNumber : "-").append("</div>");
        html.append("<div>วันที่: ").append(soldAt != null ? soldAt : "-").append("</div>");
        html.append("<div>การชำระ: ").append("credit".equalsIgnoreCase(paymentMethod) ? "เครดิต" : "เงินสด").append("</div>");

        html.append("<div class=\"line\"></div>");
        html.append("<table>");
        html.append("<tr class=\"total\"><td>ยอดสุทธิ</td><td class=\"right\">")
                .append(String.format(Locale.US, "%,.2f", total))
                .append(" บาท</td></tr>");
        html.append("</table>");

        html.append("<div class=\"line\"></div>");
        html.append("<div class=\"center\">ขอบคุณที่อุดหนุน</div>");
        html.append("<div class=\"center\" style=\"font-size:10px;color:#555;\">ร้านแม่ใหญ่อ่อน POS</div>");
        html.append("</div></body></html>");

        return html.toString();
    }
}
