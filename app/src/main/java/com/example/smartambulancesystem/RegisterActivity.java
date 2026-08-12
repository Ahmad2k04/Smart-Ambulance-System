package com.example.smartambulancesystem;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import android.view.View;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseUser;

import android.os.CountDownTimer;
public class RegisterActivity extends AppCompatActivity {

    EditText etName, etEmail, etPassword;
    Spinner spCity;
    RadioButton rbDriver, rbPolice;
    Button btnRegister;
    private CountDownTimer countDownTimer;
    private boolean isCooldown = false;

    DatabaseReference databaseReference;
    FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        spCity = findViewById(R.id.spCity);

        rbDriver = findViewById(R.id.rbDriver);
        rbPolice = findViewById(R.id.rbPolice);

        btnRegister = findViewById(R.id.btnRegister);

        databaseReference = FirebaseDatabase.getInstance().getReference("Users");
        mAuth = FirebaseAuth.getInstance();
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

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                cities
        );

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCity.setAdapter(adapter);
        rbPolice.setOnCheckedChangeListener((buttonView, isChecked) -> {

            if (isChecked) {
                spCity.setVisibility(View.VISIBLE);
            }

        });

        rbDriver.setOnCheckedChangeListener((buttonView, isChecked) -> {

            if (isChecked) {
                spCity.setVisibility(View.GONE);
            }

        });

        btnRegister.setOnClickListener(v -> {
            if (isCooldown) {
                Toast.makeText(RegisterActivity.this,
                        "Please wait 30 seconds",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            isCooldown = true;
            btnRegister.setEnabled(false);
            btnRegister.setText("Sending...");
            if (!isInternetAvailable()) {

                isCooldown = false;
                btnRegister.setEnabled(true);
                btnRegister.setText("Register");

                Toast.makeText(RegisterActivity.this,
                        "Please connect to the Internet",
                        Toast.LENGTH_LONG).show();
                return;
            }

            String name = etName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            String role = rbDriver.isChecked() ? "Driver" : "Police";

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {

                isCooldown = false;
                btnRegister.setEnabled(true);
                btnRegister.setText("Register");

                Toast.makeText(RegisterActivity.this,
                        "Fill all fields",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            String id = databaseReference.push().getKey();

            HashMap<String, Object> user = new HashMap<>();
            user.put("name", name);
            user.put("email", email);
            user.put("role", role);

            if (role.equals("Police")) {

                String city = spCity.getSelectedItem().toString();

                if (city.equals("Select City")) {

                    isCooldown = false;
                    btnRegister.setEnabled(true);
                    btnRegister.setText("Register");

                    Toast.makeText(RegisterActivity.this,
                            "Please select a city",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                user.put("city", city);
            }

            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {

                        if (task.isSuccessful()) {

                            databaseReference.child(id).setValue(user)
                                    .addOnSuccessListener(unused -> {

                                        FirebaseUser firebaseUser = mAuth.getCurrentUser();

                                        if (firebaseUser == null) {
                                            Toast.makeText(RegisterActivity.this,
                                                    "Registration completed, please login again.",
                                                    Toast.LENGTH_LONG).show();
                                            return;
                                        }

                                        firebaseUser.sendEmailVerification()
                                                .addOnSuccessListener(unused2 -> {

                                                    Toast.makeText(RegisterActivity.this,
                                                            "Registration successful.\nPlease verify your email before login.",
                                                            Toast.LENGTH_LONG).show();

                                                    startRegisterTimer();

                                                })
                                                .addOnFailureListener(e -> {

                                                    isCooldown = false;
                                                    btnRegister.setEnabled(true);
                                                    btnRegister.setText("Register");

                                                    Toast.makeText(RegisterActivity.this,
                                                            "Failed to send verification email: " + e.getMessage(),
                                                            Toast.LENGTH_LONG).show();
                                                });

                                    })
                                    .addOnFailureListener(e ->

                                            Toast.makeText(RegisterActivity.this,
                                                    "Failed to save user: " + e.getMessage(),
                                                    Toast.LENGTH_LONG).show()
                                    );

                        } else {

                            isCooldown = false;
                            btnRegister.setEnabled(true);
                            btnRegister.setText("Register");

                            Exception e = task.getException();

                            if (!isInternetAvailable()) {

                                Toast.makeText(RegisterActivity.this,
                                        "Please connect to the Internet",
                                        Toast.LENGTH_LONG).show();

                            } else {

                                Toast.makeText(RegisterActivity.this,
                                        "Registration failed. Please try again.",
                                        Toast.LENGTH_LONG).show();
                            }

                            if (e != null) {
                                e.printStackTrace();
                            }
                        }

                    });

        });
    }
    private boolean isInternetAvailable() {

        android.net.ConnectivityManager cm =
                (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        if (cm == null) return false;

        android.net.NetworkInfo networkInfo = cm.getActiveNetworkInfo();

        return networkInfo != null && networkInfo.isConnected();
    }
        private void startRegisterTimer() {

            countDownTimer = new CountDownTimer(30000, 1000) {

                @Override
                public void onTick(long millisUntilFinished) {

                    btnRegister.setText("Wait (" + (millisUntilFinished / 1000) + "s)");
                }

                @Override
                public void onFinish() {

                    isCooldown = false;
                    btnRegister.setEnabled(true);
                    btnRegister.setText("Register");

                    mAuth.signOut();

                    startActivity(new Intent(
                            RegisterActivity.this,
                            LoginActivity.class));

                    finish();
                }

            }.start();
        }

    }
