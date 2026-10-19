
package com.example.newapp;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.IOException;
import java.text.DecimalFormat;

public class ReceiptPrinter {

    private final LocalDatabase database;
    private final PrinterService printer;
    private final DecimalFormat money = new DecimalFormat("#,##0.00");

    public ReceiptPrinter(Context context) {
        database = LocalDatabase.getInstance(context);
        printer = PrinterService.getInstance(context);
    }

    public static class PrintResult {
        public final boolean success;
        public final String message;

        public PrintResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    public PrintResult printSale(long saleId) {
        SQLiteDatabase db = database.getReadableDatabase();
        StringBuilder receipt = new StringBuilder();

        try (Cursor sale = db.rawQuery(
                "SELECT bill_number, payment_type, status, subtotal, " +
                "service_fee, total, cash_received, change_due, " +
                "created_at, paid_at, receipt_text " +
                "FROM sales WHERE id = ? LIMIT 1",
                new String[]{String.valueOf(saleId)})) {

            if (!sale.moveToFirst()) {
                return new PrintResult(false, "ไม่พบบิลขาย");
            }

            String billNo = sale.getString(0);
            String paymentType = sale.getString(1);
            String status = sale.getString(2);
            double subtotal = sale.getDouble(3);
            double fee = sale.getDouble(4);
            double total = sale.getDouble(5);
            double cash = sale.getDouble(6);
            double change = sale.getDouble(7);
            String createdAt = sale.getString(8);
            String paidAt = sale.getString(9);
            String savedText = sale.getString(10);

            receipt.append(center("ร้านแม่ใหญ่อ่อน")).append("\n");
            receipt.append(center("ใบเสร็จรับเงิน")).append("\n");
            receipt.append("--------------------------------\n");
            receipt.append("เลขที่บิล: ").append(safe(billNo)).append("\n");
            receipt.append("วันที่: ").append(safe(createdAt)).append("\n");
            receipt.append("ประเภท: ")
                    .append("CREDIT".equalsIgnoreCase(paymentType)
                            ? "ขายเชื่อ" : "ขายเงินสด")
                    .append("\n");

            receipt.append("--------------------------------\n");

            try (Cursor items = db.rawQuery(
                    "SELECT product_name, quantity, unit, unit_price, line_total " +
                    "FROM sale_items WHERE sale_id = ? ORDER BY rowid ASC",
                    new String[]{String.valueOf(saleId)})) {

                while (items.moveToNext()) {
                    receipt.append(safe(items.getString(0))).append("\n");
                    receipt.append("  ")
                            .append(money.format(items.getDouble(1)))
                            .append(" ")
                            .append(safe(items.getString(2)))
                            .append(" x ")
                            .append(money.format(items.getDouble(3)))
                            .append(" = ")
                            .append(money.format(items.getDouble(4)))
                            .append("\n");
                }
            }

            receipt.append("--------------------------------\n");
            receipt.append("รวมสินค้า: ")
                    .append(money.format(subtotal)).append("\n");

            if (fee > 0) {
                receipt.append("ค่าธรรมเนียมเครดิต: ")
                        .append(money.format(fee)).append("\n");
            }

            receipt.append("ยอดสุทธิ: ")
                    .append(money.format(total)).append(" บาท\n");

            if ("CREDIT".equalsIgnoreCase(paymentType)) {
                receipt.append("สถานะ: ")
                        .append("PENDING".equalsIgnoreCase(status)
                                ? "ค้างชำระ" : "ชำระแล้ว")
                        .append("\n");

                if (paidAt != null && !paidAt.trim().isEmpty()) {
                    receipt.append("วันที่ชำระ: ")
                            .append(paidAt).append("\n");
                }
            } else {
                receipt.append("รับเงิน: ")
                        .append(money.format(cash)).append("\n");
                receipt.append("เงินทอน: ")
                        .append(money.format(change)).append("\n");
            }

            receipt.append("--------------------------------\n");
            receipt.append(center("ขอบคุณที่ใช้บริการ")).append("\n\n\n");

            String output = savedText == null || savedText.trim().isEmpty()
                    ? receipt.toString()
                    : savedText;

            return sendAndRecordSalePrint(db, saleId, output);

        } catch (Exception e) {
            return new PrintResult(
                    false,
                    "เตรียมใบเสร็จไม่สำเร็จ: " + safe(e.getMessage())
            );
        }
    }

