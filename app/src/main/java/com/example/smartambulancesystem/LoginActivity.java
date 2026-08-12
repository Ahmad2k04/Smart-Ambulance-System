package com.example.smartambulancesystem;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import android.content.SharedPreferences;

import com.google.firebase.database.*;

import android.os.CountDownTimer;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Random;

import okhttp3.*;

public class LoginActivity extends AppCompatActivity {

    EditText etPhone, etPassword, etOtp;
    Button btnSendOtp, btnVerifyOtp;
    RadioButton rbDriver, rbPolice;
    TextView txtGoRegister;
    TextView txtForgotPassword;

    String generatedOtp;
    String userRole = "";
    String userCity = "";

    private CountDownTimer countDownTimer;
    private boolean isOtpCooldown = false;


    DatabaseReference databaseReference;
    FirebaseAuth mAuth;

    // EMAILJS
    String SERVICE_ID = "service_ro4l61x";
    String TEMPLATE_ID = "template_4t6svue";
    String PUBLIC_KEY = "LJe8PbF4ht4ZyXDwE";

    OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        SharedPreferences sp = getSharedPreferences("ambulance", MODE_PRIVATE);

        boolean isLoggedIn = sp.getBoolean("isLoggedIn", false);

        if (isLoggedIn) {

            String role = sp.getString("role", "");

            if ("Driver".equalsIgnoreCase(role)) {

                startActivity(new Intent(LoginActivity.this,
                        HomeActivity.class));

            } else if ("Police".equalsIgnoreCase(role)) {

                startActivity(new Intent(LoginActivity.this,
                        PoliceHomeActivity.class));
            }

            finish();
            return;
        }

        etPhone = findViewById(R.id.etPhone);
        etPassword = findViewById(R.id.etPassword);
        txtForgotPassword = findViewById(R.id.txtForgotPassword);
        etOtp = findViewById(R.id.etOtp);

