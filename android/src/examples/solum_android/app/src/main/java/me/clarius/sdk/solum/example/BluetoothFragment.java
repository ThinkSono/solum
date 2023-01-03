package me.clarius.sdk.solum.example;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.clarius.sdk.solum.example.databinding.FragmentBluetoothBinding;


interface DeviceReceiver {
    public void addDevice(ScanResult result);
}


public class BluetoothFragment extends Fragment implements DeviceReceiver {

    enum ScanStatus {
        STOPPED,
        SCANNING,
        ERROR,
    }

    private FragmentBluetoothBinding binding;
    private boolean bluetoothPermissionsGranted = false;
    private ArrayAdapter<String> deviceListAdapter;
    private Map<String, DeviceIdentifier> deviceMap = new HashMap<String, DeviceIdentifier>();

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
                        String probe = deviceListAdapter.getItem(position);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                }
        );

        binding.showUnnamedDevices.setOnCheckedChangeListener((v, checked) -> {
            updateDeviceList(checked);
        });

        updateBluetoothPermissionsLabel();
        setupBluetooth();
    }

    private BluetoothManager btManager;
    private BluetoothAdapter btAdapter;
    private BluetoothLeScanner leScanner;
    ScanStatus scanStatus = ScanStatus.STOPPED;
    ScanCallback currentScan = null;

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

    class DeviceIdentifier {
        public String address = null;
        public String name = null;
        public String alias = null;

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
        DeviceIdentifier identifier = new DeviceIdentifier();
        identifier.address = result.getDevice().getAddress();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                identifier.alias = result.getDevice().getAlias();
                identifier.name = result.getDevice().getName();
            }
        } catch (SecurityException ignored) {}

        deviceMap.putIfAbsent(identifier.address, identifier);
        updateDeviceList(binding.showUnnamedDevices.isChecked());
    }

    private void updateDeviceList(boolean showUnnamedDevices) {
        deviceListAdapter.clear();
        for (DeviceIdentifier identifier : deviceMap.values()) {
            if (!identifier.isUnnamed() || showUnnamedDevices) {
                deviceListAdapter.add(identifier.toString());
            }
        }
    }
}
