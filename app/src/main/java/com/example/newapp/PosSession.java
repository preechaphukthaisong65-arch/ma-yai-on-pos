package com.example.newapp;

/** In-memory authenticated actor for append-only audit events. */
public final class PosSession {
    private static volatile String actorLabel = "ยังไม่ระบุผู้ใช้";
    private static volatile long actorId = -1L;
    private static volatile String role = "UNKNOWN";
    private PosSession() {}
    public static void setOwnerAuthenticated() { actorId = -1L; role = "OWNER"; actorLabel = "เจ้าของร้าน (ยืนยัน PIN)"; }
    public static void setStaffAuthenticated(long id, String name) { actorId = id; role = "STAFF"; actorLabel = "พนักงาน: " + name; }
    public static String actorLabel() { return actorLabel; }
    public static long actorId() { return actorId; }
    public static String role() { return role; }
    public static void clearActor() { actorId = -1L; role = "UNKNOWN"; actorLabel = "ยังไม่ระบุผู้ใช้"; }
}
