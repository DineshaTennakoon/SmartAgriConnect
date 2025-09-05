package com.smartagri.connect.model;

public class SensorData {
    public int id;
    public long timestamp;
    public float temperature;
    public float humidity;
    public float soil;
    public int battery;
    public String alerts;

    public SensorData() {}

    public SensorData(long timestamp, float temperature, float humidity,
                      float soil, int battery, String alerts) {
        this.timestamp = timestamp;
        this.temperature = temperature;
        this.humidity = humidity;
        this.soil = soil;
        this.battery = battery;
        this.alerts = alerts;
    }
}