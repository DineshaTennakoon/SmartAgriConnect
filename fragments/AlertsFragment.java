package com.smartagri.connect.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.smartagri.connect.AlertAdapter;
import com.smartagri.connect.BaseFragment;
import com.smartagri.connect.R;
import com.smartagri.connect.model.Alerts;

import java.util.ArrayList;
import java.util.List;

public class AlertsFragment extends BaseFragment {

    private RecyclerView recyclerView;
    private AlertAdapter adapter;
    private List<Alerts> alertsList;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_alerts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        alertsList = new ArrayList<>();

        loadAlertsFromFirestore();
        setupRecyclerView(view);

    }

    private void loadAlertsFromFirestore() {
        FirebaseFirestore.getInstance()
                .collection("sensor_data")
                .orderBy("date_time", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        Log.d("AlertsFragment", "Alerts loaded from Firestore");
                        if(task.isSuccessful()) {
                            alertsList.clear();
                            for(QueryDocumentSnapshot document : task.getResult()) {
                                String alertArray = document.getString("alerts");
                                Alerts alert = new Alerts(alertArray, document.getString("date_time"));
                                alertsList.add(alert);

                            }
                            Log.d("AlertsFragment", "Alerts " + alertsList.size());
                            adapter.notifyDataSetChanged();
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e("AlertsFragment", "Error loading alerts from Firestore", e);
                    }
                });
    }

    private void setupRecyclerView(View view) {
        recyclerView = view.findViewById(R.id.alertsRecycleview);
        adapter = new AlertAdapter(alertsList);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

    }
}