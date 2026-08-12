package com.example.smartambulancesystem;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import androidx.activity.OnBackPressedCallback;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;


import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import java.util.HashMap;

import android.widget.AdapterView;
import android.widget.TextView;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;


import android.content.Context;





public class HomeActivity extends AppCompatActivity {
    Button btnLogout;
    private long backPressedTime;

    private String currentEmergencyId;
    private DatabaseReference ambulanceRef;
    private FusedLocationProviderClient fusedLocationClient;

    private Spinner spCity;
    private Spinner spHospital;

    private Button btnMap;
    private Button btnEmergency;
    private Button btnPolice;
    private Button btnComplete;
    private BroadcastReceiver networkReceiver;
    TextView txtConnection;



    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        cleanupOldEmergencies();
        btnLogout = findViewById(R.id.btnLogout);

        btnLogout.setOnClickListener(v -> {

            if (currentEmergencyId != null) {

                Toast.makeText(
                        HomeActivity.this,
                        "Complete the current emergency before logout.",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            getSharedPreferences("ambulance", MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply();

            Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();

        });
        final long[] backPressedTime = {0};

        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {

                    @Override
                    public void handleOnBackPressed() {

                        if (backPressedTime[0] + 2000 > System.currentTimeMillis()) {

                            finishAffinity();

                        } else {

                            Toast.makeText(
                                    HomeActivity.this,
                                    "Press back again to exit",
                                    Toast.LENGTH_SHORT
                            ).show();

                            backPressedTime[0] = System.currentTimeMillis();
                        }
                    }
                });

        ambulanceRef = FirebaseDatabase.getInstance()
                .getReference("emergencies");
        btnMap = findViewById(R.id.btnMap);
        btnEmergency = findViewById(R.id.btnEmergency);
        String emergencyId = getSharedPreferences("ambulance", MODE_PRIVATE)
                .getString("emergencyId", null);

        if (emergencyId != null) {
            btnEmergency.setEnabled(false);
            btnEmergency.setText("Emergency Started");
        }

        btnPolice = findViewById(R.id.btnPolice);
        btnComplete = findViewById(R.id.btnComplete);
        txtConnection = findViewById(R.id.txtConnection);
        updateConnectionStatus();
        networkReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateConnectionStatus();
            }
        };

