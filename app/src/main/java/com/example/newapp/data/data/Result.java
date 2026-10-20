package com.example.newapp.data;

/** ผลลัพธ์ของการทำรายการ พร้อมข้อความภาษาไทยสำหรับแสดงผู้ใช้ */
public final class Result<T> {

    public final boolean success;
    public final String message;
    public final T value;

    private Result(boolean success, String message, T value) {
        this.success = success;
        this.message = message;
        this.value = value;
    }

    public static <T> Result<T> ok(T value) {
        return new Result<>(true, "", value);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(false, message, null);
    }
}
