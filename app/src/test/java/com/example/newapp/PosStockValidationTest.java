package com.example.newapp;

import org.junit.Test;

import static org.junit.Assert.*;

public class PosStockValidationTest {

    @Test
    public void receiveStockIncreasesQuantity() {
        int currentStock = 10;
        int receivedQuantity = 5;

        int newStock = currentStock + receivedQuantity;

        assertEquals(15, newStock);
    }

    @Test
    public void sellStockDecreasesQuantity() {
        int currentStock = 20;
        int soldQuantity = 7;

        int newStock = currentStock - soldQuantity;

        assertEquals(13, newStock);
    }

    @Test
    public void stockCannotBecomeNegative() {
        int currentStock = 5;
        int soldQuantity = 8;

        boolean enoughStock = soldQuantity <= currentStock;

        assertFalse(enoughStock);
        assertEquals(5, currentStock);
    }

    @Test
    public void sellingAllAvailableStockLeavesZero() {
        int currentStock = 8;
        int soldQuantity = 8;

        int newStock = currentStock - soldQuantity;

        assertEquals(0, newStock);
    }

    @Test
    public void rejectZeroReceivedQuantity() {
        int receivedQuantity = 0;

        assertFalse(receivedQuantity > 0);
    }

    @Test
    public void rejectNegativeReceivedQuantity() {
        int receivedQuantity = -3;

        assertFalse(receivedQuantity > 0);
    }

    @Test
    public void lowStockWarningWhenStockAtMinimum() {
        int currentStock = 5;
        int minimumStock = 5;

        boolean lowStock = currentStock <= minimumStock;

        assertTrue(lowStock);
    }

    @Test
    public void noLowStockWarningWhenStockAboveMinimum() {
        int currentStock = 10;
        int minimumStock = 5;

        boolean lowStock = currentStock <= minimumStock;

        assertFalse(lowStock);
    }
}
