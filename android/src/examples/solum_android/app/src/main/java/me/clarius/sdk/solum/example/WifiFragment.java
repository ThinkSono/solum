package me.clarius.sdk.solum.example;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
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

import java.util.Map;

import me.clarius.sdk.solum.example.databinding.FragmentWifiBinding;

public class WifiFragment extends Fragment {

    private FragmentWifiBinding binding;
    private Antenna antenna;
    private boolean connectButtonEnabled = true;

    private boolean permissionsGranted = false;

    private final ActivityResultLauncher<String[]> wifiPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionUpdate);

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void requestWifiPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CHANGE_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED) {
            onPermissionUpdate(null);
        }
        wifiPermissionLauncher.launch(new String[]{Manifest.permission.CHANGE_NETWORK_STATE});
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
        antenna = new ViewModelProvider(requireActivity()).get(Antenna.class);
        binding = FragmentWifiBinding.bind(view);

        binding.connectWifi.setOnClickListener(v -> {
            if (connectButtonEnabled) {
                String ssid = binding.wifiPrefix.getText().toString();
                String passphrase = binding.wifiPassword.getText().toString();
                antenna.ssid.setValue(ssid);
                antenna.passphrase.setValue(passphrase);
                antenna.connectWifi();
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

        antenna.ssid.observe(getViewLifecycleOwner(), ssid -> {
            binding.wifiPrefix.setText(ssid);
        });

        antenna.passphrase.observe(getViewLifecycleOwner(), passphrase -> {
            binding.wifiPassword.setText(passphrase);
        });

        updatePermissionLabel();
    }
}