    private PrintResult sendAndRecordSalePrint(
            SQLiteDatabase db,
            long saleId,
            String text
    ) {
        if (!printer.isConnected()) {
            updatePrintStatus(db, saleId, "FAILED");
            return new PrintResult(
                    false,
                    "ยังไม่ได้เชื่อมต่อเครื่องพิมพ์ ข้อมูลขายยังคงบันทึกอยู่"
            );
        }

        try {
            printer.initialize();
            printer.printText(text);
            printer.feed(4);

            updatePrintStatus(db, saleId, "SENT");

            return new PrintResult(
                    true,
                    "ส่งใบเสร็จไปยังเครื่องพิมพ์แล้ว"
            );

        } catch (IOException e) {
            updatePrintStatus(db, saleId, "FAILED");

            return new PrintResult(
                    false,
                    "พิมพ์ไม่สำเร็จ สามารถลองพิมพ์ซ้ำได้: "
                            + safe(e.getMessage())
            );
        }
    }

    public PrintResult printCreditPayment(String paymentReceiptNumber) {
        SQLiteDatabase db = database.getReadableDatabase();

        String sql =
                "SELECT cp.payment_number, s.bill_number, " +
                "cp.amount, cp.paid_at " +
                "FROM credit_payments cp " +
                "INNER JOIN sales s ON s.id = cp.sale_id " +
                "WHERE cp.payment_number = ? LIMIT 1";

        try (Cursor cursor = db.rawQuery(
                sql,
                new String[]{paymentReceiptNumber})) {

            if (!cursor.moveToFirst()) {
                return new PrintResult(false, "ไม่พบรายการรับชำระ");
            }

            String receiptNo = cursor.getString(0);
            String originalBill = cursor.getString(1);
            double amount = cursor.getDouble(2);
            String paidAt = cursor.getString(3);

            StringBuilder receipt = new StringBuilder();

            receipt.append(center("ร้านแม่ใหญ่อ่อน")).append("\n");
            receipt.append(center("ใบเสร็จรับชำระหนี้")).append("\n");
            receipt.append("--------------------------------\n");
            receipt.append("เลขที่ใบเสร็จ: ")
                    .append(safe(receiptNo)).append("\n");
            receipt.append("บิลต้นฉบับ: ")
                    .append(safe(originalBill)).append("\n");
            receipt.append("วันที่รับชำระ: ")
                    .append(safe(paidAt)).append("\n");
            receipt.append("รับเงินสด: ")
                    .append(money.format(amount))
                    .append(" บาท\n");
            receipt.append("--------------------------------\n");
            receipt.append(center("รับชำระเต็มบิลแล้ว")).append("\n");
            receipt.append(center("ขอบคุณที่ใช้บริการ")).append("\n\n\n");

            if (!printer.isConnected()) {
                return new PrintResult(
                        false,
                        "ยังไม่ได้เชื่อมต่อเครื่องพิมพ์ รายการรับชำระถูกบันทึกแล้ว"
                );
            }

            printer.initialize();
            printer.printText(receipt.toString());
            printer.feed(4);

            return new PrintResult(
                    true,
                    "ส่งใบเสร็จรับชำระไปพิมพ์แล้ว"
            );

        } catch (Exception e) {
            return new PrintResult(
                    false,
                    "พิมพ์ใบเสร็จรับชำระไม่สำเร็จ: "
                            + safe(e.getMessage())
            );
        }
    }

    private void updatePrintStatus(
            SQLiteDatabase db,
            long saleId,
            String status
    ) {
        try {
            db.execSQL(
                    "UPDATE sales SET print_status = ? WHERE id = ?",
                    new Object[]{status, saleId}
            );
        } catch (Exception ignored) {
            // สถานะการพิมพ์ไม่ควรทำให้ข้อมูลขายเสียหาย
        }
    }

    private String center(String text) {
        return text == null ? "" : text;
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }
}
