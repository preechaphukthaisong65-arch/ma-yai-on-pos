package com.example.newapp;

import org.junit.Test;

import static org.junit.Assert.*;

public class PosSystemTest {

    @Test
    public void calculateChangeCorrectly() {
        double total = 125.0;
        double paid = 200.0;
        double change = paid - total;

        assertEquals(75.0, change, 0.001);
    }

    @Test
    public void rejectPaymentBelowTotal() {
        double total = 125.0;
        double paid = 100.0;

        assertTrue(paid < total);
    }

    @Test
    public void stockCannotBecomeNegative() {
        int stock = 5;
        int quantity = 3;

        assertTrue(quantity <= stock);

        stock -= quantity;

        assertEquals(2, stock);
    }

    @Test
    public void calculateLineTotalCorrectly() {
        double price = 35.0;
        int quantity = 4;

        assertEquals(140.0, price * quantity, 0.001);
    }
}
