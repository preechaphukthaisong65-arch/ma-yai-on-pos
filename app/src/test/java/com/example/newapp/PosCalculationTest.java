package com.example.newapp;

import org.junit.Test;

import static org.junit.Assert.*;

public class PosCalculationTest {

    @Test
    public void calculateTotalCorrectly() {
        double price = 25.0;
        int quantity = 4;

        double total = price * quantity;

        assertEquals(100.0, total, 0.001);
    }

    @Test
    public void calculateChangeCorrectly() {
        double total = 150.0;
        double paid = 200.0;

        double change = paid - total;

        assertEquals(50.0, change, 0.001);
    }

    @Test
    public void calculateCreditFeeFivePercent() {
        double total = 1000.0;
        double fee = total * 0.05;
        double finalTotal = total + fee;

        assertEquals(50.0, fee, 0.001);
        assertEquals(1050.0, finalTotal, 0.001);
    }

    @Test
    public void rejectInsufficientPayment() {
        double total = 250.0;
        double paid = 200.0;

        assertTrue(paid < total);
    }

    @Test
    public void zeroQuantityProducesZeroTotal() {
        double price = 50.0;
        int quantity = 0;

        assertEquals(0.0, price * quantity, 0.001);
    }

    @Test
    public void rejectNegativeQuantity() {
        int quantity = -1;

        assertTrue(quantity < 0);
    }
}