        btnSendOtp = findViewById(R.id.btnLogin);
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp);

        rbDriver = findViewById(R.id.rbDriver);
        rbPolice = findViewById(R.id.rbPolice);

        txtGoRegister = findViewById(R.id.txtGoRegister);

        databaseReference = FirebaseDatabase.getInstance().getReference("Users");
        mAuth = FirebaseAuth.getInstance();

        txtGoRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
        txtForgotPassword.setOnClickListener(v -> {
            if (!isInternetAvailable()) {

                Toast.makeText(LoginActivity.this,
                        "Please connect to the Internet",
                        Toast.LENGTH_LONG).show();
                return;
            }

            String email = etPhone.getText().toString().trim();

            if (email.isEmpty()) {
                Toast.makeText(LoginActivity.this,
                        "Enter your email first",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {

                        if (task.isSuccessful()) {

                            Toast.makeText(LoginActivity.this,
                                    "Password reset email sent. Check your email.",
                                    Toast.LENGTH_LONG).show();

                        } else {

                            Toast.makeText(LoginActivity.this,
                                    task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });

        });

        // SEND OTP
        btnSendOtp.setOnClickListener(v -> {
            if (isOtpCooldown) {
                Toast.makeText(LoginActivity.this,
                        "Please wait 30 seconds",
                        Toast.LENGTH_SHORT).show();
                return;
            }


            String email = etPhone.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(LoginActivity.this,
                        "Enter Email and Password",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isInternetAvailable()) {

                Toast.makeText(LoginActivity.this,
                        "Please connect to the Internet",
                        Toast.LENGTH_LONG).show();
                return;
            }

            isOtpCooldown = true;
            btnSendOtp.setEnabled(false);
            btnSendOtp.setText("Sending...");


            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(authTask -> {

                        if (!authTask.isSuccessful()) {

                            isOtpCooldown = false;
                            btnSendOtp.setEnabled(true);
                            btnSendOtp.setText("Send OTP");

                            Toast.makeText(LoginActivity.this,
                                    "Wrong email or password",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }


                        FirebaseUser firebaseUser = mAuth.getCurrentUser();


                        if (firebaseUser == null) {
                            return;
                        }


                        firebaseUser.reload().addOnCompleteListener(task -> {


                            FirebaseUser updatedUser = mAuth.getCurrentUser();


                            if (updatedUser == null) {
                                return;
                            }


                            if (!updatedUser.isEmailVerified()) {

                                Toast.makeText(LoginActivity.this,
                                        "Please verify your email first.",
                                        Toast.LENGTH_LONG).show();


                                return;
                            }


                            // NOW Firebase Auth exists, so Users read will work

                            databaseReference.addListenerForSingleValueEvent(
                                            new ValueEventListener() {

                                                @Override
                                                public void onDataChange(DataSnapshot ds) {

                                                    for (DataSnapshot userSnapshot : ds.getChildren()) {

                                                        String dbEmail = userSnapshot.child("email")
                                                                .getValue(String.class);

                                                        if (email.equalsIgnoreCase(dbEmail)) {

                                                            userRole = userSnapshot.child("role")
                                                                    .getValue(String.class);

                                                            userCity = userSnapshot.child("city")
                                                                    .getValue(String.class);

                                                            break;
                                                        }
                                                    }


                                                    // TEMPORARY: Skip EmailJS OTP until quota resets

//                                                    Toast.makeText(LoginActivity.this,
//                                                            "OTP temporarily disabled",
//                                                            Toast.LENGTH_SHORT).show();
//
//                                                    getSharedPreferences("ambulance", MODE_PRIVATE)
//                                                            .edit()
//                                                            .putBoolean("isLoggedIn", true)
//                                                            .putString("role", userRole)
//                                                            .putString("email", email)
//                                                            .putString("driverEmail", email)
//                                                            .putString("city", userCity)
//                                                            .apply();
//
//                                                    if ("Driver".equalsIgnoreCase(userRole)) {
//
//                                                        startActivity(new Intent(LoginActivity.this,
//                                                                HomeActivity.class));
//
//                                                    } else {
//
//                                                        startActivity(new Intent(LoginActivity.this,
//                                                                PoliceHomeActivity.class));
//                                                    }
//
//                                                    finish();

                                                    generatedOtp = String.valueOf(
                                                            new Random().nextInt(900000) + 100000
                                                    );

                                                    sendEmailOTP(email, generatedOtp);







                                                }


                                                @Override
                                                public void onCancelled(DatabaseError error) {

                                                    Toast.makeText(LoginActivity.this,
                                                            "Database error",
                                                            Toast.LENGTH_LONG).show();
                                                }
                                            });

                        });

                    });

        });

        // VERIFY OTP
        btnVerifyOtp.setOnClickListener(v -> {

            if (generatedOtp == null) {
                Toast.makeText(LoginActivity.this,
                        "Send OTP First",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            String otp = etOtp.getText().toString().trim();

            if (otp.equals(generatedOtp)) {

                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

                if (currentUser == null) {

                    Toast.makeText(LoginActivity.this,
                            "Firebase session expired. Login again.",
                            Toast.LENGTH_LONG).show();

                    return;
                }

                Toast.makeText(LoginActivity.this,
                        "Login Success",
                        Toast.LENGTH_SHORT).show();
                String email = etPhone.getText().toString().trim();
                String password = etPassword.getText().toString().trim();

                getSharedPreferences("ambulance", MODE_PRIVATE)
                        .edit()
                        .putBoolean("isLoggedIn", true)
                        .putString("role", userRole)
                        .putString("email", email)
                        .putString("driverEmail", email)
                        .putString("city", userCity)
                        .apply();

                if ("Driver".equalsIgnoreCase(userRole)) {

                    startActivity(new Intent(LoginActivity.this,
                            HomeActivity.class));

                } else {

                    startActivity(new Intent(LoginActivity.this,
                            PoliceHomeActivity.class));
                }

                finish();

            } else {

                Toast.makeText(LoginActivity.this,
                        "Invalid OTP",
                        Toast.LENGTH_SHORT).show();
            }

        });

    }
 // end of onCreate()

private void startOtpTimer() {

    isOtpCooldown = true;
    btnSendOtp.setEnabled(false);

    countDownTimer = new CountDownTimer(30000, 1000) {

        @Override
        public void onTick(long millisUntilFinished) {
            btnSendOtp.setText("Send OTP (" + (millisUntilFinished / 1000) + "s)");
        }

        @Override
        public void onFinish() {
            isOtpCooldown = false;
            btnSendOtp.setEnabled(true);
            btnSendOtp.setText("Send OTP");
        }

    }.start();
}


    private void sendEmailOTP(String email, String otp) {


        try {

            JSONObject json = new JSONObject();
            json.put("service_id", SERVICE_ID);
            json.put("template_id", TEMPLATE_ID);
            json.put("user_id", PUBLIC_KEY);

            JSONObject params = new JSONObject();
            params.put("to_email", email);
            params.put("otp", otp);

            json.put("template_params", params);

            RequestBody body = RequestBody.create(
                    json.toString(),
                    MediaType.get("application/json")
            );

            Request request = new Request.Builder()
                    .url("https://api.emailjs.com/api/v1.0/email/send")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {

                @Override
                public void onFailure(Call call, IOException e) {

                    runOnUiThread(() -> {

                        isOtpCooldown = false;
                        btnSendOtp.setEnabled(true);
                        btnSendOtp.setText("Send OTP");

                        if (!isInternetAvailable()) {

                            Toast.makeText(LoginActivity.this,
                                    "Please connect to the Internet",
                                    Toast.LENGTH_LONG).show();

                        } else {

                            Toast.makeText(LoginActivity.this,
                                    "Failed to send OTP. Please try again.",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {

                    runOnUiThread(() -> {
                        Toast.makeText(LoginActivity.this,
                                "OTP Sent Successfully",
                                Toast.LENGTH_SHORT).show();

                        startOtpTimer();
                    });
                }

            });

        } catch (Exception e) {

            Toast.makeText(this,
                    e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }
    private boolean isInternetAvailable() {

        android.net.ConnectivityManager cm =
                (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        if (cm == null) return false;

        android.net.NetworkInfo networkInfo = cm.getActiveNetworkInfo();

        return networkInfo != null && networkInfo.isConnected();
    }
}