        registerReceiver(networkReceiver,
                new IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION));


        spHospital = findViewById(R.id.spHospital);
        spCity = findViewById(R.id.spCity);
        // Show "Select Hospital" before city is selected
        ArrayList<String> defaultHospital = new ArrayList<>();
        defaultHospital.add("Select Hospital");

        ArrayAdapter<String> hospitalAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        defaultHospital
                );

        hospitalAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);

        spHospital.setAdapter(hospitalAdapter);
        String[] cities = {
                "Select City",
                "Kanpur",
                "Lucknow",
                "Agra",
                "Varanasi",
                "Prayagraj",
                "Unnao",
                "Ayodhya",
                "Fatehpur",
                "Jhansi"

        };


        ArrayAdapter<String> cityAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        cities
                );

        cityAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);


        spCity.setAdapter(cityAdapter);
        spCity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent,
                                       android.view.View view,
                                       int position,
                                       long id) {

                String city = spCity.getSelectedItem().toString();

                if (!city.equals("Select City")) {

                    loadHospitals(city);

                }
            }


            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });



        String driverEmail = getSharedPreferences("ambulance", MODE_PRIVATE)
                .getString("driverEmail", "");

        ambulanceRef.addListenerForSingleValueEvent(new ValueEventListener() {

            @Override
            public void onDataChange(DataSnapshot snapshot) {

                for (DataSnapshot ds : snapshot.getChildren()) {

                    String email = ds.child("driverEmail").getValue(String.class);
                    String status = ds.child("status").getValue(String.class);

                    if (driverEmail.equals(email) && "RUNNING".equals(status)) {

                        currentEmergencyId = ds.getKey();

                        getSharedPreferences("ambulance", MODE_PRIVATE)
                                .edit()
                                .putString("emergencyId", currentEmergencyId)
                                .apply();

                        Toast.makeText(HomeActivity.this,
                                "Previous emergency restored",
                                Toast.LENGTH_LONG).show();
                        btnEmergency.setEnabled(false);
                        btnEmergency.setText("Emergency Started");

                        spCity.setEnabled(false);
                        spHospital.setEnabled(false);
                        Intent serviceIntent = new Intent(HomeActivity.this, LocationService.class);

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent);
                        } else {
                            startService(serviceIntent);
                        }

                        // Open Live Tracking automatically
                        Intent intent = new Intent(HomeActivity.this, LiveTrackingActivity.class);
                        intent.putExtra("emergencyId", currentEmergencyId);
                        startActivity(intent);

                        break;
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {

            }
        });
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);


        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                1
        );
        // Request Background Location Permission (Android 10+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                        2
                );
            }
        }

        btnMap.setOnClickListener(v -> {

            if (currentEmergencyId == null) {
                currentEmergencyId = getSharedPreferences("ambulance", MODE_PRIVATE)
                        .getString("emergencyId", null);
            }

            if (currentEmergencyId == null) {
                Toast.makeText(this, "Start an emergency first", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(HomeActivity.this, LiveTrackingActivity.class);
            intent.putExtra("emergencyId", currentEmergencyId);
            startActivity(intent);
        });

        btnEmergency.setOnClickListener(v -> {
            btnEmergency.setEnabled(false);
            btnEmergency.setText("Starting...");
            new android.os.Handler().postDelayed(() -> {

                if (btnEmergency.getText().toString().equals("Starting...")) {

                    btnEmergency.setEnabled(true);
                    btnEmergency.setText("Start Emergency");

                    spCity.setEnabled(true);
                    spHospital.setEnabled(true);

                    spCity.setSelection(0);
                    spHospital.setSelection(0);

                    Toast.makeText(HomeActivity.this,
                            "Request timed out. Please try again.",
                            Toast.LENGTH_LONG).show();
                }

            }, 15000);


            String hospital = spHospital.getSelectedItem().toString();

            if (hospital.equals("Select Hospital")) {

                btnEmergency.setEnabled(true);
                btnEmergency.setText("Start Emergency");

                Toast.makeText(this, "Please select a hospital", Toast.LENGTH_SHORT).show();
                return;
            }

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {

                Toast.makeText(this,
                        "Location permission not granted",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isLocationEnabled()) {

                new androidx.appcompat.app.AlertDialog.Builder(HomeActivity.this)
                        .setTitle("GPS Required")
                        .setMessage("Please turn on GPS to start an emergency.")
                        .setCancelable(false)
                        .setPositiveButton("Turn On", (dialog, which) -> {
                            startActivity(new Intent(
                                    android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        })
                        .setNegativeButton("Cancel", null)
                        .show();

                return;
            }

            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {



                if (location != null) {

                    startEmergency(location, hospital);

                } else {

                    LocationRequest request = new LocationRequest.Builder(
                            Priority.PRIORITY_HIGH_ACCURACY,
                            1000
                    )
                            .setMaxUpdates(1)
                            .build();

                    LocationCallback callback = new LocationCallback() {
                        @Override
                        public void onLocationResult(LocationResult result) {

                            fusedLocationClient.removeLocationUpdates(this);

                            if (result == null || result.getLastLocation() == null) {

                                Toast.makeText(HomeActivity.this,
                                        "Unable to get current location",
                                        Toast.LENGTH_LONG).show();
                                return;
                            }

                            startEmergency(result.getLastLocation(), hospital);
                        }
                    };

                    fusedLocationClient.requestLocationUpdates(
                            request,
                            callback,
                            getMainLooper()
                    );
                }
            });
        });

        btnPolice.setOnClickListener(v ->
                Toast.makeText(this,
                        "Police Alert Sent 🚓",
                        Toast.LENGTH_SHORT).show()
        );

        btnComplete.setOnClickListener(v -> {

            if (currentEmergencyId == null) {
                Toast.makeText(this,
                        "No active emergency",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // stop service
            stopService(new Intent(HomeActivity.this, LocationService.class));

            DatabaseReference oldRef =
                    FirebaseDatabase.getInstance()
                            .getReference("emergencies")
                            .child(currentEmergencyId);

            DatabaseReference historyRef =
                    FirebaseDatabase.getInstance()
                            .getReference("completedEmergencies")
                            .child(currentEmergencyId);

            oldRef.addListenerForSingleValueEvent(new ValueEventListener() {

                @Override
                public void onDataChange(DataSnapshot snapshot) {

                    HashMap<String, Object> history = new HashMap<>();

                    history.put("city",
                            snapshot.child("city").getValue(String.class));

                    history.put("hospital",
                            snapshot.child("hospital").getValue(String.class));

                    history.put("driverEmail",
                            snapshot.child("driverEmail").getValue(String.class));

                    history.put("time",
                            snapshot.child("time").getValue(String.class));

                    history.put("status",
                            "COMPLETED");

                    history.put("completedAt",
                            System.currentTimeMillis());

                    historyRef.setValue(history)
                            .addOnSuccessListener(unused -> {

                                // delete active emergency
                                oldRef.removeValue().addOnSuccessListener(unused2 -> {

                                    Toast.makeText(
                                            HomeActivity.this,
                                            "Emergency Completed ✅",
                                            Toast.LENGTH_LONG
                                    ).show();

                                    currentEmergencyId = null;

                                    getSharedPreferences("ambulance", MODE_PRIVATE)
                                            .edit()
                                            .remove("emergencyId")
                                            .apply();

                                    btnEmergency.setEnabled(true);
                                    btnEmergency.setText("Start Emergency");

                                    spCity.setEnabled(true);
                                    spHospital.setEnabled(true);

                                    spCity.setSelection(0);
                                    spHospital.setSelection(0);

                                });

                                currentEmergencyId = null;

                                getSharedPreferences("ambulance", MODE_PRIVATE)
                                        .edit()
                                        .remove("emergencyId")
                                        .apply();

                                btnEmergency.setEnabled(true);
                                btnEmergency.setText("Start Emergency");

                                spCity.setEnabled(true);
                                spHospital.setEnabled(true);

                                spCity.setSelection(0);
                                spHospital.setSelection(0);

                            });
                }

                @Override
                public void onCancelled(DatabaseError error) {

                }
            });

        });
    }

    private void startEmergency(android.location.Location location, String hospital) {


        double lat = location.getLatitude();
        double lng = location.getLongitude();

        String time = new SimpleDateFormat(
                "dd-MM-yyyy HH:mm:ss",
                Locale.getDefault()
        ).format(new Date());

        currentEmergencyId = ambulanceRef.push().getKey();
        btnEmergency.setEnabled(false);
        btnEmergency.setText("Starting...");

        if (currentEmergencyId == null) {
            Toast.makeText(this,
                    "Failed to create emergency",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        HospitalLocation hospitalLocation = HospitalHelper.getHospital(hospital);

        if (currentEmergencyId == null) {

            btnEmergency.setEnabled(true);
            btnEmergency.setText("Start Emergency");

            Toast.makeText(this,
                    "Failed to create emergency",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String driverEmail = getSharedPreferences("ambulance", MODE_PRIVATE)
                .getString("email", "");

        String city = spCity.getSelectedItem().toString();

        Emergency emergency = new Emergency(
                hospital,
                hospitalLocation.latitude,
                hospitalLocation.longitude,
                lat,
                lng,
                "RUNNING",
                time,
                driverEmail,
                city
        );
        if (!isInternetAvailable()) {

            btnEmergency.setEnabled(true);
            btnEmergency.setText("Start Emergency");

            Toast.makeText(this,
                    "Please connect to the Internet",
                    Toast.LENGTH_LONG).show();

            return;
        }
        ambulanceRef.child(currentEmergencyId)
                .setValue(emergency)
                .addOnSuccessListener(unused -> {

                    Toast.makeText(this,
                            "Emergency Sent 🚑",
                            Toast.LENGTH_LONG).show();
                    btnEmergency.setEnabled(false);
                    btnEmergency.setText("Emergency Started");
                    spHospital.setEnabled(false);
                    spCity.setEnabled(false);


                    getSharedPreferences("ambulance", MODE_PRIVATE)
                            .edit()
                            .putString("emergencyId", currentEmergencyId)
                            .apply();

                    Intent intent = new Intent(HomeActivity.this, LocationService.class);

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        startForegroundService(intent);
                    } else {
                        startService(intent);
                    }


                    btnEmergency.setText("Emergency Started");
                    btnEmergency.setEnabled(false);

                    startActivity(new Intent(HomeActivity.this, LiveTrackingActivity.class));

                })
                .addOnFailureListener(e -> {

                    currentEmergencyId = null;

                    btnEmergency.setEnabled(true);
                    btnEmergency.setText("Start Emergency");

                    getSharedPreferences("ambulance", MODE_PRIVATE)
                            .edit()
                            .remove("emergencyId")
                            .apply();

                    Toast.makeText(this,
                            "Network error. Please check your internet and try again.",
                            Toast.LENGTH_LONG).show();
                });
    }


    private void loadHospitalsByCity() {

        String driverEmail = getSharedPreferences("ambulance", MODE_PRIVATE)
                .getString("driverEmail", "");


        DatabaseReference usersRef =
                FirebaseDatabase.getInstance()
                        .getReference("Users");


        usersRef.orderByChild("email")
                .equalTo(driverEmail)
                .addListenerForSingleValueEvent(new ValueEventListener() {

                    @Override
                    public void onDataChange(DataSnapshot snapshot) {

                        for (DataSnapshot user : snapshot.getChildren()) {

                            String city = user.child("city")
                                    .getValue(String.class);

                            if (city != null) {

                                loadHospitals(city);

                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {

                    }
                });
    }


    private void loadHospitals(String city) {

        ArrayList<String> hospitals = new ArrayList<>();

        hospitals.add("Select Hospital");


        if(city.equals("Kanpur")) {

            hospitals.add("Regency Hospital");
            hospitals.add("LPS Institute of Cardiology");
            hospitals.add("Ursula Horsman Memorial Hospital");
            hospitals.add("Mariampur Hospital");
            hospitals.add("Hallet Hospital");
            hospitals.add("Rama Medical College");
            hospitals.add("Kanshiram Hospital");



        }

        else if(city.equals("Lucknow")) {

            hospitals.add("KGMU Hospital");
            hospitals.add("SGPGI Hospital");
            hospitals.add("Dr Ram Manohar Lohia Hospital");
            hospitals.add("Medanta Hospital Lucknow");
            hospitals.add("Apollo Medics");
            hospitals.add("Civil Hospital Lucknow");

        }

        else if(city.equals("Agra")) {

            hospitals.add("SN Medical College");
            hospitals.add("Pushpanjali Hospital");
            hospitals.add("Rainbow Hospital");
            hospitals.add("Shanti Manglik Hospital");

            hospitals.add("Agra City Hospital");

        }

        else if(city.equals("Varanasi")) {

            hospitals.add("BHU Trauma Centre");
            hospitals.add("Heritage Hospital");
            hospitals.add("Apex Hospital");
            hospitals.add("Popular Hospital");
            hospitals.add("Galaxy Hospital");
            hospitals.add("Vijaya Hospital");

        }

        else if(city.equals("Prayagraj")) {

            hospitals.add("SRN Hospital");
            hospitals.add("Nazareth Hospital");
            hospitals.add("Vatsalya Hospital");
            hospitals.add("Phoenix Hospital");

            hospitals.add("United Medicity");

        }
        else if(city.equals("Unnao")) {

            hospitals.add("District Hospital Unnao");
            hospitals.add("Lifeline Hospital");
            hospitals.add("Shri Ram Hospital");

            hospitals.add("New City Hospital");
            hospitals.add("Sanjeevani Hospital");

        }

        else if(city.equals("Ayodhya")) {

            hospitals.add("District Hospital Ayodhya");
            hospitals.add("Shri Ram Hospital");
            hospitals.add("Kanak Hospital");
            hospitals.add("Avadh Hospital");
            hospitals.add("Life Care Hospital");
            hospitals.add("Sahara Hospital");

        }

        else if(city.equals("Fatehpur")) {

            hospitals.add("District Hospital Fatehpur");
            hospitals.add("Aarogya Hospital Fatehpur");

        }

        else if(city.equals("Jhansi")) {

            hospitals.add("Maharani Laxmi Bai Medical College");
            hospitals.add("District Hospital Jhansi");
            hospitals.add("Lifeline Superspeciality Hospital and Heart Center");
            hospitals.add("MAXCARE SPARSH SUPER SPECIALITY HOSPITAL");

        }

        // add other cities later


        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                hospitals
        );

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spHospital.setAdapter(adapter);
        spHospital.setSelection(0, false);
    }
    private boolean isLocationEnabled() {

        android.location.LocationManager locationManager =
                (android.location.LocationManager) getSystemService(LOCATION_SERVICE);

        return locationManager != null &&
                (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                        || locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER));
    }
    private void calculateETA(double ambulanceLat,
                              double ambulanceLng,
                              double hospitalLat,
                              double hospitalLng) {

        float[] result = new float[1];

        android.location.Location.distanceBetween(
                ambulanceLat,
                ambulanceLng,
                hospitalLat,
                hospitalLng,
                result
        );

        double distanceKm = result[0] / 1000;

        int speed = 40;

        int minutes = (int)((distanceKm / speed) * 60);

        if (minutes < 1) {
            minutes = 1;
        }

        String eta = minutes + " minutes";


        if (currentEmergencyId != null) {

            ambulanceRef.child(currentEmergencyId)
                    .child("eta")
                    .setValue(eta);
        }
    }
    private void cleanupOldEmergencies() {

        DatabaseReference ref =
                FirebaseDatabase.getInstance()
                        .getReference("completedEmergencies");


        long fifteenDays =
                15 * 24 * 60 * 60 * 1000;


        long currentTime = System.currentTimeMillis();


        ref.addListenerForSingleValueEvent(new ValueEventListener() {

            @Override
            public void onDataChange(DataSnapshot snapshot) {


                for (DataSnapshot emergency : snapshot.getChildren()) {


                    Long completedAt =
                            emergency.child("completedAt")
                                    .getValue(Long.class);


                    if (completedAt != null &&
                            (currentTime - completedAt) > fifteenDays) {


                        emergency.getRef().removeValue();

                    }

                }

            }


            @Override
            public void onCancelled(DatabaseError error) {

            }

        });
    }
    private boolean isInternetAvailable() {

        android.net.ConnectivityManager cm =
                (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        if (cm == null) return false;

        android.net.NetworkInfo networkInfo = cm.getActiveNetworkInfo();

        return networkInfo != null && networkInfo.isConnected();
    }
    private void updateConnectionStatus() {

        android.net.ConnectivityManager cm =
                (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        android.net.NetworkInfo networkInfo = cm.getActiveNetworkInfo();

        if (networkInfo != null && networkInfo.isConnected()) {

            txtConnection.setText("🟢");
            txtConnection.setTextColor(android.graphics.Color.parseColor("#4CAF50"));

        } else {

            txtConnection.setText("🔴");
            txtConnection.setTextColor(android.graphics.Color.RED);
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (networkReceiver != null) {
            unregisterReceiver(networkReceiver);
        }
    }

}