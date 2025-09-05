package com.smartagri.connect.fragments;

import static android.content.Context.MODE_PRIVATE;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.smartagri.connect.BaseFragment;
import com.smartagri.connect.R;
import com.smartagri.connect.helper.DatabaseHelper;
import com.smartagri.connect.model.SensorData;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DashboardFragment extends BaseFragment {

    private static final String TAG = "LogCatSmartAgri";
    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1001;
    private static final int REQUEST_ENABLE_BT = 1002;

    private DatabaseHelper dbHelper;
    private boolean isReceivingHistory = false;
    private int expectedHistoryCount = 0;
    private int receivedHistoryCount = 0;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private boolean isConnected = false;
    private String connectedDeviceName = "";
    private SharedPreferences prefs;
    private TextView txtTemperature, txtHumidity, txtSoil, txtBattery;

    private List<SensorData> historyDataList = new ArrayList<>();
    private ImageButton bluetoothButton;

    public DashboardFragment() {
    }

    public static DashboardFragment newInstance(String param1, String param2) {
        DashboardFragment fragment = new DashboardFragment();
        Bundle args = new Bundle();
        args.putString("param1", param1);
        args.putString("param2", param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dbHelper = new DatabaseHelper(requireContext());
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        prefs = requireContext().getSharedPreferences("bluetooth_prefs", MODE_PRIVATE);
        dbHelper = new DatabaseHelper(requireContext());

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        bluetoothButton = view.findViewById(R.id.btn_bluetooth);

        txtTemperature = view.findViewById(R.id.txt_temperature);
        txtHumidity = view.findViewById(R.id.txt_humidity);
        txtSoil = view.findViewById(R.id.txt_soil);
        txtBattery = view.findViewById(R.id.txt_battery);

        bluetoothButton.setOnClickListener(v -> onBluetoothButtonClick());

        updateBluetoothUI(false);

        return view;
    }

    private void onBluetoothButtonClick() {
        Log.d(TAG, "Dashboard BT button clicked");

        if (isConnected) {
            showDisconnectDialog();
        } else {
            startBluetoothConnection();
        }
    }

    private void startBluetoothConnection() {
        if (bluetoothAdapter == null) {
            showToast("Bluetooth not supported");
            return;
        }

        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }

        showPairedDevices();
    }

    private boolean hasBluetoothPermissions() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBluetoothPermissions() {
        String[] permissions = {
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
        };
        requestPermissions(permissions, REQUEST_BLUETOOTH_PERMISSIONS);
    }

    private void showPairedDevices() {
        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions();
            return;
        }

        try {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

            if (pairedDevices.isEmpty()) {
                showToast("No paired devices found.\n\nGo to Settings > Bluetooth and pair your FARM device first.");
                openBluetoothSettings();
                return;
            }

            BluetoothDevice[] deviceArray = pairedDevices.toArray(new BluetoothDevice[0]);
            String[] deviceNames = new String[deviceArray.length];

            for (int i = 0; i < deviceArray.length; i++) {
                String name = deviceArray[i].getName();
                if (name != null && name.startsWith("FARM_")) {
                    deviceNames[i] = name + " (Farm Device)";
                } else if (name != null) {
                    deviceNames[i] = name;
                } else {
                    deviceNames[i] = "Unknown (" + deviceArray[i].getAddress() + ")";
                }
            }

            new AlertDialog.Builder(requireContext())
                    .setTitle("Select Farm Device")
                    .setIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .setItems(deviceNames, (dialog, which) -> {
                        BluetoothDevice selectedDevice = deviceArray[which];
                        connectToDevice(selectedDevice);
                    })
                    .setNegativeButton("Cancel", null)
                    .setNeutralButton("Pair New Device", (dialog, which) -> openBluetoothSettings())
                    .show();

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception: " + e.getMessage());
            showToast("Permission denied");
        }
    }

    private void openBluetoothSettings() {
        Intent intent = new Intent();
        intent.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
        startActivity(intent);

        new AlertDialog.Builder(requireContext())
                .setTitle("Pair Your Device")
                .setMessage("1. Tap 'Pair new device'\n2. Select 'FARM_xxxxxxxx'\n3. Return and try again")
                .setPositiveButton("OK", null)
                .show();
    }

    private void connectToDevice(BluetoothDevice device) {
        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions();
            return;
        }

        ProgressDialog dialog = new ProgressDialog(requireContext());
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

            return;
        }
        dialog.setMessage("Connecting to " + device.getName() + "...");
        dialog.setCancelable(false);
        dialog.show();

        new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

                    return;
                }
                Log.d(TAG, "Connecting to: " + device.getName());

                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothAdapter.cancelDiscovery();
                }

                // Try standard connection first
                bluetoothSocket = device.createRfcommSocketToServiceRecord(BT_UUID);

                try {
                    bluetoothSocket.connect();
                    Log.d(TAG, "Standard connection successful");
                } catch (IOException e) {
                    Log.w(TAG, "Standard failed, trying fallback...");
                    try {
                        bluetoothSocket.close();
                    } catch (IOException ignored) {}

                    // Fallback method
                    try {
                        java.lang.reflect.Method m = device.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
                        bluetoothSocket = (BluetoothSocket) m.invoke(device, 1);
                        bluetoothSocket.connect();
                        Log.d(TAG, "Fallback connection successful");
                    } catch (Exception e2) {
                        throw new IOException("Both connection methods failed");
                    }
                }

                outputStream = bluetoothSocket.getOutputStream();
                inputStream = bluetoothSocket.getInputStream();
                isConnected = true;
                connectedDeviceName = device.getName();

                prefs.edit().putString("last_device_address", device.getAddress()).apply();

                requireActivity().runOnUiThread(() -> {
                    dialog.dismiss();
                    showToast("Connected to " + device.getName());
                    updateBluetoothUI(true);
                    authenticateDevice();
                });

            } catch (IOException e) {
                Log.e(TAG, "Connection failed: " + e.getMessage());
                requireActivity().runOnUiThread(() -> {
                    dialog.dismiss();
                    showToast("Connection failed: " + e.getMessage());
                    resetConnection();
                });
            }
        }).start();
    }

    private void authenticateDevice() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Enter PIN");
        builder.setMessage("Enter device PIN:");

        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText("1234");
        builder.setView(input);

        builder.setPositiveButton("Connect", (dialog, which) -> {
            String pin = input.getText().toString().trim();
            if (pin.isEmpty()) pin = "1234";
            sendPin(pin);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> disconnectDevice());
        builder.setCancelable(false);
        builder.show();
    }

    private void sendPin(String pin) {
        ProgressDialog dialog = new ProgressDialog(requireContext());
        dialog.setMessage("Authenticating...");
        dialog.setCancelable(false);
        dialog.show();

        new Thread(() -> {
            try {
                outputStream.write(("PIN:" + pin + "\n").getBytes());
                outputStream.flush();

                byte[] buffer = new byte[1024];
                int bytes = inputStream.read(buffer);
                String response = new String(buffer, 0, bytes).trim();

                requireActivity().runOnUiThread(() -> {
                    dialog.dismiss();
                    if (response.equals("AUTH:OK")) {
                        Log.d(TAG, "Authentication successful");
                        showToast("Connected! Getting sensor data...");
                        requestSensorData();
                    } else {
                        Log.e(TAG, "Authentication failed: " + response);
                        showToast("Wrong PIN");
                        disconnectDevice();
                    }
                });

            } catch (IOException e) {
                Log.e(TAG, "Auth error: " + e.getMessage());
                requireActivity().runOnUiThread(() -> {
                    dialog.dismiss();
                    showToast("Authentication error");
                    disconnectDevice();
                });
            }
        }).start();
    }

    private void requestSensorData() {
        if (!isConnected) return;

        requestHistoryData();

        new Thread(() -> {
            try {
                while (isConnected) {
                    if (!isReceivingHistory) {
                        outputStream.write("LIVE\n".getBytes());
                        outputStream.flush();
                    }

                    byte[] buffer = new byte[1024];
                    int bytes = inputStream.read(buffer);
                    String response = new String(buffer, 0, bytes).trim();


                    if (response.startsWith("LIVE:")) {
                        String data = response.substring(5);
                        parseSensorData(data);
                    } else if (response.startsWith("HISTORY_START:") ||
                            response.startsWith("DATA:") ||
                            response.equals("HISTORY_END")) {
                        handleHistoryData(response);

                        // After history ends, send clear command
                        if (response.equals("HISTORY_END")) {
                            outputStream.write("CLEAR\n".getBytes());
                            outputStream.flush();
                        }
                    }

                    Thread.sleep(5000);
                }
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Error getting sensor data: " + e.getMessage());
                resetConnection();
                requireActivity().runOnUiThread(() -> updateBluetoothUI(false));
            }
        }).start();
    }
    private void requestHistoryData() {
        if (!isConnected) return;

        new Thread(() -> {
            try {
                outputStream.write("HISTORY\n".getBytes());
                outputStream.flush();
                Log.d(TAG, "Requested history data from ESP32");
            } catch (IOException e) {
                Log.e(TAG, "Error requesting history: " + e.getMessage());
            }
        }).start();
    }


    private void handleHistoryData(String response) {
        Log.d(TAG, "Raw ESP32 response: " + response);

        if (response.startsWith("HISTORY_START:")) {
            isReceivingHistory = true;
            expectedHistoryCount = Integer.parseInt(response.substring(14));
            receivedHistoryCount = 0;
            historyDataList.clear();

            Log.d(TAG, "========== ESP32 HISTORY DOWNLOAD START ==========");
            Log.d(TAG, "ESP32 says it has: " + expectedHistoryCount + " history records");
            Log.d(TAG, "Starting to receive actual ESP32 stored data...");
            Log.d(TAG, "=================================================");

            requireActivity().runOnUiThread(() -> {
                showToast("ESP32 sending " + expectedHistoryCount + " stored records...");
            });

        } else if (response.startsWith("DATA:")) {
            String rawEsp32Data = response.substring(5);
            receivedHistoryCount++;


            Log.d(TAG, "ESP32 Record #" + receivedHistoryCount + " RAW: " + rawEsp32Data);


            SensorData historyRecord = parseRealEsp32HistoryRecord(rawEsp32Data, receivedHistoryCount);
            if (historyRecord != null) {
                historyDataList.add(historyRecord);
                Log.d(TAG, "✓ ESP32 Record #" + receivedHistoryCount + " successfully parsed and stored");
            } else {
                Log.e(TAG, "✗ ESP32 Record #" + receivedHistoryCount + " FAILED to parse");
            }


            if (receivedHistoryCount % 5 == 0 || receivedHistoryCount == expectedHistoryCount) {
                Log.d(TAG, "ESP32 History Progress: " + receivedHistoryCount + "/" + expectedHistoryCount +
                        " (" + String.format("%.1f", (receivedHistoryCount * 100.0 / expectedHistoryCount)) + "%)");
            }

        } else if (response.equals("HISTORY_END")) {
            isReceivingHistory = false;
            Log.d(TAG, "=========== ESP32 HISTORY DOWNLOAD COMPLETE ===========");
            Log.d(TAG, "ESP32 finished sending. Total received: " + receivedHistoryCount + "/" + expectedHistoryCount);

            printCompleteEsp32HistoryArray();

            dbHelper.logDatabaseStatus();


            requireActivity().runOnUiThread(() -> {
                showToast("ESP32 history complete! Got " + receivedHistoryCount + " records. Ready for Firebase upload.");
            });

            uploadAllStoredDataToFirebase();
        } else {
            Log.w(TAG, "Unknown ESP32 history response: " + response);
        }
    }

    private SensorData parseRealEsp32HistoryRecord(String rawData, int recordNumber) {
        try {
            Log.d(TAG, "  → Parsing ESP32 record #" + recordNumber + ": " + rawData);

            String[] parts = rawData.split(",");
            Log.d(TAG, "  → Split into " + parts.length + " parts: " + java.util.Arrays.toString(parts));

            if (parts.length >= 6) {
                long timestamp = Long.parseLong(parts[0]);
                float temperature = Float.parseFloat(parts[1]);
                float humidity = Float.parseFloat(parts[2]);
                float soil = Float.parseFloat(parts[3]);
                float batteryVoltage = Float.parseFloat(parts[4]);
                int battery = Integer.parseInt(parts[5]);
                String alerts = parts.length > 6 ? parts[6] : "OK";

                long dbId = dbHelper.insertSensorData(timestamp, temperature, humidity, soil, battery, alerts);

                SensorData sensorData = new SensorData(timestamp, temperature, humidity, soil, battery, alerts);
                sensorData.id = (int) dbId;

                String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));
                Log.d(TAG, "  ✓ ESP32 Data Parsed:");
                Log.d(TAG, "    └─ Timestamp: " + timestamp + " (" + dateTime + ")");
                Log.d(TAG, "    └─ Temperature: " + temperature + "°C");
                Log.d(TAG, "    └─ Humidity: " + humidity + "%");
                Log.d(TAG, "    └─ Soil: " + soil + "%");
                Log.d(TAG, "    └─ Battery Voltage: " + batteryVoltage + "V");
                Log.d(TAG, "    └─ Battery Percentage: " + battery + "%");
                Log.d(TAG, "    └─ Alerts: " + alerts);
                Log.d(TAG, "    └─ Stored in SQLite with ID: " + dbId);

                return sensorData;
            } else {
                Log.e(TAG, "  ✗ ESP32 data has insufficient parts. Expected >= 6, got " + parts.length);
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "  ✗ Number parsing error in ESP32 record #" + recordNumber + ": " + e.getMessage());
            Log.e(TAG, "    Raw data: " + rawData);
        } catch (Exception e) {
            Log.e(TAG, "  ✗ General parsing error in ESP32 record #" + recordNumber + ": " + e.getMessage());
            Log.e(TAG, "    Raw data: " + rawData);
        }
        return null;
    }

    private void printCompleteEsp32HistoryArray() {


        for (int i = 0; i < historyDataList.size(); i++) {
            SensorData data = historyDataList.get(i);
            String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(data.timestamp));

            Log.d(TAG, String.format("ESP32[%03d] %s | T:%.1f°C H:%.1f%% S:%.1f%% B:%d%% %s (SQLite ID:%d)",
                    i + 1,
                    dateTime,
                    data.temperature,
                    data.humidity,
                    data.soil,
                    data.battery,
                    data.alerts.equals("OK") ? "" : "⚠️ " + data.alerts,
                    data.id
            ));
        }



        if (!historyDataList.isEmpty()) {
            printEsp32HistorySummary();
        }



    }

    private void printEsp32HistorySummary() {
        if (historyDataList.isEmpty()) return;

        float avgTemp = 0, avgHumidity = 0, avgSoil = 0, avgBattery = 0;
        float minTemp = Float.MAX_VALUE, maxTemp = Float.MIN_VALUE;
        float minHumidity = Float.MAX_VALUE, maxHumidity = Float.MIN_VALUE;
        float minSoil = Float.MAX_VALUE, maxSoil = Float.MIN_VALUE;
        int minBattery = Integer.MAX_VALUE, maxBattery = Integer.MIN_VALUE;
        int alertCount = 0;

        for (SensorData data : historyDataList) {
            avgTemp += data.temperature;
            avgHumidity += data.humidity;
            avgSoil += data.soil;
            avgBattery += data.battery;

            minTemp = Math.min(minTemp, data.temperature);
            maxTemp = Math.max(maxTemp, data.temperature);
            minHumidity = Math.min(minHumidity, data.humidity);
            maxHumidity = Math.max(maxHumidity, data.humidity);
            minSoil = Math.min(minSoil, data.soil);
            maxSoil = Math.max(maxSoil, data.soil);
            minBattery = Math.min(minBattery, data.battery);
            maxBattery = Math.max(maxBattery, data.battery);

            if (!data.alerts.equals("OK")) {
                alertCount++;
            }
        }

        int size = historyDataList.size();
        avgTemp /= size;
        avgHumidity /= size;
        avgSoil /= size;
        avgBattery /= size;


        Log.d(TAG, String.format("Temperature: %.1f°C (%.1f - %.1f)", avgTemp, minTemp, maxTemp));
        Log.d(TAG, String.format("Humidity: %.1f%% (%.1f - %.1f)", avgHumidity, minHumidity, maxHumidity));
        Log.d(TAG, String.format("Soil Moisture: %.1f%% (%.1f - %.1f)", avgSoil, minSoil, maxSoil));
        Log.d(TAG, String.format("Battery: %.1f%% (%d - %d)", avgBattery, minBattery, maxBattery));
        Log.d(TAG, "Records with alerts: " + alertCount + "/" + size);

        if (size > 1) {
            List<SensorData> sortedData = new ArrayList<>(historyDataList);
            sortedData.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));

            long startTime = sortedData.get(0).timestamp;
            long endTime = sortedData.get(size - 1).timestamp;
            String startDate = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(startTime));
            String endDate = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(endTime));
            long durationHours = (endTime - startTime) / (1000 * 60 * 60); // hours

            Log.d(TAG, "ESP32 Time Range: " + startDate + " to " + endDate + " (" + durationHours + " hours)");
            Log.d(TAG, "Data collection interval: ~" + (durationHours / (size - 1)) + " hours between readings");
        }

    }

    private SensorData parseHistoryRecord(String data) {
        try {
            String[] parts = data.split(",");
            if (parts.length >= 6) {
                long timestamp = Long.parseLong(parts[0]);
                float temperature = Float.parseFloat(parts[1]);
                float humidity = Float.parseFloat(parts[2]);
                float soil = Float.parseFloat(parts[3]);
                float batteryVoltage = Float.parseFloat(parts[4]);
                int battery = Integer.parseInt(parts[5]);
                String alerts = parts.length > 6 ? parts[6] : "OK";

                SensorData sensorData = new SensorData(timestamp, temperature, humidity, soil, battery, alerts);

                long recordId = dbHelper.insertSensorData(timestamp, temperature, humidity, soil, battery, alerts);
                sensorData.id = Math.toIntExact(recordId);

                String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));
                Log.d(TAG, "  └─ Time: " + dateTime + " | Temp: " + temperature + "°C | Humidity: " + humidity + "% | Soil: " + soil + "% | Battery: " + battery + "%" + (alerts.equals("OK") ? "" : " | Alerts: " + alerts));
                Log.d(TAG, "  └─ Stored in SQLite with ID: " + recordId + " (marked as unuploaded)");

                if (receivedHistoryCount % 10 == 0) {
                    Log.d(TAG, "History progress: " + receivedHistoryCount + "/" + expectedHistoryCount + " (" + String.format("%.1f", (receivedHistoryCount * 100.0 / expectedHistoryCount)) + "%)");
                }

                return sensorData;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing history record #" + receivedHistoryCount + ": " + e.getMessage() + " | Raw data: " + data);
        }
        return null;
    }

    // 5. Add method to print summary statistics
    private void printHistorySummary() {
        if (historyDataList.isEmpty()) return;

        float avgTemp = 0, avgHumidity = 0, avgSoil = 0, avgBattery = 0;
        float minTemp = Float.MAX_VALUE, maxTemp = Float.MIN_VALUE;
        float minHumidity = Float.MAX_VALUE, maxHumidity = Float.MIN_VALUE;
        float minSoil = Float.MAX_VALUE, maxSoil = Float.MIN_VALUE;
        int minBattery = Integer.MAX_VALUE, maxBattery = Integer.MIN_VALUE;
        int alertCount = 0;

        for (SensorData data : historyDataList) {
            avgTemp += data.temperature;
            avgHumidity += data.humidity;
            avgSoil += data.soil;
            avgBattery += data.battery;

            minTemp = Math.min(minTemp, data.temperature);
            maxTemp = Math.max(maxTemp, data.temperature);
            minHumidity = Math.min(minHumidity, data.humidity);
            maxHumidity = Math.max(maxHumidity, data.humidity);
            minSoil = Math.min(minSoil, data.soil);
            maxSoil = Math.max(maxSoil, data.soil);
            minBattery = Math.min(minBattery, data.battery);
            maxBattery = Math.max(maxBattery, data.battery);

            if (!data.alerts.equals("OK")) {
                alertCount++;
            }
        }

        int size = historyDataList.size();
        avgTemp /= size;
        avgHumidity /= size;
        avgSoil /= size;
        avgBattery /= size;

        Log.d(TAG, "======================== SUMMARY ========================");
        Log.d(TAG, String.format("Temperature: %.1f°C (%.1f - %.1f)", avgTemp, minTemp, maxTemp));
        Log.d(TAG, String.format("Humidity: %.1f%% (%.1f - %.1f)", avgHumidity, minHumidity, maxHumidity));
        Log.d(TAG, String.format("Soil Moisture: %.1f%% (%.1f - %.1f)", avgSoil, minSoil, maxSoil));
        Log.d(TAG, String.format("Battery: %.1f%% (%d - %d)", avgBattery, minBattery, maxBattery));
        Log.d(TAG, "Records with alerts: " + alertCount + "/" + size);

        // Time range
        if (size > 1) {
            long startTime = historyDataList.get(0).timestamp;
            long endTime = historyDataList.get(size - 1).timestamp;
            String startDate = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(startTime));
            String endDate = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(endTime));
            long duration = (endTime - startTime) / (1000 * 60); // minutes

            Log.d(TAG, "Time Range: " + startDate + " to " + endDate + " (" + duration + " minutes)");
        }
    }

    private void uploadSingleRecordToFirebase(SensorData data) {
        new Thread(() -> {
            if (uploadToFirebase(data)) {
                Log.d(TAG, "Live data uploaded to Firebase successfully");
            } else {
                Log.e(TAG, "Failed to upload live data to Firebase");
            }
        }).start();
    }

    private void uploadAllStoredDataToFirebase() {
        new Thread(() -> {
            List<SensorData> unuploadedData = dbHelper.getUnuploadedData();

            Log.d(TAG, "========== FIREBASE UPLOAD START ==========");
            Log.d(TAG, "Total unuploaded records found: " + unuploadedData.size());
            Log.d(TAG, "===========================================");

            int uploadCount = 0;

            for (SensorData data : unuploadedData) {
                if (uploadToFirebase(data)) {
                    dbHelper.markAsUploaded(data.id);
                    uploadCount++;

                    Log.d(TAG, "Uploaded record #" + uploadCount + " (ID: " + data.id + ") - " +
                            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(data.timestamp)));

                    if (uploadCount % 20 == 0) {
                        int finalUploadCount = uploadCount;
                        requireActivity().runOnUiThread(() -> {
                            showToast("Uploaded " + finalUploadCount + "/" + unuploadedData.size() + " records");
                        });
                    }
                } else {
                    Log.e(TAG, "Failed to upload record ID: " + data.id);
                }
            }

            Log.d(TAG, "Successfully uploaded: " + uploadCount + "/" + unuploadedData.size() + " records");

            int finalUploadCount1 = uploadCount;
            requireActivity().runOnUiThread(() -> {
                showToast("Upload complete! " + finalUploadCount1 + " records saved to cloud");
            });

            Log.d(TAG, "Firebase upload complete: " + uploadCount + "/" + unuploadedData.size() + " records uploaded");
        }).start();
    }
    private boolean uploadToFirebase(SensorData data) {
        try {
            Log.d(TAG, "  → Preparing Firebase document for record ID: " + data.id);

            Date date = new Date(data.timestamp);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String dateTimeString = sdf.format(date);



            // Create document data
            Map<String, Object> sensorDocument = new HashMap<>();
            sensorDocument.put("alerts", data.alerts);
            sensorDocument.put("battery", data.battery);
            sensorDocument.put("date_time", dateTimeString);
            sensorDocument.put("humidity", data.humidity);
            sensorDocument.put("temperature", data.temperature);
            sensorDocument.put("soil", data.soil);
            sensorDocument.put("sqlite_id", data.id); // Include SQLite ID for reference
            sensorDocument.put("id", UUID.randomUUID().toString());

            Log.d(TAG, "  → Document data: " + sensorDocument.toString());

            // Upload to Firebase
            FirebaseFirestore.getInstance()
                    .collection("sensor_data")
                    .add(sensorDocument)
                    .addOnSuccessListener(documentReference -> {
                        Log.d(TAG, "  ✓ Firebase upload success! Document ID: " + documentReference.getId());
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "  ✗ Firebase upload failed: " + e.getMessage());
                    });

            Log.d(TAG, "  ✓ Firebase upload initiated for: " + dateTimeString + " - " + data.temperature + "°C");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "  ✗ Error preparing Firebase upload: " + e.getMessage());
            return false;
        }
    }

    private void parseSensorData(String data) {
        try {
            String[] parts = data.split(",");
            if (parts.length >= 6) {
                long timestamp = System.currentTimeMillis();
                float temperature = Float.parseFloat(parts[1]);
                float humidity = Float.parseFloat(parts[2]);
                float soilMoisture = Float.parseFloat(parts[3]);
                float batteryVoltage = Float.parseFloat(parts[4]);
                int batteryPercentage = Integer.parseInt(parts[5]);
                String alerts = parts.length > 6 ? parts[6] : "OK";

                Log.d(TAG, "=== LIVE SENSOR DATA ===");
                Log.d(TAG, temperature + "°C, " + humidity + "%, " + soilMoisture + "% soil, " + batteryPercentage + "% battery");
                if (!alerts.equals("OK")) {
                    Log.d(TAG, "Alerts: " + alerts);
                }

                requireActivity().runOnUiThread(() -> {
                    txtTemperature.setText(String.format("%.1f °C", temperature));
                    txtHumidity.setText(String.format("%.1f %%", humidity));
                    txtSoil.setText(String.format("%.1f %%", soilMoisture));
                    txtBattery.setText(String.format("%d %%", batteryPercentage));
                });

                if (!isReceivingHistory) {
                    dbHelper.insertSensorData(timestamp, temperature, humidity,
                            soilMoisture, batteryPercentage, alerts);
                    Log.d(TAG, "Live data stored in SQLite");

                    uploadSingleRecordToFirebase(new SensorData(timestamp, temperature, humidity,
                            soilMoisture, batteryPercentage, alerts));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing sensor data: " + e.getMessage());
        }
    }


    private void showDisconnectDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Disconnect")
                .setMessage("Disconnect from " + connectedDeviceName + "?")
                .setPositiveButton("Disconnect", (dialog, which) -> disconnectDevice())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void disconnectDevice() {
        new Thread(() -> {
            try {
                if (bluetoothSocket != null) {
                    bluetoothSocket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing socket: " + e.getMessage());
            }

            resetConnection();
            requireActivity().runOnUiThread(() -> {
                showToast("Disconnected");
                updateBluetoothUI(false);
            });
        }).start();
    }

    private void resetConnection() {
        isConnected = false;
        connectedDeviceName = "";
        bluetoothSocket = null;
        outputStream = null;
        inputStream = null;
    }

    private void updateBluetoothUI(boolean connected) {
        if (isAdded() && bluetoothButton != null) {
            requireActivity().runOnUiThread(() -> {
                if (connected) {
                    bluetoothButton.setImageResource(R.drawable.bluetooth_connect);
                    bluetoothButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#334CAF50")));
                } else {
                    bluetoothButton.setImageResource(R.drawable.bluetooth);
                    bluetoothButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#33B0BEC5")));
                }
            });
        }
    }


    private void showToast(String message) {
        if (isAdded()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                startBluetoothConnection();
            } else {
                showToast("Bluetooth permissions required");
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == -1) { // RESULT_OK
                startBluetoothConnection();
            } else {
                showToast("Bluetooth must be enabled");
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isConnected) {
            disconnectDevice();
        }
    }

}