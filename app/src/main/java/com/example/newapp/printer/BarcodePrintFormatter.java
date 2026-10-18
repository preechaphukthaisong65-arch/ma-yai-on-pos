package com.example.newapp.printer;

public class BarcodePrintFormatter {

    /**
     * เตรียมข้อมูล HTML สำหรับพิมพ์บาร์โค้ดสินค้าผ่านเครื่องพิมพ์ความร้อน
     */
    public static String formatBarcodeReceiptHtml(String productName, String barcode, double price) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html><html><head>");
        html.append("<meta charset=\"UTF-8\">");
        html.append("<style>");
        html.append("@page{size:auto;margin:2mm;}");
        html.append("body{font-family:sans-serif;color:#111;margin:0;padding:0;font-size:12px;text-align:center;}");
        html.append(".label{width:100%;max-width:80mm;margin:0 auto;padding:5px;}");
        html.append(".name{font-size:14px;font-weight:bold;margin-bottom:4px;}");
        html.append(".price{font-size:13px;margin-bottom:6px;}");
        html.append(".barcode{font-family:monospace;font-size:16px;font-weight:bold;letter-spacing:2px;}");
        html.append("</style></head><body>");

        html.append("<div class=\"label\">");
        html.append("<div class=\"name\">").append(productName != null ? productName : "-").append("</div>");
        html.append("<div class=\"price\">ราคา: ").append(String.format(java.util.Locale.US, "%,.2f", price)).append(" บาท</div>");
        html.append("<div class=\"barcode\">*").append(barcode != null ? barcode : "").append("*</div>");
        html.append("<div style=\"font-size:11px;margin-top:2px;\">").append(barcode != null ? barcode : "").append("</div>");
        html.append("</div></body></html>");

        return html.toString();
    }
}
