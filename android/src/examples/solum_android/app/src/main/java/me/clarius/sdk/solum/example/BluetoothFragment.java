package me.clarius.sdk.solum.example;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import me.clarius.sdk.solum.example.databinding.FragmentBluetoothBinding;
import  me.clarius.sdk.solum.example.BluetoothAntenna.ScanStatus;

public class BluetoothFragment extends Fragment {
    private FragmentBluetoothBinding binding;
    private ArrayAdapter<BluetoothAntenna.BTDevice> deviceListAdapter;
    private ProbeStore probeStore;
    private BluetoothAntenna btAntenna;

    private final ActivityResultLauncher<String[]> bluetoothPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::onBluetoothPermissionUpdate);

    public void onBluetoothPermissionUpdate(Map<String, Boolean> results) {
        this.btAntenna.onBluetoothPermissionUpdate(results);
    }

    private void updatePermissionsUI(Boolean permissionGranted) {
        String text = "";
        if (permissionGranted) {
            text = "Granted";
        } else {
            text = "Not granted";
        }
        binding.bluetoothPermissionsValue.setText(text);
    }

    private void updateScanStatusUI(ScanStatus scanStatus) {
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

        probeStore = new ViewModelProvider(requireActivity()).get(ProbeStore.class);
        btAntenna = new ViewModelProvider(requireActivity()).get(BluetoothAntenna.class);
        btAntenna.connectProbeStore(probeStore);

        binding.toggleScanBtn.setOnClickListener(v -> {
            if (btAntenna.scanStatus.getValue() == ScanStatus.SCANNING) {
                btAntenna.stopScan();
            } else {
                btAntenna.startScan();
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.requestPermissionsBtn.setOnClickListener(v -> btAntenna.requestBluetoothPermissions(bluetoothPermissionLauncher));
        }

        deviceListAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item);
        deviceListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.deviceList.setAdapter(deviceListAdapter);
        binding.deviceList.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                       // TODO: Clear all UI elements, update with selected probe / device
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                }
        );

        binding.showUnnamedDevices.setOnCheckedChangeListener((v, checked) -> {
            updateDeviceList(btAntenna.devices.getValue(), checked);
        });

        binding.connectBtn.setOnClickListener(v -> {
            int state;
            try {
                state = btAntenna.connectionState.getValue();
            } catch (NullPointerException e) {
                state = BluetoothProfile.STATE_DISCONNECTED;
            }

            if (state == BluetoothProfile.STATE_CONNECTED) {
                btAntenna.disconnect();
            } else {
                int position = binding.deviceList.getSelectedItemPosition();
                if (position != AdapterView.INVALID_POSITION) {
                    String selectedAddress = deviceListAdapter.getItem(position).address;
                    btAntenna.connect(selectedAddress);
                }
            }
        });

        binding.readPowerBtn.setOnClickListener(v -> {
            btAntenna.readCharacteristic(BluetoothAntenna.powerServiceUUID, BluetoothAntenna.powerPublishedUUID);
        });

        binding.setPowerBtn.setOnClickListener(v -> {
            Probe probe = btAntenna.getCurrentProbe();
            byte[] payload;
            if (probe.powered) {
                payload = new byte[] {0};
            } else {
                payload = new byte[] {1};
            }
            btAntenna.writeCharacteristic(BluetoothAntenna.powerServiceUUID, BluetoothAntenna.powerRequestUUID, payload);
        });

        binding.wifiBtn.setOnClickListener(v -> {
            btAntenna.readCharacteristic(BluetoothAntenna.wifiServiceUUID, BluetoothAntenna.wifiPublishedUUID);
        });

        btAntenna.permissionsGranted.observe(getViewLifecycleOwner(), this::updatePermissionsUI);
        btAntenna.scanStatus.observe(getViewLifecycleOwner(), this::updateScanStatusUI);
        btAntenna.services.observe(getViewLifecycleOwner(), this::updateServicesUI);
        btAntenna.devices.observe(getViewLifecycleOwner(), devices -> updateDeviceList(devices, binding.showUnnamedDevices.isChecked()));
        btAntenna.connectionState.observe(getViewLifecycleOwner(), this::updateConnectionUI);
        probeStore.probeUpdated.observe(getViewLifecycleOwner(), probe -> {
            updateProbePower(probe.powered);
            updateWifi(probe.wifiInfo);
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            btAntenna.requestBluetoothPermissions(bluetoothPermissionLauncher);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void updateDeviceList(Collection<BluetoothAntenna.BTDevice> devices, boolean showUnnamedDevices) {
        deviceListAdapter.clear();
        for (BluetoothAntenna.BTDevice device : devices) {
            if (!device.isUnnamed() || showUnnamedDevices) {
                deviceListAdapter.add(device);
            }
        }
    }

    private void updateConnectionUI(int connectionState) {
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
        btAntenna.disconnect();
    }

    private void updateServicesUI(List<BluetoothGattService> services) {
        binding.services.removeAllViews();
        for (BluetoothGattService service : services) {
            displayServiceInfo(service);
        }
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

    public void updateProbePower(boolean powered) {
        if (powered) {
            binding.powerStatus.setText("On");
            binding.setPowerBtn.setText("Power off");
        } else {
            binding.powerStatus.setText("Off");
            binding.setPowerBtn.setText("Power on");
        }
    }

    public void updateWifi(WifiInfo wifiInfo) {
        if (wifiInfo == null) {
            binding.wifiInfo.setText("N/A");
            return;
        }
        // linter says we can replace this with string. Is this some advanced compiler magic?
        // I would think that all the string additions would lead to creation of new strings at every operation.
        StringBuilder builder = new StringBuilder();
        builder.append("state: ").append(wifiInfo.state);
        builder.append("\nssid: ").append(wifiInfo.ssid);
        builder.append("\npassphrase: ").append(wifiInfo.passphrase);
        builder.append("\nipAddr: ").append(wifiInfo.ipAddr);
        builder.append("\ncontrolPort: ").append(wifiInfo.controlPort);
        builder.append("\ncastPort: ").append(wifiInfo.castPort);

        binding.wifiInfo.setText(builder.toString());
    }
}
