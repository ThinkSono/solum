package me.clarius.sdk.solum.example;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import java.util.Map;

import me.clarius.sdk.solum.example.databinding.FragmentWifiBinding;

public class WifiFragment extends Fragment {

    private FragmentWifiBinding binding;
    private WifiAntenna antenna;
    private ProbeStore probeStore;
    private boolean connectButtonEnabled = true;
    private ArrayAdapter<Probe> probeListAdapter;

    private boolean permissionsGranted = false;

    private final ActivityResultLauncher<String[]> wifiPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionUpdate);

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void requestWifiPermissions() {
        wifiPermissionLauncher.launch(new String[]{Manifest.permission.CHANGE_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
    }

    private void onPermissionUpdate(Map<String, Boolean> results) {
        permissionsGranted = true;
        if (results != null) {
            for (boolean value : results.values()) {
                permissionsGranted &= value;
            }
        }
        updatePermissionLabel();
    }

    private void updatePermissionLabel() {
        String text;
        if (permissionsGranted) {
            text = "Granted";
        } else {
            text = "Not granted";
        }
        binding.wifiPermssionStatus.setText(text);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_wifi, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        antenna = new ViewModelProvider(requireActivity()).get(WifiAntenna.class);
        probeStore = new ViewModelProvider(requireActivity()).get(ProbeStore.class);
        binding = FragmentWifiBinding.bind(view);

        binding.connectWifi.setOnClickListener(v -> {
            if (connectButtonEnabled) {
                String ssid = binding.wifiPrefix.getText().toString();
                String passphrase = binding.wifiPassword.getText().toString();
                antenna.connectWifi(ssid, passphrase, getSelectedProbe());
            } else {
                antenna.disconnectWifi();
                updatePermissionLabel();
            }
        });

        binding.wifiPermissionBtn.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestWifiPermissions();
            }
        });

        antenna.network.observe(getViewLifecycleOwner(), network -> {
            if (network != null) {
                binding.wifiStatus.setText("Connected");
            } else {
                binding.wifiStatus.setText("No connection");
            }
        });

        antenna.tryingToConnect.observe(getViewLifecycleOwner(), tryingToConnect -> {
            connectButtonEnabled = !tryingToConnect;
            if (tryingToConnect) {
                binding.connectWifi.setText("Disconnect");
            } else {
                binding.connectWifi.setText("Connect");
            }
        });

        probeListAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item);
        probeListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        for (Probe probe : probeStore.probeMap.values()) {
            probeListAdapter.add(probe);
        }
        binding.probeList.setAdapter(probeListAdapter);
        binding.probeList.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        Probe probe = probeListAdapter.getItem(position);
                        updateSelectedProbe(probe);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                }
        );

        updatePermissionLabel();
    }

    private Probe getSelectedProbe() {
        int position = binding.probeList.getSelectedItemPosition();
        if (position != AdapterView.INVALID_POSITION) {
            return probeListAdapter.getItem(position);
        }
        return null;
    }

    private void updateSelectedProbe(Probe probe) {
        if (probe == null) {
            Log.d("WifiFragment", "Selected probe is null");
            return;
        }
        Log.d("WifiFragment", "Select probe " + probe.name);
        if (probe.wifiInfo != null) {
            Log.d("WifiFragment", "Set Probe wifi info");
            binding.wifiPrefix.setText(probe.wifiInfo.ssid);
            binding.wifiPassword.setText(probe.wifiInfo.passphrase);
        }
    }
}