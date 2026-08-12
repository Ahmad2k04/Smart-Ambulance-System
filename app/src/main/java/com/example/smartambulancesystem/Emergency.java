package com.example.smartambulancesystem;

public class Emergency {

    public String id;
    public String hospital;
    public String city;

    public double hospitalLat;
    public double hospitalLng;

    public double latitude;
    public double longitude;

    public String status;
    public String time;

    public String driverEmail;
    public String eta;
    public String routeStatus;

    public boolean arrived = false;

    public Emergency() {
        // Required for Firebase
    }

    public Emergency(String hospital,
                     double hospitalLat,
                     double hospitalLng,
                     double latitude,
                     double longitude,
                     String status,
                     String time,
                     String driverEmail,
                     String city) {

        this.hospital = hospital;

        this.hospitalLat = hospitalLat;
        this.hospitalLng = hospitalLng;

        this.latitude = latitude;
        this.longitude = longitude;

        this.status = status;
        this.time = time;

        this.driverEmail = driverEmail;
        this.city = city;

        this.eta = "Calculating...";
        this.routeStatus = "ACTIVE";
        this.arrived = false;
    }
}