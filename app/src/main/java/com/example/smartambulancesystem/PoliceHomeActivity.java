package com.example.smartambulancesystem;

import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import android.widget.Button;
import android.content.Intent;

import java.util.ArrayList;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
;

import android.content.BroadcastReceiver;
import android.content.Context;

import android.content.IntentFilter;

public class PoliceHomeActivity extends AppCompatActivity {
    Button btnLogout;

    RecyclerView recyclerEmergency;

    TextView txtActive, txtCompleted, txtTotal;

    ArrayList<Emergency> emergencyList;
    EmergencyAdapter adapter;
    DatabaseReference emergencyRef;

    private int previousCount = 0;
    private long backPressedTime;

    private String policeCity;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_police_home);


        btnLogout = findViewById(R.id.btnLogout);

        btnLogout.setOnClickListener(v -> {

            getSharedPreferences("ambulance", MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply();

            Intent intent = new Intent(PoliceHomeActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);

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
                                    PoliceHomeActivity.this,
                                    "Press back again to exit",
                                    Toast.LENGTH_SHORT
                            ).show();

                            backPressedTime[0] = System.currentTimeMillis();
                        }
                    }
                });

        txtActive = findViewById(R.id.txtActive);
        txtCompleted = findViewById(R.id.txtCompleted);
        txtTotal = findViewById(R.id.txtTotal);

        recyclerEmergency = findViewById(R.id.recyclerEmergency);
        recyclerEmergency.setLayoutManager(new LinearLayoutManager(this));

        emergencyList = new ArrayList<>();
        adapter = new EmergencyAdapter(this, emergencyList);
        recyclerEmergency.setAdapter(adapter);
        policeCity = getSharedPreferences("ambulance", MODE_PRIVATE)
                .getString("city", "");

        emergencyRef = FirebaseDatabase.getInstance().getReference("emergencies");

        emergencyRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {

                emergencyList.clear();

                int active = 0;
                int completed = 0;
                int total = 0;

                if (snapshot.exists()) {

                    for (DataSnapshot dataSnapshot : snapshot.getChildren()) {

                        Emergency emergency = dataSnapshot.getValue(Emergency.class);

                        if (emergency != null) {

                            emergency.id = dataSnapshot.getKey();

                            if (policeCity.equalsIgnoreCase(emergency.city)) {
                                total++;
                            }

                            if ("COMPLETED".equalsIgnoreCase(emergency.status)) {

                                if (policeCity.equalsIgnoreCase(emergency.city)) {
                                    completed++;
                                }

                            } else {

                                if (policeCity.equalsIgnoreCase(emergency.city)) {
                                    active++;
                                    emergencyList.add(0, emergency);
                                }
                            }
                        }
                    }
                }

                txtActive.setText("🟢 Active\n" + active);
                txtCompleted.setText("✅ Completed\n" + completed);
                txtTotal.setText("📊 Total\n" + total);

                // Play notification only when a NEW emergency is added
                if (previousCount != 0 && total > previousCount) {

                    Toast.makeText(
                            PoliceHomeActivity.this,
                            "🚨 New Emergency Received!",
                            Toast.LENGTH_LONG
                    ).show();

                    try {
                        Uri notification = RingtoneManager.getDefaultUri(
                                RingtoneManager.TYPE_NOTIFICATION);

                        Ringtone ringtone = RingtoneManager.getRingtone(
                                getApplicationContext(),
                                notification);

                        if (ringtone != null) {
                            ringtone.play();
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                previousCount = total;

                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError error) {

                Toast.makeText(
                        PoliceHomeActivity.this,
                        "Database Error",
                        Toast.LENGTH_SHORT
                ).show();
            }

        });

    }



}