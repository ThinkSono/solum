package me.clarius.sdk.solum.example;

import android.Manifest;
import android.app.Application;
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
import android.net.MacAddress;
import android.os.Build;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import javax.crypto.Mac;

interface DeviceReceiver {
    void addDevice(ScanResult result);
}

public class BluetoothAntenna extends AndroidViewModel implements DeviceReceiver {
    public BluetoothAntenna(@NonNull Application application) {
        super(application);
    }

    public void connectProbeStore(ProbeStore probeStore) {
        this.probeStore = probeStore;
    }

    enum ScanStatus {
        STOPPED,
        SCANNING,
        ERROR,
    }

    public MutableLiveData<Collection<BTDevice>> devices = new MutableLiveData<>();
    public MutableLiveData<ScanStatus> scanStatus = new MutableLiveData<>();
    public MutableLiveData<Boolean> permissionsGranted = new MutableLiveData<>();
    public MutableLiveData<List<BluetoothGattService>> services = new MutableLiveData<>();
    public MutableLiveData<Integer> connectionState = new MutableLiveData<Integer>(BluetoothProfile.STATE_DISCONNECTED);

    public final Map<String, BTDevice> deviceMap = new HashMap<>();
    private ProbeStore probeStore;
    private BluetoothLeScanner leScanner;
    private BluetoothGatt gattClient;
    private ScanCallback currentScan = null;
    private BluetoothOperator operator = new BluetoothOperator();

    static final UUID powerServiceUUID = UUID.fromString("8C853B6A-2297-44C1-8277-73627C8D2ABC");
    static final UUID powerPublishedUUID = UUID.fromString("8C853B6A-2297-44C1-8277-73627C8D2ABD");
    static final UUID powerRequestUUID = UUID.fromString("8C853B6A-2297-44C1-8277-73627C8D2ABE");
    static final UUID wifiServiceUUID = UUID.fromString("F9EB3FAE-947A-4E5B-AB7C-C799E91ED780");
    static final UUID wifiPublishedUUID = UUID.fromString("F9EB3FAE-947A-4E5B-AB7C-C799E91ED781");
    static final UUID wifiRequestUUID = UUID.fromString("F9EB3FAE-947A-4E5B-AB7C-C799E91ED782");
    static final UUID configurationDescriptorUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    @RequiresApi(api = Build.VERSION_CODES.S)
    public void requestBluetoothPermissions(ActivityResultLauncher<String[]> launcher) {
        String[] permissions = new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
        boolean hasAllPermissions = true;
        for (String permission : permissions) {
            hasAllPermissions &= ContextCompat.checkSelfPermission(getApplication(), permission) == PackageManager.PERMISSION_GRANTED;
        }

        if (!hasAllPermissions) {
            launcher.launch(permissions);
        } else {
            setupBluetooth();
            permissionsGranted.postValue(true);
        }
    }

    public void onBluetoothPermissionUpdate(Map<String, Boolean> results) {
        boolean hasAllPermissions = true;
        if (results != null) {
            for (boolean value : results.values()) {
                hasAllPermissions &= value;
            }
        }
        permissionsGranted.postValue(hasAllPermissions);
        if (hasAllPermissions) {
            setupBluetooth();
        }
    }

    public void setupBluetooth() {
        BluetoothManager btManager = (BluetoothManager) getApplication().getSystemService(Context.BLUETOOTH_SERVICE);
        if (btManager == null) {
            Log.e("BluetoothFragment", "BluetoothManager is null");
            return;
        }
        BluetoothAdapter btAdapter = btManager.getAdapter();
        if (btAdapter == null) {
            Log.e("BluetoothFragment", "BluetoothAdapter is null");
            return;
        }
        leScanner = btAdapter.getBluetoothLeScanner();
        if (leScanner == null) {
            Log.e("BluetoothFragment", "BluetoothLeScanner is null");
            return;
        }
    }

