package com.smartagri.connect;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.smartagri.connect.model.Alerts;
import java.util.List;

public class AlertAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_OK = 0;
    private static final int TYPE_DRY = 1;

    private List<Alerts> alertsList;

    public AlertAdapter(List<Alerts> alertsList) {
        this.alertsList = alertsList;
    }

    @Override
    public int getItemViewType(int position) {
        Alerts alert = alertsList.get(position);
        if ("OK".equalsIgnoreCase(alert.getAlerts())) {
            return TYPE_OK;
        } else if ("soil_too_dry".equalsIgnoreCase(alert.getAlerts())) {
            return TYPE_DRY;
        } else {
            return TYPE_OK;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_DRY) {
            View view = inflater.inflate(R.layout.list_item_red, parent, false);
            return new DryViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.list_item, parent, false);
            return new OkViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Alerts alerts = alertsList.get(position);
        String alertText = alerts.getAlerts();

        if (holder instanceof OkViewHolder) {

            if ("OK".equalsIgnoreCase(alertText)) {
                ((OkViewHolder) holder).alertTextView.setText("Data Sent Successfully");
            } else {
                ((OkViewHolder) holder).alertTextView.setText(alertText);
            }
            ((OkViewHolder) holder).dateTextView.setText(alerts.getDate());

        } else if (holder instanceof DryViewHolder) {

            if ("soil_too_dry".equalsIgnoreCase(alertText)) {
                ((DryViewHolder) holder).alertTextView.setText("Soil Is Too Dry");
            } else {
                ((DryViewHolder) holder).alertTextView.setText(alertText);
            }
            ((DryViewHolder) holder).dateTextView.setText(alerts.getDate());
        }
    }

    @Override
    public int getItemCount() {
        return alertsList.size();
    }


    public static class OkViewHolder extends RecyclerView.ViewHolder {
        TextView alertTextView, dateTextView;

        public OkViewHolder(@NonNull View itemView) {
            super(itemView);
            alertTextView = itemView.findViewById(R.id.alert_name);
            dateTextView = itemView.findViewById(R.id.date_tv);
        }
    }


    public static class DryViewHolder extends RecyclerView.ViewHolder {
        TextView alertTextView, dateTextView;

        public DryViewHolder(@NonNull View itemView) {
            super(itemView);
            alertTextView = itemView.findViewById(R.id.alert_name);
            dateTextView = itemView.findViewById(R.id.date_tv);
        }
    }
}
