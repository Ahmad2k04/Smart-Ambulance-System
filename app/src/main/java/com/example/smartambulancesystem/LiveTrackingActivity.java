package com.example.smartambulancesystem;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import com.google.firebase.database.*;


import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Color;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.util.HashMap;

import android.widget.ImageButton;



public class LiveTrackingActivity extends AppCompatActivity
        implements OnMapReadyCallback {

    private GoogleMap googleMap;

    private TextView txtEta;
    private TextView txtSpeed;

    private Marker ambulanceMarker;
    private Marker hospitalMarker;
    private Polyline routeLine;


    private DatabaseReference emergencyRef;
    private String emergencyId;

    private boolean cameraMoved = false;

    private double lastRouteLat = 0;
    private double lastRouteLng = 0;

    private static final float ROUTE_UPDATE_DISTANCE = 120f;


    private int orsCallCount;
    private static final int MAX_ORS_CALLS = 2;
    private long lastRerouteTime = 0;
    private static final long REROUTE_COOLDOWN = 10000; // 15 seconds


    private List<LatLng> currentRoutePoints = new ArrayList<>();
    private boolean navigationStarted = false;


    private ImageButton btnRecenter;
    private LatLng currentAmbulanceLocation;

    private float ambulanceBearing = 0f;
    private ValueAnimator markerAnimator;

    private android.content.SharedPreferences prefs;

    private long lastSpeedTime = 0;


    private long offRouteStartTime = 0;
    private static final long OFF_ROUTE_DELAY = 10000; // 10 seconds


    private boolean orsLimitShown = false;


    private boolean followAmbulance = true;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_tracking);
        prefs = getSharedPreferences("ambulance", MODE_PRIVATE);
        btnRecenter = findViewById(R.id.btnRecenter);
        btnRecenter.setOnClickListener(v -> {
            followAmbulance = true;

            if (currentAmbulanceLocation != null && googleMap != null) {

                googleMap.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                                new CameraPosition.Builder()
                                        .target(currentAmbulanceLocation)
                                        .zoom(17f)
                                        .bearing(ambulanceBearing)
                                        .tilt(45f)
                                        .build()
                        )
                );

            }

        });

        txtEta = findViewById(R.id.txtEta);
        txtSpeed = findViewById(R.id.txtSpeed);

        emergencyId = getIntent().getStringExtra("emergencyId");

        if (emergencyId == null) {
            emergencyId = getSharedPreferences("ambulance", MODE_PRIVATE)
                    .getString("emergencyId", null);
        }
        orsCallCount = prefs.getInt("orsCalls_" + emergencyId, 0);


        if (emergencyId == null) {
            Toast.makeText(this,
                    "No active emergency found",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        emergencyRef = FirebaseDatabase.getInstance()
                .getReference("emergencies")
                .child(emergencyId);

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.map);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {

        googleMap = map;
        googleMap.setOnCameraMoveStartedListener(reason -> {

            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {

                followAmbulance = false;

            }
        });

        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);

        googleMap.setTrafficEnabled(true);

        startFirebaseTracking();
    }

    private void startFirebaseTracking() {

        emergencyRef.addValueEventListener(new ValueEventListener() {

            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                if (!snapshot.exists()) return;

                String status = snapshot.child("status").getValue(String.class);

                if ("Completed".equals(status)) {

                    Intent intent = new Intent(
                            LiveTrackingActivity.this,
                            HomeActivity.class
                    );

                    intent.addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                    );
                    prefs.edit()
                            .remove("orsCalls_" + emergencyId)
                            .remove("route_" + emergencyId)
                            .apply();

                    startActivity(intent);
                    finish();
                    return;
                }

                Double lat = snapshot.child("latitude").getValue(Double.class);
                Double lng = snapshot.child("longitude").getValue(Double.class);
                Integer speed = snapshot.child("speed").getValue(Integer.class);

                if (speed != null) {
                    android.text.SpannableString speedText =
                            new android.text.SpannableString(speed + "\nkm/h");

                    speedText.setSpan(
                            new android.text.style.RelativeSizeSpan(0.50f),
                            (speed + "\n").length(),
                            speedText.length(),
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );

                    txtSpeed.setText(speedText);
                }

                Double hLat = snapshot.child("hospitalLat").getValue(Double.class);
                Double hLng = snapshot.child("hospitalLng").getValue(Double.class);

                if (lat == null || lng == null) return;



                LatLng ambulance = new LatLng(lat, lng);
                if (lastRouteLat != 0 && lastRouteLng != 0) {

                    long currentTime = System.currentTimeMillis();

                    if (lastSpeedTime != 0) {

                        float[] result = new float[1];

                        Location.distanceBetween(
                                lastRouteLat,
                                lastRouteLng,
                                lat,
                                lng,
                                result
                        );

                        float seconds = (currentTime - lastSpeedTime) / 1000f;


                    }

                    lastSpeedTime = currentTime;
                }
                currentAmbulanceLocation = ambulance;


                // Ambulance marker
                if (ambulanceMarker == null) {

                    ambulanceMarker = googleMap.addMarker(
                            new MarkerOptions()
                                    .position(ambulance)
                                    .title("Ambulance")
                                    .icon(resizeIcon(R.drawable.ambulance, 100, 100))
                                    .anchor(0.5f, 0.5f)
                    );

                } else {

                    animateMarker(ambulance);
                }

                // Hospital marker
                if (hLat != null && hLng != null) {

                    LatLng hospital = new LatLng(hLat, hLng);

                    if (hospitalMarker == null) {

                        hospitalMarker = googleMap.addMarker(
                                new MarkerOptions()
                                        .position(hospital)
                                        .title("Hospital")
                                        .icon(resizeIcon(R.drawable.hospital, 100, 100))
                        );

                    } else {

                        hospitalMarker.setPosition(hospital);
                    }

                    if (lastRouteLat == 0 && lastRouteLng == 0) {

                        List<LatLng> saved = loadRoute();

                        if (!saved.isEmpty()) {

                            drawRoute(saved);
                            currentRoutePoints = new ArrayList<>(saved);
                            lastRouteLat = lat;
                            lastRouteLng = lng;
                            updateRemainingRoute(ambulance);

                        } else {

                            lastRouteLat = lat;
                            lastRouteLng = lng;

                            requestRoadRoute(ambulance, hospital);
                        }
                    } else {



                        if (isOffRoute(ambulance)
                                && System.currentTimeMillis() - lastRerouteTime >= REROUTE_COOLDOWN) {

                            lastRerouteTime = System.currentTimeMillis();

                            lastRouteLat = lat;
                            lastRouteLng = lng;

                            requestRoadRoute(ambulance, hospital);
                        }
                        updateRemainingRoute(ambulance);
                    }
                }

                if (!cameraMoved) {

                    googleMap.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                    ambulance,
                                    15f
                            )
                    );

                    cameraMoved = true;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }




    private void requestRoadRoute(LatLng origin, LatLng destination) {
        if (orsCallCount >= MAX_ORS_CALLS) {

            if (!orsLimitShown) {
                orsLimitShown = true;

                Toast.makeText(
                        this,
                        "Route updates limit reached",
                        Toast.LENGTH_SHORT
                ).show();
            }

            return;
        }

        orsCallCount++;

        prefs.edit()
                .putInt("orsCalls_" + emergencyId, orsCallCount)
                .apply();

        String url = "https://api.heigit.org/openrouteservice/v2/directions/driving-car/geojson";

        RequestQueue queue = Volley.newRequestQueue(this);

        JSONObject body = new JSONObject();

        try {

            JSONArray coordinates = new JSONArray();

            JSONArray start = new JSONArray();
            start.put(origin.longitude);
            start.put(origin.latitude);

            JSONArray end = new JSONArray();
            end.put(destination.longitude);
            end.put(destination.latitude);

            coordinates.put(start);
            coordinates.put(end);

            body.put("coordinates", coordinates);

        } catch (Exception e) {
            e.printStackTrace();
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                body,

                response -> {

                    parseORSResponse(response);

                },

                error -> {


                    Toast.makeText(
                            this,
                            error.toString(),
                            Toast.LENGTH_LONG
                    ).show();


                }

        ) {

            @Override
            public java.util.Map<String, String> getHeaders() {

                HashMap<String, String> headers = new HashMap<>();

                headers.put(
                        "Authorization",
                        BuildConfig.ORS_API_KEY
                );

                headers.put(
                        "Content-Type",
                        "application/json"
                );

                return headers;
            }

        };
        if (orsCallCount == 1) {
            navigationStarted = false;
        }

        queue.add(request);
    }
    private void parseORSResponse(JSONObject response) {

        try {

            JSONArray features = response.getJSONArray("features");

            if (features.length() == 0) {
                Toast.makeText(this, "No route found", Toast.LENGTH_SHORT).show();
                return;
            }

            JSONObject geometry = features
                    .getJSONObject(0)
                    .getJSONObject("geometry");
            JSONObject properties = features.getJSONObject(0).getJSONObject("properties");
            JSONObject summary = properties.getJSONObject("summary");

            double distance = summary.getDouble("distance");   // meters
            double duration = summary.getDouble("duration");   // seconds

            JSONArray coordinates = geometry.getJSONArray("coordinates");

            List<LatLng> points = new ArrayList<>();

            for (int i = 0; i < coordinates.length(); i++) {

                JSONArray point = coordinates.getJSONArray(i);

                double lng = point.getDouble(0);
                double lat = point.getDouble(1);

                points.add(new LatLng(lat, lng));
            }

            drawRoute(points);
            saveRoute(points);
            if (ambulanceMarker != null && followAmbulance) {
                googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                                ambulanceMarker.getPosition(),
                                17f
                        )
                );
            }
            double km = distance / 1000.0;
            int minutes = (int) Math.ceil(duration / 60.0);

            txtEta.setText(
                    "ETA: " + minutes + " min\nDistance: " +
                            String.format("%.1f", km) + " km"
            );

        } catch (Exception e) {

            e.printStackTrace();

            Toast.makeText(
                    this,
                    "Route Parse Error: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }





    private void drawRoute(List<LatLng> points) {



        if (routeLine != null) {

            routeLine.remove();
        }


        PolylineOptions options =
                new PolylineOptions()
                        .addAll(points)
                        .width(10)
                        .color(Color.BLUE);


        routeLine =
                googleMap.addPolyline(options);
        currentRoutePoints = new ArrayList<>(points);
    }


    private void animateAmbulance(List<LatLng> points) {


        if (ambulanceMarker == null ||
                points.size() == 0) return;


        LatLng start =
                ambulanceMarker.getPosition();
        LatLng end =
                points.get(points.size() - 1);
        Location oldLoc = new Location("");

        oldLoc.setLatitude(start.latitude);
        oldLoc.setLongitude(start.longitude);


        Location newLoc = new Location("");

        newLoc.setLatitude(end.latitude);
        newLoc.setLongitude(end.longitude);


        ambulanceBearing = oldLoc.bearingTo(newLoc);





        ValueAnimator animator =
                ValueAnimator.ofFloat(0, 1);


        animator.setDuration(3000);


        animator.setInterpolator(
                new LinearInterpolator()
        );


        animator.addUpdateListener(animation -> {


            float v =
                    (float) animation.getAnimatedValue();


            double lat =
                    start.latitude +
                            (end.latitude - start.latitude) * v;


            double lng =
                    start.longitude +
                            (end.longitude - start.longitude) * v;


            ambulanceMarker.setPosition(
                    new LatLng(lat, lng)
            );
            updateRemainingRoute(new LatLng(lat, lng));
            btnRecenter.setRotation(ambulanceBearing);


        });


        animator.start();
    }
    private LatLng getNearestPointOnRoute(LatLng location) {

        if (currentRoutePoints == null || currentRoutePoints.size() < 2) {
            return location;
        }

        LatLng nearestPoint = currentRoutePoints.get(0);
        float minDistance = Float.MAX_VALUE;

        for (int i = 0; i < currentRoutePoints.size() - 1; i++) {

            LatLng p1 = currentRoutePoints.get(i);
            LatLng p2 = currentRoutePoints.get(i + 1);

            double x = location.longitude;
            double y = location.latitude;

            double x1 = p1.longitude;
            double y1 = p1.latitude;

            double x2 = p2.longitude;
            double y2 = p2.latitude;

            double dx = x2 - x1;
            double dy = y2 - y1;

            if (dx == 0 && dy == 0) continue;

            double t = ((x - x1) * dx + (y - y1) * dy)
                    / (dx * dx + dy * dy);

            t = Math.max(0, Math.min(1, t));

            LatLng point = new LatLng(
                    y1 + t * dy,
                    x1 + t * dx
            );

            float[] result = new float[1];

            Location.distanceBetween(
                    location.latitude,
                    location.longitude,
                    point.latitude,
                    point.longitude,
                    result
            );

            if (result[0] < minDistance) {
                minDistance = result[0];
                nearestPoint = point;
            }
        }

        return nearestPoint;
    }
    private BitmapDescriptor resizeIcon(int drawableId, int width, int height) {

        Bitmap image = BitmapFactory.decodeResource(
                getResources(),
                drawableId
        );

        Bitmap smallImage = Bitmap.createScaledBitmap(
                image,
                width,
                height,
                false
        );

        return BitmapDescriptorFactory.fromBitmap(smallImage);
    }
    private void animateMarker(LatLng destination) {

        if (ambulanceMarker == null) return;

        LatLng routePosition = destination;

        LatLng start = ambulanceMarker.getPosition();

        if (markerAnimator != null && markerAnimator.isRunning()) {
            markerAnimator.cancel();
        }

        markerAnimator = ValueAnimator.ofFloat(0f, 1f);

        markerAnimator.setDuration(600);
        markerAnimator.setInterpolator(new LinearInterpolator());

        markerAnimator.addUpdateListener(animation -> {

            float v = (float) animation.getAnimatedValue();

            double lat = start.latitude +
                    (routePosition.latitude - start.latitude) * v;

            double lng = start.longitude +
                    (routePosition.longitude - start.longitude) * v;

            LatLng position = new LatLng(lat, lng);

            ambulanceMarker.setPosition(position);

            if (followAmbulance && googleMap != null) {

                googleMap.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                                new CameraPosition.Builder()
                                        .target(position)
                                        .zoom(17f)
                                        .bearing(ambulanceBearing)
                                        .tilt(45f)
                                        .build()
                        )
                );
            }
        });

        markerAnimator.start();
    }
    private boolean isOffRoute(LatLng currentLocation) {

        if (currentRoutePoints.isEmpty()) {
            return false;
        }

        float[] result = new float[1];
        float nearestDistance = Float.MAX_VALUE;

        for (LatLng point : currentRoutePoints) {

            Location.distanceBetween(
                    currentLocation.latitude,
                    currentLocation.longitude,
                    point.latitude,
                    point.longitude,
                    result
            );

            if (result[0] < nearestDistance) {
                nearestDistance = result[0];
            }
        }

        Log.d("OFF_ROUTE", "Distance=" + nearestDistance + " Points=" + currentRoutePoints.size());

        // Ambulance has reached the route for the first time
        if (!navigationStarted) {

            if (nearestDistance <= 80) {
                navigationStarted = true;
            } else {
                return false;
            }
        }

        // Off-route detection
        if (nearestDistance > ROUTE_UPDATE_DISTANCE
                && ambulanceMarker != null) {



            if (offRouteStartTime == 0) {
                offRouteStartTime = System.currentTimeMillis();
                return false;
            }

            if (System.currentTimeMillis() - offRouteStartTime < OFF_ROUTE_DELAY) {
                return false;
            }

            offRouteStartTime = 0;

            Toast.makeText(
                    this,
                    "OFF ROUTE: " + (int) nearestDistance + " m",
                    Toast.LENGTH_SHORT
            ).show();

            return true;
        }

        // Back on route
        offRouteStartTime = 0;
        return false;
    }






    private void updateRemainingRoute(LatLng ambulance) {

        if (currentRoutePoints.isEmpty()) return;

        int nearestIndex = 0;
        float minDistance = Float.MAX_VALUE;
        float[] result = new float[1];

        for (int i = 0; i < currentRoutePoints.size(); i++) {

            Location.distanceBetween(
                    ambulance.latitude,
                    ambulance.longitude,
                    currentRoutePoints.get(i).latitude,
                    currentRoutePoints.get(i).longitude,
                    result
            );

            if (result[0] < minDistance) {
                minDistance = result[0];
                nearestIndex = i;
            }
        }

        if (nearestIndex > 0) {

            currentRoutePoints = new ArrayList<>(
                    currentRoutePoints.subList(nearestIndex, currentRoutePoints.size())
            );

            if (routeLine != null) {
                routeLine.setPoints(currentRoutePoints);
            }
        }

        // Calculate remaining distance
        double remainingDistance = 0;

        for (int i = 0; i < currentRoutePoints.size() - 1; i++) {

            Location.distanceBetween(
                    currentRoutePoints.get(i).latitude,
                    currentRoutePoints.get(i).longitude,
                    currentRoutePoints.get(i + 1).latitude,
                    currentRoutePoints.get(i + 1).longitude,
                    result
            );

            remainingDistance += result[0];
        }

        double remainingKm = remainingDistance / 1000.0;
        double avgSpeed = 40.0; // km/h

        int etaMinutes = (int) Math.ceil((remainingKm / avgSpeed) * 60);

        txtEta.setText(
                "ETA: " + etaMinutes + " min\nDistance: " +
                        String.format("%.1f", remainingKm) + " km"
        );
    }
    private void saveRoute(List<LatLng> points) {

        StringBuilder data = new StringBuilder();

        for (LatLng p : points) {
            data.append(p.latitude)
                    .append(",")
                    .append(p.longitude)
                    .append(";");
        }

        prefs.edit()
                .putString("route_" + emergencyId, data.toString())
                .apply();
    }
    private List<LatLng> loadRoute() {

        List<LatLng> points = new ArrayList<>();

        String data = prefs.getString("route_" + emergencyId, null);

        if (data == null) return points;

        for (String item : data.split(";")) {

            if (item.isEmpty()) continue;

            String[] value = item.split(",");

            if (value.length != 2) continue;

            points.add(
                    new LatLng(
                            Double.parseDouble(value[0]),
                            Double.parseDouble(value[1])
                    )
            );
        }

        return points;
    }
}