    public void startScan() {
        try {
            deviceMap.clear();
            devices.postValue(deviceMap.values());
            currentScan = new BluetoothAntenna.ScanCallback(this);
            leScanner.startScan(currentScan);
            scanStatus.postValue(BluetoothAntenna.ScanStatus.SCANNING);
        } catch (SecurityException x) {
            Log.e("BluetoothFragment", "Missing required permissions for scan");
            scanStatus.postValue(BluetoothAntenna.ScanStatus.ERROR);
        }
    }

    public void stopScan() {
        try {
            if (currentScan != null) {
                leScanner.stopScan(currentScan);
                currentScan = null;
            }
            scanStatus.postValue(BluetoothAntenna.ScanStatus.STOPPED);
        } catch (SecurityException x) {
            Log.e("BluetoothFragment", "Missing required permissions for scan");
            scanStatus.postValue(BluetoothAntenna.ScanStatus.ERROR);
        }
    }

    public void connect(String deviceAddress) {
        connect(deviceAddress, true);
    }

    public void connect(String deviceAddress, boolean autoReconnect) {
        stopScan();
        disconnect();

        BTDevice selectedDevice = deviceMap.get(deviceAddress);
        if (selectedDevice == null) {
            return;
        }
        try {
            gattClient = selectedDevice.device.connectGatt(getApplication(), autoReconnect, gattCallback);
        } catch (SecurityException ignored) {}
    }

    public void disconnect() {
        if (gattClient != null) {
            try {
                gattClient.disconnect();
            } catch (SecurityException ignored) {}
            gattClient = null;
            operator.clear();
            clearServices();
        }
    }

