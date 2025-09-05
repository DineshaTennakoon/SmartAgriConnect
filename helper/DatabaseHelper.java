package com.smartagri.connect.helper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.smartagri.connect.model.SensorData;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "sensor_data.db";
    private static final int DATABASE_VERSION = 1;

    // Table name
    private static final String TABLE_SENSOR_DATA = "sensor_data";

    // Column names
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    private static final String COLUMN_TEMPERATURE = "temperature";
    private static final String COLUMN_HUMIDITY = "humidity";
    private static final String COLUMN_SOIL = "soil";
    private static final String COLUMN_BATTERY = "battery";
    private static final String COLUMN_ALERTS = "alerts";
    private static final String COLUMN_UPLOADED = "uploaded";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_SENSOR_DATA + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_TIMESTAMP + " INTEGER,"
                + COLUMN_TEMPERATURE + " REAL,"
                + COLUMN_HUMIDITY + " REAL,"
                + COLUMN_SOIL + " REAL,"
                + COLUMN_BATTERY + " INTEGER,"
                + COLUMN_ALERTS + " TEXT,"
                + COLUMN_UPLOADED + " INTEGER DEFAULT 0"
                + ")";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SENSOR_DATA);
        onCreate(db);
    }

    public long insertSensorData(long timestamp, float temperature, float humidity,
                                 float soil, int battery, String alerts) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TIMESTAMP, timestamp);
        values.put(COLUMN_TEMPERATURE, temperature);
        values.put(COLUMN_HUMIDITY, humidity);
        values.put(COLUMN_SOIL, soil);
        values.put(COLUMN_BATTERY, battery);
        values.put(COLUMN_ALERTS, alerts);
        values.put(COLUMN_UPLOADED, 0);

        return db.insert(TABLE_SENSOR_DATA, null, values);
    }

    public List<SensorData> getUnuploadedData() {
        List<SensorData> dataList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_SENSOR_DATA, null,
                COLUMN_UPLOADED + "=0", null, null, null,
                COLUMN_TIMESTAMP + " ASC");

        if (cursor.moveToFirst()) {
            do {
                SensorData data = new SensorData();
                data.id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID));
                data.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP));
                data.temperature = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_TEMPERATURE));
                data.humidity = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_HUMIDITY));
                data.soil = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_SOIL));
                data.battery = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_BATTERY));
                data.alerts = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ALERTS));
                dataList.add(data);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return dataList;
    }

    public void markAsUploaded(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_UPLOADED, 1);
        db.update(TABLE_SENSOR_DATA, values, COLUMN_ID + "=?",
                new String[]{String.valueOf(id)});
    }

    public void clearTestData() {
        SQLiteDatabase db = this.getWritableDatabase();
        int deletedRows = db.delete(TABLE_SENSOR_DATA, null, null);
        android.util.Log.d("LogCatSmartAgri", "Cleared " + deletedRows + " test records from SQLite");
    }

    // Method to check if a record is marked as uploaded
    public boolean isRecordUploaded(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_SENSOR_DATA,
                new String[]{COLUMN_UPLOADED},
                COLUMN_ID + "=?",
                new String[]{String.valueOf(id)},
                null, null, null);

        boolean isUploaded = false;
        if (cursor.moveToFirst()) {
            isUploaded = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_UPLOADED)) == 1;
        }
        cursor.close();

        android.util.Log.d("LogCatSmartAgri", "Record ID " + id + " upload status: " + (isUploaded ? "uploaded" : "not uploaded"));
        return isUploaded;
    }

    // Method to get all records (uploaded and unuploaded) for debugging
    public List<SensorData> getAllRecords() {
        List<SensorData> dataList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_SENSOR_DATA, null, null, null, null, null, COLUMN_TIMESTAMP + " ASC");

        android.util.Log.d("LogCatSmartAgri", "Retrieving all records from SQLite...");

        if (cursor.moveToFirst()) {
            do {
                SensorData data = new SensorData();
                data.id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID));
                data.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP));
                data.temperature = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_TEMPERATURE));
                data.humidity = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_HUMIDITY));
                data.soil = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_SOIL));
                data.battery = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_BATTERY));
                data.alerts = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ALERTS));

                boolean isUploaded = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_UPLOADED)) == 1;
                android.util.Log.d("LogCatSmartAgri", "Record ID " + data.id + ": uploaded=" + isUploaded);

                dataList.add(data);
            } while (cursor.moveToNext());
        }
        cursor.close();

        android.util.Log.d("LogCatSmartAgri", "Retrieved " + dataList.size() + " total records from SQLite");
        return dataList;
    }

    public void logDatabaseStatus() {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor unuploadedCursor = db.query(TABLE_SENSOR_DATA, new String[]{"COUNT(*)"},
                COLUMN_UPLOADED + "=0", null, null, null, null);
        unuploadedCursor.moveToFirst();
        int unuploadedCount = unuploadedCursor.getInt(0);
        unuploadedCursor.close();

        Cursor uploadedCursor = db.query(TABLE_SENSOR_DATA, new String[]{"COUNT(*)"},
                COLUMN_UPLOADED + "=1", null, null, null, null);
        uploadedCursor.moveToFirst();
        int uploadedCount = uploadedCursor.getInt(0);
        uploadedCursor.close();

        Cursor totalCursor = db.query(TABLE_SENSOR_DATA, new String[]{"COUNT(*)"},
                null, null, null, null, null);
        totalCursor.moveToFirst();
        int totalCount = totalCursor.getInt(0);
        totalCursor.close();

        android.util.Log.d("LogCatSmartAgri", "========== DATABASE STATUS ==========");
        android.util.Log.d("LogCatSmartAgri", "Total records: " + totalCount);
        android.util.Log.d("LogCatSmartAgri", "Uploaded records: " + uploadedCount);
        android.util.Log.d("LogCatSmartAgri", "Unuploaded records: " + unuploadedCount);
        android.util.Log.d("LogCatSmartAgri", "===================================");
    }
}