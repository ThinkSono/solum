package me.clarius.sdk.solum.example;

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

import java.util.Map;
import me.clarius.sdk.solum.example.databinding.FragmentAutoConnectionBinding;

public class AutoConnectionFragment extends Fragment {
    private SolumProbe solumProbe;
    private FragmentAutoConnectionBinding binding;
    private ArrayAdapter<Probe> probeAdapter;

    private final ActivityResultLauncher<String[]> bluetoothPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::onBluetoothPermissionUpdate);
    private final ActivityResultLauncher<String[]> wifiPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::onWifiPermissionUpdate);

    public void onBluetoothPermissionUpdate(Map<String, Boolean> results) {
        solumProbe.btAntenna.onBluetoothPermissionUpdate(results);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            solumProbe.wifiAntenna.requestWifiPermissions(wifiPermissionLauncher);
        }
    }

    public void onWifiPermissionUpdate(Map<String, Boolean> results) {
        solumProbe.wifiAntenna.onWifiPermissionsUpdate(results);
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
        solumProbe = provider.get(SolumProbe.class);
        solumProbe.init(requireActivity());

        solumProbe.processedImage.observe(getViewLifecycleOwner(), binding.imageView::setImageBitmap);

        binding.connectToProbe.setOnClickListener(v -> {
            if (solumProbe.state.currentStep == SolumProbe.Step.SELECT_PROBE) {
                solumProbe.connect();
            } else {
                solumProbe.disconnect();
            }
        });

        binding.toggleImaging.setOnClickListener(v -> {
            solumProbe.toggleImaging();
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            solumProbe.btAntenna.requestBluetoothPermissions(bluetoothPermissionLauncher);
        }

        solumProbe.btAntenna.scanStatus.observe(getViewLifecycleOwner(), status -> {
            if (status == BluetoothAntenna.ScanStatus.SCANNING) {
                binding.searchProbes.setText("Stop search");
            } else {
                binding.searchProbes.setText("Search probes");
            }
        });

        binding.searchProbes.setOnClickListener(v -> {
            if (binding.searchProbes.getText().equals("Stop search")) {
                solumProbe.btAntenna.stopScan();
            } else {
                solumProbe.btAntenna.startScan();
            }
        });

        probeAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item);
        probeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.probeList.setAdapter(probeAdapter);
        binding.probeList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Probe probe = probeAdapter.getItem(position);
                solumProbe.setProbeCereal(probe.name);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                solumProbe.setProbeCereal(null);
            }
        });

        solumProbe.probeStore.probeUpdated.observe(getViewLifecycleOwner(), probe -> {
            probeAdapter.clear();
            for (Probe p : solumProbe.probeStore.probeMap.values()) {
                probeAdapter.add(p);
            }
        });

        solumProbe.stateLD.observe(getViewLifecycleOwner(), this::updateChecklist);
    }

    private void updateChecklist(SolumProbe.State state) {
        Probe selectedProbe = solumProbe.getSelectedProbe();
        String wifiInfoText = "N/A";
        if (selectedProbe != null && selectedProbe.wifiInfo != null) {
            wifiInfoText = selectedProbe.wifiInfo.toString();
        }

        if (state.currentStep == SolumProbe.Step.SELECT_PROBE) {
            binding.connectToProbe.setText("Connect");
        } else {
            binding.connectToProbe.setText("Disconnect");
        }
        binding.currentStep.setText(state.currentStep.toString());
        binding.bluetoothConnected.setText(Boolean.toString(state.bluetoothConnected));
        binding.probePowered.setText(Boolean.toString(state.probePowered));
        binding.wifiInfo.setText(wifiInfoText);
        binding.wifiConnected.setText(Boolean.toString(state.wifiConnected));
        binding.solumConnected.setText(Boolean.toString(state.solumnConnected));
        binding.loadedApplication.setText(Boolean.toString(state.applicationLoaded));
        binding.imaging.setText(Boolean.toString(state.imaging));
        binding.toggleImaging.setEnabled(state.solumnConnected && state.applicationLoaded && state.certificateValid);
    }
}