    @Override
    public void addDevice(ScanResult result) {
        BluetoothAntenna.BTDevice device = new BluetoothAntenna.BTDevice();
        device.device = result.getDevice();
        device.address = result.getDevice().getAddress();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                device.alias = result.getDevice().getAlias();
                device.name = result.getDevice().getName();
            }
        } catch (SecurityException ignored) {}

        deviceMap.putIfAbsent(device.address, device);

        if (device.name != null && device.name.matches("^CUS-.*$")) {
            if (!probeStore.probeMap.containsKey(device.name)) {
                Probe probe = new Probe();
                probe.bluetoothAddr = device.address;
                probe.name = device.name;
                MacAddress address = MacAddress.fromString(probe.bluetoothAddr);
                byte[] address_arr = address.toByteArray();
                StringBuilder sb = new StringBuilder();
                for (byte b : address_arr) {
                    sb.append(String.format("%02X ", b));
                }
                int lastByte = 0xFF & address_arr[5];
                if (lastByte != 255) {
                    lastByte += 1;
                    address_arr[5] = (byte) lastByte;
                    probe.bssid = MacAddress.fromBytes(address_arr).toString();
                    Log.d("BluetoothAntenna", "Inferred BSSID from bluetooth address: " + probe.bssid);
                }
                probeStore.probeMap.put(probe.name, probe);
                probeStore.probeUpdated.postValue(probe);
            }
        }

        devices.postValue(deviceMap.values());
    }


    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            try {
                updateBluetoothConnection(newState);
            } catch (Exception ignored) {}
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                updateServices();
            } else {
                Log.w("BluetoothFragment", "onServicesDiscovered received: " + status);
            }
            operator.commandFinished();
        }

        @Override
        public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, int status) {
            byte[] value = characteristic.getValue();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.getUuid().equals(powerPublishedUUID)) {
                    Log.d("BluetoothFragment", "updateProbePower");
                    updateProbePower(value);
                } else if (characteristic.getUuid().equals(wifiPublishedUUID)) {
                    updateWifi(value);
                }
            }
            operator.commandFinished();
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic) {
            byte[] value = characteristic.getValue();
            if (characteristic.getUuid().equals(powerPublishedUUID)) {
                Log.d("BluetoothFragment", "updateProbePower");
                updateProbePower(value);
            } else if (characteristic.getUuid().equals(wifiPublishedUUID)) {
                updateWifi(value);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d("BluetoothFragment", "onCharacteristicWrite");
            operator.commandFinished();
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d("BluetoothFragment", "onDescriptorWrite");
            operator.commandFinished();
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Log.d("BluetoothFragment", "onMtuChanged");
            operator.commandFinished();
        }
    };

    private void updateServices() {
        if (gattClient == null) {
            return;
        }
        services.postValue(gattClient.getServices());
    }

    private void updateBluetoothConnection(int newState) {
        connectionState.postValue(newState);
        try {
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    discoverServices();
                    changeMTU();
                    subscribeCharacteristic(powerServiceUUID, powerPublishedUUID);
                    subscribeCharacteristic(wifiServiceUUID, wifiPublishedUUID);
                    readCharacteristic(powerServiceUUID, powerPublishedUUID);
                    readCharacteristic(wifiServiceUUID, wifiPublishedUUID);
                    break;
            }
        } catch (SecurityException ignored) {}
    }

    private void clearServices() {
        services.postValue(new ArrayList<>());
    }

    public void discoverServices() {
        operator.addCommand(() -> {
            try {
                this.gattClient.discoverServices();
            } catch (SecurityException ignored) {}
        });
    }

    public void changeMTU() {
        operator.addCommand(() -> {
            Log.d("BluetoothFragment", "Requesting MTU change to 512");
            try {
                gattClient.requestMtu(512);
            } catch (SecurityException ignored) {};
        });
    }

    public void readCharacteristic(UUID serviceUUID, UUID characteristicUUID) {
        operator.addCommand(() -> {
            BluetoothGattCharacteristic characteristic = findCharacteristic(serviceUUID, characteristicUUID);
            if(characteristic == null) return;
            Log.d("BluetoothFragment", "Dispatching read of characteristic " + characteristic.getUuid().toString());
            try {
                boolean initialized = gattClient.readCharacteristic(characteristic);
                Log.d("BluetoothFragment", "Read request initialized: " + initialized);
            } catch (SecurityException e) {
                Log.e("BluetoothFragment", "Not allowed to read characteristic. Check app permissions.");
            }
        });
    }

    public void subscribeCharacteristic(UUID serviceUUID, UUID characteristicUUID) {
        operator.addCommand(() -> {
            BluetoothGattCharacteristic characteristic = findCharacteristic(serviceUUID, characteristicUUID);
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
        });
    }

    public void writeCharacteristic(UUID serviceUUID, UUID characteristicUUID, byte[] value) {
        operator.addCommand(() -> {
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
        });
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

    public Probe getCurrentProbe() {
        if (gattClient == null) {
            return null;
        }
        BluetoothAntenna.BTDevice device = deviceMap.get(gattClient.getDevice().getAddress());
        Probe probe = null;
        if (device != null && device.name != null) {
            probe = probeStore.probeMap.get(device.name);
        }
        return probe;
    }

    private void postProbeUpdate(Probe probe) {
        probeStore.probeUpdated.postValue(probe);
    }

    private void updateProbePower(byte[] power) {
        boolean probePowered = false;
        for (byte b : power) {
            if (b > 0) {
                probePowered = true;
            }
        }

        Probe probe = getCurrentProbe();
        if (probe != null) {
            probe.powered = probePowered;
            Log.d("BluetoothFragment", "Update wifi info for probe " + probe);
            postProbeUpdate(probe);
        }
    }

    private void updateWifi(byte[] wifiInfo) {
        if (wifiInfo == null) return;
        Log.d("BluetoothFragment", "updateWifi called with length " + wifiInfo.length);
        String payload = new String(wifiInfo, StandardCharsets.US_ASCII);
        String text = payload.replace('\n', ',');

        Probe probe = getCurrentProbe();
        if (probe != null) {
            probe.wifiInfo = WifiInfo.fromPayload(payload);
            Log.d("BluetoothFragment", "Update wifi info for probe " + probe);
            postProbeUpdate(probe);
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


}
