package me.clarius.sdk.solum.example;

import android.bluetooth.BluetoothProfile;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.util.Map;

import me.clarius.sdk.Solum;
import me.clarius.sdk.solum.example.databinding.FragmentAutoConnectionBinding;

public class AutoConnectionFragment extends Fragment {

    private ProbeStore probeStore;
    private BluetoothAntenna btAntenna;
    private WifiAntenna wifiAntenna;

    private FragmentAutoConnectionBinding binding;
    private ArrayAdapter<Probe> probeAdapter;
//    private Solum solum;

    private final ActivityResultLauncher<String[]> bluetoothPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::onBluetoothPermissionUpdate);

    public void onBluetoothPermissionUpdate(Map<String, Boolean> results) {
        this.btAntenna.onBluetoothPermissionUpdate(results);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_auto_connection, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding = FragmentAutoConnectionBinding.bind(view);

        ViewModelProvider provider = new ViewModelProvider(requireActivity());
        probeStore = provider.get(ProbeStore.class);
        btAntenna = provider.get(BluetoothAntenna.class);
        wifiAntenna = provider.get(WifiAntenna.class);
        btAntenna.connectProbeStore(probeStore);

        probeAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item);
        probeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.probeList.setAdapter(probeAdapter);
        binding.probeList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateProbeState();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updateProbeState();
            }
        });

        probeStore.probeUpdated.observe(getViewLifecycleOwner(), probe -> {
            probeAdapter.clear();
            for (Probe p : probeStore.probeMap.values()) {
                probeAdapter.add(p);
            }
            updateProbeState();
            nextStep();
            updateChecklist();
        });

        btAntenna.connectionState.observe(getViewLifecycleOwner(), state -> {
            bluetoothConnected = state == BluetoothProfile.STATE_CONNECTED;
            nextStep();
            updateChecklist();
        });

        wifiAntenna.network.observe(getViewLifecycleOwner(), network -> {
            wifiConnected = network != null;
            nextStep();
            updateChecklist();
        });

        binding.connectToProbe.setOnClickListener(v -> {
            nextStep();
            updateChecklist();
        });

        binding.searchProbes.setOnClickListener(v -> {
            if (btAntenna.scanStatus.getValue() == BluetoothAntenna.ScanStatus.SCANNING) {
                btAntenna.stopScan();
            } else {
                btAntenna.startScan();
            }
        });

        btAntenna.scanStatus.observe(getViewLifecycleOwner(), status -> {
            binding.scanStatus.setText(status.toString());

            if (status == BluetoothAntenna.ScanStatus.SCANNING) {
                binding.searchProbes.setText("Stop search");
            } else {
                binding.searchProbes.setText("Search probes");
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            btAntenna.requestBluetoothPermissions(bluetoothPermissionLauncher);
        }

        updateChecklist();
    }

    private synchronized void nextStep() {
        Step originalStep = currentStep;
        Step nextStep = currentStep;
        do {
            currentStep = nextStep;
            nextStep = transitionStateMachine();
        } while (currentStep != nextStep);

        if (originalStep == currentStep) {
            return;
        }

        Probe selectedProbe = getSelectedProbe();

        switch (currentStep) {
            case CONNECT_BLUETOOTH: {
                btAntenna.connect(selectedProbe.bluetoothAddr);
            } break;
            case POWER_PROBE: {
                byte[] payload = new byte[]{1};
                btAntenna.writeCharacteristic(BluetoothAntenna.powerServiceUUID, BluetoothAntenna.powerRequestUUID, payload);
            } break;
            case WAIT_WIFI: break;
            case CONNECT_WIFI: {
                wifiAntenna.ssid.setValue(selectedProbe.wifiInfo.ssid);
                wifiAntenna.passphrase.setValue(selectedProbe.wifiInfo.passphrase);
                wifiAntenna.connectWifi();
            } break;
        }
    }

    public Step transitionStateMachine() {
        Probe selectedProbe = getSelectedProbe();
        switch(currentStep) {
            case SELECT_PROBE: {
                if (selectedProbe != null) {
                    return Step.CONNECT_BLUETOOTH;
                }
            } break;
            case CONNECT_BLUETOOTH: {
                if (selectedProbe == null) {
                    return Step.SELECT_PROBE;
                }
                if (bluetoothConnected) {
                    return Step.POWER_PROBE;
                }
            } break;
            case POWER_PROBE: {
                if (!bluetoothConnected) {
                    return Step.CONNECT_BLUETOOTH;
                }
                if (probePowered) {
                    return Step.WAIT_WIFI;
                }
            } break;
            case WAIT_WIFI: {
                if (!bluetoothConnected) {
                    return Step.CONNECT_BLUETOOTH;
                }
                if (!probePowered) {
                    return Step.POWER_PROBE;
                }
                if (wifiEnabled) {
                    return Step.CONNECT_WIFI;
                }
            } break;
            case CONNECT_WIFI: {
                if (!wifiEnabled) {
                    return Step.WAIT_WIFI;
                }
                if (wifiConnected) {
                    return Step.CONNECT_SOLUM;
                }
            } break;
        }

        return currentStep;
    }

    private void updateChecklist() {
        Probe selectedProbe = getSelectedProbe();
        String wifiInfoText = "N/A";
        if (selectedProbe != null && selectedProbe.wifiInfo != null) {
            wifiInfoText = selectedProbe.wifiInfo.toString();
        }

        binding.currentStep.setText(currentStep.toString());
        binding.bluetoothConnected.setText(Boolean.toString(bluetoothConnected));
        binding.probePowered.setText(Boolean.toString(probePowered));
        binding.wifiInfo.setText(wifiInfoText);
        binding.wifiConnected.setText(Boolean.toString(wifiConnected));
        binding.solumConnected.setText(Boolean.toString(solumnConnected));
        binding.loadedApplication.setText(Boolean.toString(applicationLoaded));
        binding.imaging.setText(Boolean.toString(imaging));
    }

    private void updateProbeState() {
        Probe selectedProbe = getSelectedProbe();
        if (selectedProbe == null) {
            probePowered = false;
            wifiEnabled = false;
            return;
        }

        probePowered = selectedProbe.powered;
        if (selectedProbe.wifiInfo == null) {
            wifiEnabled = false;
        } else {
            wifiEnabled = selectedProbe.wifiInfo.state.equals("connected");
        }
    }

    private Probe getSelectedProbe() {
        int position = binding.probeList.getSelectedItemPosition();
        if (position == Spinner.INVALID_POSITION) {
            return null;
        }
        Probe selectedProbe = probeAdapter.getItem(position);
        return probeStore.probeMap.get(selectedProbe.name);
    }

    enum Step {
        SELECT_PROBE,
        CONNECT_BLUETOOTH,
        POWER_PROBE,
        WAIT_WIFI,
        CONNECT_WIFI,
        CONNECT_SOLUM,
        LOAD_APPLICATION,
        IMAGING_READY,
        IMAGING,
        EOC,
    }

    private Step currentStep = Step.SELECT_PROBE;
    private boolean bluetoothConnected = false;
    private boolean probePowered = false;
    private boolean wifiEnabled = false;
    private boolean wifiConnected = false;
    private boolean solumnConnected = false;
    private boolean applicationLoaded = false;
    private boolean imaging = false;
}