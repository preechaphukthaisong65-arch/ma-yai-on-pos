package com.example.newapp;

/** In-memory actor context for append-only audit events; reset when returning to the public home screen. */
public final class PosSession {
    private static volatile String actorLabel = "ยังไม่ระบุผู้ใช้";
    private PosSession() {}
    public static void setOwnerAuthenticated() { actorLabel = "เจ้าของร้าน (ยืนยัน PIN)"; }
    public static String actorLabel() { return actorLabel; }
    public static void clearActor() { actorLabel = "ยังไม่ระบุผู้ใช้"; }
}
