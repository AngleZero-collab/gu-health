package com.gucare.health;

public class HealthRecord {
    static final String REPORT_SUMMARY = "report_summary";
    public long id, profileId;
    public String kind = "note", key = "", title = "", date = "", note = "";
    public boolean flag;
}
