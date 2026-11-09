package com.gucare.health;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Private on-device database. No network, automatic cloud backup, or invented encryption claims. */
public final class HealthStore extends SQLiteOpenHelper {
    public HealthStore(Context c) { super(c,"guhealth.db",null,1); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE profiles(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,birth TEXT NOT NULL,sex TEXT NOT NULL,city TEXT NOT NULL,indigenous INTEGER,polio INTEGER,familyColon INTEGER,familyLung INTEGER,smokes INTEGER,betel INTEGER,packYears INTEGER,quitYears INTEGER)");
        db.execSQL("CREATE TABLE records(id INTEGER PRIMARY KEY AUTOINCREMENT,profileId INTEGER NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,kind TEXT,key TEXT,title TEXT,date TEXT,note TEXT,flag INTEGER)");
        db.execSQL("CREATE TABLE appointments(id INTEGER PRIMARY KEY AUTOINCREMENT,profileId INTEGER NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,key TEXT,title TEXT,at INTEGER,place TEXT,notified INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX record_profile_date ON records(profileId,date)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) { /* No destructive migration. */ }
    public long save(Profile p) {
        ContentValues v=new ContentValues();
        v.put("name",p.name);v.put("birth",p.birth);v.put("sex",p.sex);v.put("city",p.city);
        v.put("indigenous",p.indigenous);v.put("polio",p.polio);v.put("familyColon",p.familyColon);v.put("familyLung",p.familyLung);
        v.put("smokes",p.smokes);v.put("betel",p.betel);v.put("packYears",p.packYears);v.put("quitYears",p.quitYears);
        if (p.id==0) p.id=getWritableDatabase().insertOrThrow("profiles",null,v);
        else getWritableDatabase().update("profiles",v,"id=?",new String[]{""+p.id});
        return p.id;
    }
    public List<Profile> profiles() {
        List<Profile> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("profiles",null,null,null,null,null,"id ASC")) {
            while(c.moveToNext()) { Profile p=new Profile(); p.id=lng(c,"id");p.name=str(c,"name");p.birth=str(c,"birth");p.sex=str(c,"sex");p.city=str(c,"city");p.indigenous=bool(c,"indigenous");p.polio=bool(c,"polio");p.familyColon=bool(c,"familyColon");p.familyLung=bool(c,"familyLung");p.smokes=bool(c,"smokes");p.betel=bool(c,"betel");p.packYears=(int)lng(c,"packYears");p.quitYears=(int)lng(c,"quitYears");out.add(p); }
        } return out;
    }
    public Profile profile(long id) { for(Profile p:profiles()) if(p.id==id) return p; return null; }
    public void deleteProfile(long id) { getWritableDatabase().delete("profiles","id=?",new String[]{""+id}); }
    public long add(HealthRecord r) {
        ContentValues v=new ContentValues();v.put("profileId",r.profileId);v.put("kind",r.kind);v.put("key",r.key);v.put("title",r.title);v.put("date",r.date);v.put("note",r.note);v.put("flag",r.flag);
        r.id=getWritableDatabase().insertOrThrow("records",null,v);return r.id;
    }
    public List<HealthRecord> records(long profileId) {
        List<HealthRecord> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("records",null,"profileId=?",new String[]{""+profileId},null,null,"date DESC, id DESC")) {
            while(c.moveToNext()){HealthRecord r=new HealthRecord();r.id=lng(c,"id");r.profileId=lng(c,"profileId");r.kind=str(c,"kind");r.key=str(c,"key");r.title=str(c,"title");r.date=str(c,"date");r.note=str(c,"note");r.flag=bool(c,"flag");out.add(r);}
        }return out;
    }
    public void deleteRecord(long id) { getWritableDatabase().delete("records","id=?",new String[]{""+id}); }
    public LocalDate lastScreening(long profileId,String key) {
        for(HealthRecord r:records(profileId)) if(r.kind.equals("screening") && r.key.equals(key)) return LocalDate.parse(r.date);
        return null;
    }
    public long add(Appointment a) {
        ContentValues v=new ContentValues();v.put("profileId",a.profileId);v.put("key",a.key);v.put("title",a.title);v.put("at",a.at);v.put("place",a.place);v.put("notified",a.notified);
        a.id=getWritableDatabase().insertOrThrow("appointments",null,v);return a.id;
    }
    public List<Appointment> appointments(long profileId) {
        List<Appointment> out=new ArrayList<>();
        String selection=profileId==0?null:"profileId=?";
        try(Cursor c=getReadableDatabase().query("appointments",null,selection,profileId==0?null:new String[]{""+profileId},null,null,"at ASC")){
            while(c.moveToNext()){Appointment a=new Appointment();a.id=lng(c,"id");a.profileId=lng(c,"profileId");a.key=str(c,"key");a.title=str(c,"title");a.at=lng(c,"at");a.place=str(c,"place");a.notified=bool(c,"notified");out.add(a);}
        }return out;
    }
    public Appointment appointment(long id) { for(Appointment a:appointments(0)) if(a.id==id) return a;return null; }
    public void notified(long id) { ContentValues v=new ContentValues();v.put("notified",true);getWritableDatabase().update("appointments",v,"id=?",new String[]{""+id}); }
    public void deleteAppointment(long id) { getWritableDatabase().delete("appointments","id=?",new String[]{""+id}); }
    public Profile demo() {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            Profile p=new Profile();p.name="林媽媽（示範）";p.birth="1954-03-15";p.city="台北市";save(p);
            HealthRecord r=new HealthRecord();r.profileId=p.id;r.kind="screening";r.key="dental";r.title="牙科定期追蹤";r.date=LocalDate.now().minusDays(8).toString();r.note="示範資料：例行口腔追蹤。";add(r);
            HealthRecord r2=new HealthRecord();r2.profileId=p.id;r2.kind="note";r2.title="陪媽媽一起散步";r2.date=LocalDate.now().minusDays(2).toString();r2.note="示範資料：今天到公園走了 20 分鐘。";add(r2);
            db.setTransactionSuccessful();return p;
        } finally { db.endTransaction(); }
    }
    public String summary(Profile p) {
        StringBuilder s=new StringBuilder("顧健康｜個人健康摘要\n");
        s.append(p.name).append("・").append(p.age(LocalDate.now())).append(" 歲\n產生日期：").append(LocalDate.now()).append("\n\n");
        for(HealthRecord r:records(p.id)){
            if(!HealthRecord.REPORT_SUMMARY.equals(r.kind))s.append(r.date).append("｜").append(r.title).append("\n").append(r.note).append("\n\n");
        }
        s.append("自行登錄資料，非醫療院所正式報告；居家觀察不構成診斷。");return s.toString();
    }
    public String exportJson() throws Exception {
        JSONObject root=new JSONObject();root.put("format","guhealth-backup");root.put("version",1);root.put("exportedAt",LocalDate.now().toString());
        JSONArray people=new JSONArray();
        for(Profile p:profiles()) {
            JSONObject person=new JSONObject();person.put("id",p.id);person.put("name",p.name);person.put("birth",p.birth);person.put("sex",p.sex);person.put("city",p.city);
            person.put("indigenous",p.indigenous);person.put("polio",p.polio);person.put("familyColon",p.familyColon);person.put("familyLung",p.familyLung);person.put("smokes",p.smokes);person.put("betel",p.betel);person.put("packYears",p.packYears);person.put("quitYears",p.quitYears);
            JSONArray records=new JSONArray();for(HealthRecord r:records(p.id)){if(HealthRecord.REPORT_SUMMARY.equals(r.kind))continue;JSONObject j=new JSONObject();j.put("kind",r.kind);j.put("key",r.key);j.put("title",r.title);j.put("date",r.date);j.put("note",r.note);j.put("flag",r.flag);records.put(j);}person.put("records",records);
            JSONArray appointments=new JSONArray();for(Appointment a:appointments(p.id)){JSONObject j=new JSONObject();j.put("key",a.key);j.put("title",a.title);j.put("at",a.at);j.put("place",a.place);j.put("notified",a.notified);appointments.put(j);}person.put("appointments",appointments);people.put(person);
        }
        root.put("profiles",people);return root.toString(2);
    }
    private static String str(Cursor c,String key){return c.getString(c.getColumnIndexOrThrow(key));}
    private static long lng(Cursor c,String key){return c.getLong(c.getColumnIndexOrThrow(key));}
    private static boolean bool(Cursor c,String key){return lng(c,key)==1;}
}
