package me.clarius.sdk.solum.example;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import me.clarius.sdk.solum.example.databinding.FragmentBluetoothBinding;


interface DeviceReceiver {
    void addDevice(ScanResult result);
}


public class BluetoothFragment extends Fragment implements DeviceReceiver {

    enum ScanStatus {
        STOPPED,
        SCANNING,
        ERROR,
    }

    private FragmentBluetoothBinding binding;
    private boolean bluetoothPermissionsGranted = false;
    private ArrayAdapter<BTDevice> deviceListAdapter;
    private Map<String, BTDevice> deviceMap = new HashMap<>();
    private BTDevice selectedDevice = null;

    private final ActivityResultLauncher<String[]> bluetoothPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::onBluetoothPermissionUpdate);

    private void onBluetoothPermissionUpdate(Map<String, Boolean> results) {
        bluetoothPermissionsGranted = true;
        if (results != null) {
            for (boolean value : results.values()) {
                bluetoothPermissionsGranted &= value;
            }
        }
        updateBluetoothPermissionsLabel();
    }

    private void updateBluetoothPermissionsLabel() {
        String text = "";
        if (bluetoothPermissionsGranted) {
            text = "Granted";
        } else {
            text = "Not granted";
        }
        binding.bluetoothPermissionsValue.setText(text);
    }

    private void updateScanningUI() {
        String labelText = "";
        String buttonText = "";
        switch (scanStatus) {
            case SCANNING: labelText = "Scanning"; buttonText = "Stop Scan"; break;
            case STOPPED: labelText = "Stopped"; buttonText = "Start Scan"; break;
            case ERROR: labelText = "Error"; buttonText = "Start Scan"; break;
            default: labelText = "Unknown"; buttonText = "Start Scan"; break;
        }
        binding.scanStatus.setText(labelText);
        binding.toggleScanBtn.setText(buttonText);
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void requireBluetoothPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            onBluetoothPermissionUpdate(null);
        }
        bluetoothPermissionLauncher.launch(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT});
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentBluetoothBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toggleScanBtn.setOnClickListener(v -> {
            if (scanStatus == ScanStatus.SCANNING) {
                stopScan();
            } else {
                startScan();
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.requestPermissionsBtn.setOnClickListener(v -> requireBluetoothPermissions());
        }

        deviceListAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item);
        deviceListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.deviceList.setAdapter(deviceListAdapter);
        binding.deviceList.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        selectedDevice = deviceListAdapter.getItem(position);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                }
        );

        binding.showUnnamedDevices.setOnCheckedChangeListener((v, checked) -> {
            updateDeviceList(checked);
        });

        binding.connectBtn.setOnClickListener(v -> {
            if (connectionState == BluetoothProfile.STATE_CONNECTED) {
                disconnect();
            } else {
                connect();
            }
        });

        binding.readPowerBtn.setOnClickListener(v -> {
            readCharacteristic(powerServiceUUID, powerPublishedUUID);
            subscribePower();
        });

        binding.setPowerBtn.setOnClickListener(v -> {
            byte[] payload;
            if (probePowered) {
                payload = new byte[] {0};
            } else {
                payload = new byte[] {1};
            }
            writeCharacteristic(powerServiceUUID, powerRequestUUID, payload);
        });

        updateConnectionUI();
        updateBluetoothPermissionsLabel();
        setupBluetooth();
    }

    private BluetoothManager btManager;
    private BluetoothAdapter btAdapter;
    private BluetoothLeScanner leScanner;
    private BluetoothGatt gattClient;
    private List<BluetoothGattService> services;
    ScanStatus scanStatus = ScanStatus.STOPPED;
    ScanCallback currentScan = null;
    boolean probePowered = false;

    static final UUID powerServiceUUID = UUID.fromString("8C853B6A-2297-44C1-8277-73627C8D2ABC");
    static final UUID powerPublishedUUID = UUID.fromString("8C853B6A-2297-44C1-8277-73627C8D2ABD");
    static final UUID powerRequestUUID = UUID.fromString("8C853B6A-2297-44C1-8277-73627C8D2ABE");
    static final UUID wifiServiceUUID = UUID.fromString("F9EB3FAE-947A-4E5B-AB7C-C799E91ED780");
    static final UUID wifiPublishedUUID = UUID.fromString("F9EB3FAE-947A-4E5B-AB7C-C799E91ED781");
    static final UUID wifiRequestUUID = UUID.fromString("F9EB3FAE-947A-4E5B-AB7C-C799E91ED782");
    static final UUID configurationDescriptorUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private Map<UUID, BluetoothGattCharacteristic> characteristics = new HashMap<>();

    private void setupBluetooth() {
        btManager = (BluetoothManager) requireContext().getSystemService(Context.BLUETOOTH_SERVICE);
        if (btManager == null) {
            Log.e("BluetoothFragment", "BluetoothManager is null");
            return;
        }
        btAdapter = btManager.getAdapter();
        if (btManager == null) {
            Log.e("BluetoothFragment", "BluetoothAdapter is null");
            return;
        }
        leScanner = btAdapter.getBluetoothLeScanner();
        if (btManager == null) {
            Log.e("BluetoothFragment", "BluetoothLeScanner is null");
            return;
        }
    }

    class ScanCallback extends android.bluetooth.le.ScanCallback {
        private final DeviceReceiver receiver;

        public ScanCallback(DeviceReceiver receiver) {
            this.receiver = receiver;
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            Log.d("BluetoothFragment", "New BLE device: " + result.getDevice().getAddress());
            receiver.addDevice(result);
        }
    }

    private void startScan() {
        try {
            deviceMap.clear();
            deviceListAdapter.clear();
            currentScan = new ScanCallback(this);
            leScanner.startScan(currentScan);
            scanStatus = ScanStatus.SCANNING;
        } catch (SecurityException x) {
            Log.e("BluetoothFragment", "Missing required permissions for scan");
            scanStatus = ScanStatus.ERROR;
        }
        updateScanningUI();
    }

    private void stopScan() {
        try {
            if (currentScan != null) {
                leScanner.stopScan(currentScan);
                currentScan = null;
            }
            scanStatus = ScanStatus.STOPPED;
        } catch (SecurityException x) {
            Log.e("BluetoothFragment", "Missing required permissions for scan");
            scanStatus = ScanStatus.ERROR;
        }
        updateScanningUI();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    class BTDevice {
        public String address = null;
        public String name = null;
        public String alias = null;
        public BluetoothDevice device = null;

        public boolean isUnnamed() {
            return name == null && alias == null;
        }

        @NonNull
        public String toString() {
            if (name != null) {
                return name;
            } else if (alias != null) {
                return alias;
            } else if (address != null) {
                return address;
            }
            return "Unknown";
        }
    }

    @Override
    public void addDevice(ScanResult result) {
        BTDevice device = new BTDevice();
        device.device = result.getDevice();
        device.address = result.getDevice().getAddress();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                device.alias = result.getDevice().getAlias();
                device.name = result.getDevice().getName();
            }
        } catch (SecurityException ignored) {}

        if (deviceMap.putIfAbsent(device.address, device) == null) {
            updateDeviceList(binding.showUnnamedDevices.isChecked());
        }
    }

    private void updateDeviceList(boolean showUnnamedDevices) {
        deviceListAdapter.clear();
        for (BTDevice device : deviceMap.values()) {
            if (!device.isUnnamed() || showUnnamedDevices) {
                deviceListAdapter.add(device);
            }
        }
    }


    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            binding.getRoot().post(() -> updateBluetoothConnection(newState));
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                binding.getRoot().post(() -> updateServices());
            } else {
                Log.w("BluetoothFragment", "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, int status) {
            byte[] value = characteristic.getValue();
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.getUuid().equals(powerPublishedUUID)) {
                Log.d("BluetoothFragment", "updateProbePower");
                binding.getRoot().post(() -> updateProbePower(value));
            }
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic) {
            byte[] value = characteristic.getValue();
            if(value != null) {
                binding.getRoot().post(() -> updateProbePower(value));
            }
        }
    };

    private int connectionState = BluetoothProfile.STATE_DISCONNECTED;

    private void connect() {
        if (selectedDevice == null) {
            return;
        }
        try {
            gattClient = selectedDevice.device.connectGatt(requireContext(), true, gattCallback);
        } catch (SecurityException ignored) {}
    }

    private void disconnect() {
        if (gattClient != null) {
            try {
                gattClient.disconnect();
            } catch (SecurityException ignored) {}
            gattClient = null;
            services = null;
            clearServices();
        }
    }

    private void updateServices() {
        if (gattClient == null) {
            return;
        }
        services = gattClient.getServices();
        clearServices();
        for (BluetoothGattService service: services) {
            displayServiceInfo(service);
            for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                characteristics.put(c.getUuid(), c);
            }
        }
    }

    public void updateBluetoothConnection(int newState) {
        connectionState = newState;
        updateConnectionUI();
        try {
            switch (connectionState) {
                case BluetoothProfile.STATE_CONNECTED:
                    gattClient.discoverServices();
                    break;
            }
        } catch (SecurityException ignored) {}
    }

    private void updateConnectionUI() {
        String connectionStatus = "N/A";
        String buttonText = "Connect";
        switch(connectionState) {
            case BluetoothProfile.STATE_DISCONNECTED: connectionStatus = "disconnected"; break;
            case BluetoothProfile.STATE_CONNECTED: connectionStatus = "connected"; buttonText = "Disconnect"; break;
            case BluetoothProfile.STATE_CONNECTING: connectionStatus = "connecting"; break;
            case BluetoothProfile.STATE_DISCONNECTING: connectionStatus = "disconnecting"; break;
        }
        binding.connectionStatus.setText(connectionStatus);
        binding.connectBtn.setText(buttonText);
    }

    @Override
    public void onPause() {
        super.onPause();
        disconnect();
    }

    private void clearServices() {
        binding.services.removeAllViews();
        characteristics.clear();
    }

    private void displayServiceInfo(BluetoothGattService service) {
        UUID serviceUuid = service.getUuid();
        ArrayList<UUID> characteristics = new ArrayList<>();
        for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
            characteristics.add(c.getUuid());
        }
        TextView serviceView = new TextView(requireContext());
        serviceView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        serviceView.setText("service: " + serviceUuid.toString());
        binding.services.addView(serviceView);

        for (UUID characteristicUUID : characteristics) {
            TextView cView = new TextView(requireContext());
            cView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            cView.setText("-- char: " + characteristicUUID.toString());
            binding.services.addView(cView);
        }
    }

    private void readCharacteristic(UUID serviceUUID, UUID characteristicUUID) {
        BluetoothGattCharacteristic characteristic = findCharacteristic(serviceUUID, characteristicUUID);
        if(characteristic == null) return;

        Log.d("BluetoothFragment", "Dispatching read of characteristic " + characteristic.getUuid().toString());
        try {
            boolean initialized = gattClient.readCharacteristic(characteristic);
            Log.d("BluetoothFragment", "Read request initialized: " + initialized);
            byte[] newValue = characteristic.getValue();
            if (newValue != null) {
                String display = "0x";
                for (byte b : newValue) {
                    display += Byte.toString(b);
                }
                Log.d("BluetoothFragment", "Value of characteristic after read dispatch " + display);
            }
        } catch (SecurityException e) {
            Log.e("BluetoothFragment", "Not allowed to read characteristic. Check app permissions.");
        }
    }

    @SuppressLint("NewApi")
    public void subscribePower() {
        BluetoothGattCharacteristic characteristic = findCharacteristic(powerServiceUUID, powerPublishedUUID);
        if(characteristic == null) return;

        Log.d("BluetoothFragment", "Subscribing to characteristic " + characteristic.getUuid().toString());
        try {
             gattClient.setCharacteristicNotification(characteristic, true);
             BluetoothGattDescriptor configDescriptor = characteristic.getDescriptor(configurationDescriptorUUID);
             configDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
             gattClient.writeDescriptor(configDescriptor);
        } catch (SecurityException e) {
            Log.e("BluetoothFragment", "Not allowed to write characteristic. Check app permissions.");
        }
    }

    private void writeCharacteristic(UUID serviceUUID, UUID characteristicUUID, byte[] value) {
        BluetoothGattCharacteristic characteristic = findCharacteristic(serviceUUID, characteristicUUID);
        if(characteristic == null) return;
        Log.d("BluetoothFragment", "Dispatching write of characteristic " + characteristic.getUuid().toString());
        try {
            characteristic.setValue(value);
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            gattClient.writeCharacteristic(characteristic);
        } catch (SecurityException e) {
            Log.e("BluetoothFragment", "Not allowed to read characteristic. Check app permissions.");
        }
    }

    private BluetoothGattCharacteristic findCharacteristic(UUID serviceUUID, UUID characteristicUUID) {
        if (gattClient == null) {
            Log.e("BluetoothFragment", "Bluetooth not connected");
            return null;
        }
        BluetoothGattService service = gattClient.getService(serviceUUID);
        if (service == null) {
            Log.e("BluetoothFragment", "Could not find service " + serviceUUID.toString());
            return null;
        }
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
        if (characteristic == null) {
            Log.e("BluetoothFragment", "Could not find characteristic " + characteristicUUID.toString());
            return null;
        }
        return characteristic;
    }

    public void updateProbePower(byte[] power) {
        probePowered = false;
        for (byte b : power) {
            if (b > 0) {
                probePowered = true;
            }
        }
        binding.powerStatus.setText(payloadToHex(power));
        if (probePowered) {
            binding.setPowerBtn.setText("Power off");
        } else {
            binding.setPowerBtn.setText("Power on");
        }
    }

    static String payloadToHex(byte[] payload) {
        if (payload == null) {
            return "null";
        }
        String hex = "0x";
        for (byte b : payload) {
            hex += Byte.toString(b);
        }
        return hex;
    }
}
