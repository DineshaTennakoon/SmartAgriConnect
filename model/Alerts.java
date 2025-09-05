package com.smartagri.connect.model;

public class Alerts {
    private String alerts;
    private String date;


    public Alerts(String alerts, String date) {
        this.alerts = alerts;
        this.date = date;
    }

    public String getAlerts() {
        return alerts;
    }

    public void setAlerts(String alerts) {
        this.alerts = alerts;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }
}
