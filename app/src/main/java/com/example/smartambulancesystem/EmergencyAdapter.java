package com.example.smartambulancesystem;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;

import com.google.firebase.database.DatabaseReference;

public class EmergencyAdapter extends RecyclerView.Adapter<EmergencyAdapter.ViewHolder> {

    Context context;
    ArrayList<Emergency> list;

    public EmergencyAdapter(Context context, ArrayList<Emergency> list) {
        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_emergency, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        Emergency emergency = list.get(position);

        holder.txtHospital.setText("🏥 Hospital : " + emergency.hospital);
        holder.txtStatus.setText("🚨 Status : " + emergency.status);
        holder.txtTime.setText("🕒 Time : " + emergency.time);
        holder.txtLocation.setText(
                "📍 " + emergency.latitude + ", " + emergency.longitude
        );
        holder.txtCity.setText(
                "🏙 City : " + emergency.city
        );

        holder.txtEta.setText(
                "⏱ ETA : " + emergency.eta
        );

        if (emergency.eta != null) {

            try {

                String etaText = emergency.eta.replace(" minutes", "");

                int minutes = Integer.parseInt(etaText);


                if (minutes <= 5) {

                    holder.txtTrafficAlert.setText(
                            "🚨 HIGH PRIORITY\nAmbulance arriving soon\nCLEAR ROAD NOW"
                    );

                } else {

                    holder.txtTrafficAlert.setText(
                            "🚑 Ambulance approaching\nPrepare route clearance"
                    );

                }

            } catch (Exception e) {

                holder.txtTrafficAlert.setText(
                        "🚨 Clear route - Ambulance approaching"
                );
            }

        } else {

            holder.txtTrafficAlert.setText(
                    "🚨 Clear route - Ambulance approaching"
            );
        }

        // View on Map
        holder.btnViewMap.setOnClickListener(v -> {

            Intent intent = new Intent(context, LiveTrackingActivity.class);

            intent.putExtra("emergencyId", emergency.id);

            context.startActivity(intent);
        });
        // Complete Emergency
        holder.btnComplete.setOnClickListener(v -> {

            DatabaseReference emergencyRef = FirebaseDatabase.getInstance()
                    .getReference("emergencies")
                    .child(emergency.id);

            emergencyRef.child("arrived").get().addOnSuccessListener(snapshot -> {

                Boolean arrived = snapshot.getValue(Boolean.class);

                if (arrived != null && arrived) {

                    emergencyRef.child("status").setValue("Completed");

                    Toast.makeText(context,
                            "Emergency Completed ✅",
                            Toast.LENGTH_SHORT).show();

                } else {

                    Toast.makeText(context,
                            "Ambulance has not reached the hospital yet.",
                            Toast.LENGTH_LONG).show();
                }

            }).addOnFailureListener(e -> {

                Toast.makeText(context,
                        "Unable to verify ambulance location.",
                        Toast.LENGTH_SHORT).show();

            });

        });

        // Change button based on status
        if ("Completed".equalsIgnoreCase(emergency.status)) {
            holder.btnComplete.setEnabled(false);
            holder.btnComplete.setText("Completed");
        } else {
            holder.btnComplete.setEnabled(true);
            holder.btnComplete.setText("Complete");
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView txtHospital, txtStatus, txtTime, txtLocation;
        TextView txtCity, txtEta, txtTrafficAlert;
        Button btnViewMap, btnComplete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            txtHospital = itemView.findViewById(R.id.txtHospital);
            txtStatus = itemView.findViewById(R.id.txtStatus);
            txtTime = itemView.findViewById(R.id.txtTime);
            txtLocation = itemView.findViewById(R.id.txtLocation);
            txtCity = itemView.findViewById(R.id.txtCity);
            txtEta = itemView.findViewById(R.id.txtEta);
            txtTrafficAlert = itemView.findViewById(R.id.txtTrafficAlert);

            btnViewMap = itemView.findViewById(R.id.btnViewMap);
            btnComplete = itemView.findViewById(R.id.btnComplete);
        }
    }
}