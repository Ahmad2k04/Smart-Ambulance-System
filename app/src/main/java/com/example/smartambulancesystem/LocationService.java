package com.example.smartambulancesystem;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import android.annotation.SuppressLint;
@SuppressLint("ForegroundServiceType")

public class LocationService extends Service {

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private DatabaseReference ambulanceRef;
    private String emergencyId;
    private Location lastETALocation;
    private boolean emergencyCompleted = false;
    private long reachStartTime = 0;
    private static final long REACH_CONFIRM_TIME = 5000; // 5 seconds

    private static final String CHANNEL_ID = "ambulance_channel";

    private boolean wasOffline = false;

    @Override
    public void onCreate() {
        super.onCreate();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        ambulanceRef = FirebaseDatabase.getInstance().getReference("emergencies");

        createNotificationChannel();

        Notification notification = createNotification();
        startForeground(1, notification);

        startTracking();
    }

    private void startTracking() {

        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                2000
        )
                .setMinUpdateIntervalMillis(1000)
                .setWaitForAccurateLocation(true)
                .build();

        emergencyId = getSharedPreferences("ambulance", Context.MODE_PRIVATE)
                .getString("emergencyId", null);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {

                if (result == null || emergencyId == null) return;

                Location location = result.getLastLocation();
                boolean online = NetworkUtil.isInternetAvailable(LocationService.this);
                if (!online && !wasOffline) {

                    wasOffline = true;

                    android.widget.Toast.makeText(
                            LocationService.this,
                            "🔴 Offline\nWaiting for Internet...",
                            android.widget.Toast.LENGTH_SHORT
                    ).show();

                } else if (online && wasOffline) {

                    wasOffline = false;

                    android.widget.Toast.makeText(
                            LocationService.this,
                            "🟢 Internet Connected",
                            android.widget.Toast.LENGTH_SHORT
                    ).show();

                }

                if (lastETALocation == null ||
                        location.distanceTo(lastETALocation) > 100) {

                    updateETA(location);

                    lastETALocation = location;
                }
                android.util.Log.d("LocationService",
                        "Lat: " + location.getLatitude() +
                                " Lng: " + location.getLongitude());

                if (NetworkUtil.isInternetAvailable(LocationService.this)) {

                    ambulanceRef.child(emergencyId).child("latitude")
                            .setValue(location.getLatitude());

                    ambulanceRef.child(emergencyId).child("longitude")
                            .setValue(location.getLongitude());

                }

                int speedKmh = 0;

                if (location.hasSpeed() && location.getAccuracy() <= 30) {
                    speedKmh = Math.round(location.getSpeed() * 3.6f);

                    if (speedKmh < 3) {
                        speedKmh = 0;
                    }
                }

                ambulanceRef.child(emergencyId)
                        .child("speed")
                        .setValue(speedKmh);
                checkHospitalReached(location);
            }
        };

        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
        );
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Emergency Active 🚑")
                .setContentText("Live ambulance tracking running")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Ambulance Tracking",
                    NotificationManager.IMPORTANCE_HIGH
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification notification = createNotification();
            startForeground(1, notification);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }

        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    private void updateETA(Location ambulanceLocation) {
        if (!NetworkUtil.isInternetAvailable(this)) {
            return;
        }

        ambulanceRef.child(emergencyId)
                .get()
                .addOnSuccessListener(snapshot -> {

                    Double hospitalLat = snapshot.child("hospitalLat")
                            .getValue(Double.class);

                    Double hospitalLng = snapshot.child("hospitalLng")
                            .getValue(Double.class);


                    if (hospitalLat == null || hospitalLng == null) {
                        return;
                    }


                    float[] result = new float[1];

                    Location.distanceBetween(
                            ambulanceLocation.getLatitude(),
                            ambulanceLocation.getLongitude(),
                            hospitalLat,
                            hospitalLng,
                            result
                    );


                    double distanceKm = result[0] / 1000;


                    int speed = Math.max(20, (int) (ambulanceLocation.getSpeed() * 3.6f));


                    int minutes = (int) ((distanceKm / speed) * 60);


                    if (minutes < 1) {
                        minutes = 1;
                    }


                    ambulanceRef.child(emergencyId)
                            .child("eta")
                            .setValue(minutes + " minutes");

                });
    }
        private void checkHospitalReached(Location ambulanceLocation) {

            if (emergencyCompleted) return;

            ambulanceRef.child(emergencyId)
                    .get()
                    .addOnSuccessListener(snapshot -> {

                        Double hospitalLat = snapshot.child("hospitalLat").getValue(Double.class);
                        Double hospitalLng = snapshot.child("hospitalLng").getValue(Double.class);

                        if (hospitalLat == null || hospitalLng == null) {
                            return;
                        }

                        float[] result = new float[1];

                        Location.distanceBetween(
                                ambulanceLocation.getLatitude(),
                                ambulanceLocation.getLongitude(),
                                hospitalLat,
                                hospitalLng,
                                result
                        );

                        float distance = result[0];
                        android.util.Log.d("AUTO_COMPLETE",
                                "Distance = " + distance + " Accuracy = " + ambulanceLocation.getAccuracy());



                        if (distance <= 120 &&
                                ambulanceLocation.hasAccuracy() &&
                                ambulanceLocation.getAccuracy() <= 30 &&
                                (!ambulanceLocation.hasSpeed() || ambulanceLocation.getSpeed() < 10.0f)) {

                            if (reachStartTime == 0) {
                                reachStartTime = System.currentTimeMillis();
                                return;
                            }

                            if (System.currentTimeMillis() - reachStartTime >= REACH_CONFIRM_TIME) {

                                emergencyCompleted = true;
                                ambulanceRef.child(emergencyId)
                                        .child("arrived")
                                        .setValue(true);
                                snapshot.getRef().child("arrived").setValue(true);
                                snapshot.getRef().child("status").setValue("Completed");
                                snapshot.getRef().child("completedAt").setValue(System.currentTimeMillis());

                                snapshot.getRef().get().addOnSuccessListener(updatedSnapshot -> {

                                    FirebaseDatabase.getInstance()
                                            .getReference("completedEmergency")
                                            .child(emergencyId)
                                            .setValue(updatedSnapshot.getValue())
                                            .addOnSuccessListener(unused -> {

                                                updatedSnapshot.getRef().removeValue();

                                            });

                                });

                                getSharedPreferences("ambulance", MODE_PRIVATE)
                                        .edit()
                                        .remove("emergencyId")
                                        .apply();
                                android.widget.Toast.makeText(
                                        LocationService.this,
                                        "Destination reached.\nEmergency completed successfully.",
                                        android.widget.Toast.LENGTH_LONG
                                ).show();

                                Intent intent = new Intent(LocationService.this, HomeActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                startActivity(intent);



                                stopSelf();
                            }

                        } else {
                            reachStartTime = 0;
                        }

                    });
        }
    }
