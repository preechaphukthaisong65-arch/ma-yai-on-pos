
package com.example.newapp;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ReceiptFormatter {

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00");

    private ReceiptFormatter() {
        // Utility class
    }

    public static class ReceiptLine {
        public final String name;
        public final int quantity;
        public final String unit;
        public final double unitPrice;
        public final double lineTotal;

        public ReceiptLine(
                String name,
                int quantity,
                String unit,
                double unitPrice,
                double lineTotal
        ) {
            this.name = name == null ? "" : name;
            this.quantity = quantity;
            this.unit = unit == null ? "" : unit;
            this.unitPrice = unitPrice;
            this.lineTotal = lineTotal;
        }
    }

    public static class ReceiptData {
        public String storeName = "ร้านแม่ใหญ่อ่อน";
        public String receiptNumber = "";
        public String receiptType = "ใบเสร็จรับเงิน";
        public String dateTime = "";
        public String paymentType = "CASH";
        public String customerName = "";

        public List<ReceiptLine> lines;

        public double subtotal;
        public double serviceFee;
        public double total;
        public double cashReceived;
        public double changeAmount;
        public double amountPaid;
        public double outstanding;
    }

    public static String formatSale(ReceiptData data) {
        validate(data);

        StringBuilder out = new StringBuilder();

        appendCenter(out, data.storeName);
        appendCenter(out, data.receiptType);
        out.append("--------------------------------\n");
        out.append("เลขที่: ").append(data.receiptNumber).append('\n');
        out.append("วันที่: ").append(resolveDate(data.dateTime)).append('\n');

        if (data.customerName != null
                && !data.customerName.trim().isEmpty()) {
            out.append("ลูกค้า: ")
                    .append(data.customerName.trim())
                    .append('\n');
        }

        out.append("--------------------------------\n");

        for (ReceiptLine line : data.lines) {
            out.append(line.name).append('\n');

            out.append(" ")
                    .append(line.quantity)
                    .append(' ')
                    .append(line.unit)
                    .append(" x ")
                    .append(money(line.unitPrice))
                    .append(" = ")
                    .append(money(line.lineTotal))
                    .append('\n');
        }

        out.append("--------------------------------\n");
        out.append(row("รวมสินค้า", data.subtotal));

        if (data.serviceFee > 0) {
            out.append(row("ค่าบริการขายเชื่อ", data.serviceFee));
        }

        out.append(row("ยอดสุทธิ", data.total));

        if ("CASH".equalsIgnoreCase(data.paymentType)) {
            out.append(row("รับเงิน", data.cashReceived));
            out.append(row("เงินทอน", data.changeAmount));
            out.append("\nชำระแล้ว\n");
        } else if ("CREDIT".equalsIgnoreCase(data.paymentType)) {
            out.append("ประเภท: ขายเชื่อ\n");
            out.append(row("ยอดค้างชำระ", data.outstanding));
            out.append("\nยังไม่ชำระ\n");
        } else if ("CREDIT_PAYMENT".equalsIgnoreCase(data.paymentType)) {
            out.append(row("รับชำระครั้งนี้", data.amountPaid));
            out.append(row("ยอดคงเหลือ", data.outstanding));
        }

        out.append("--------------------------------\n");
        appendCenter(out, "ขอบคุณที่ใช้บริการ");

        return out.toString();
    }

    public static String formatCreditPayment(
            String storeName,
            String receiptNumber,
            String dateTime,
            String customerName,
            String originalBillNumber,
            double amountPaid,
            double remainingDebt
    ) {
        StringBuilder out = new StringBuilder();

        appendCenter(
                out,
                storeName == null ? "ร้านแม่ใหญ่อ่อน" : storeName
        );
        appendCenter(out, "ใบเสร็จรับชำระหนี้");
        out.append("--------------------------------\n");
        out.append("เลขที่ใบเสร็จ: ")
                .append(safe(receiptNumber)).append('\n');
        out.append("วันที่: ")
                .append(resolveDate(dateTime)).append('\n');
        out.append("ลูกค้า: ")
                .append(safe(customerName)).append('\n');
        out.append("บิลต้นฉบับ: ")
                .append(safe(originalBillNumber)).append('\n');
        out.append("--------------------------------\n");
        out.append(row("รับชำระ", amountPaid));
        out.append(row("หนี้คงเหลือ", remainingDebt));
        out.append("--------------------------------\n");
        appendCenter(out, "ขอบคุณที่ใช้บริการ");

        return out.toString();
    }

    private static void validate(ReceiptData data) {
        if (data == null) {
            throw new IllegalArgumentException(
                    "ข้อมูลใบเสร็จต้องไม่เป็น null"
            );
        }

        if (data.lines == null) {
            throw new IllegalArgumentException(
                    "รายการสินค้าในใบเสร็จต้องไม่เป็น null"
            );
        }

        if (data.total < 0
                || data.subtotal < 0
                || data.serviceFee < 0) {
            throw new IllegalArgumentException(
                    "ยอดเงินในใบเสร็จไม่ถูกต้อง"
            );
        }
    }

    private static String row(String label, double amount) {
        return label + ": " + money(amount) + " บาท\n";
    }

    private static String money(double amount) {
        return MONEY.format(amount);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String resolveDate(String value) {
        if (value != null && !value.trim().isEmpty()) {
            return value;
        }

        return new SimpleDateFormat(
                "dd/MM/yyyy HH:mm:ss",
                Locale.getDefault()
        ).format(new Date());
    }

    private static void appendCenter(
            StringBuilder out,
            String text
    ) {
        out.append(text == null ? "" : text).append('\n');
    }
}
