package me.clarius.sdk.solum.example;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class ProbeFragment extends Fragment {

    private ProbeStore probeStore;
    private BluetoothAntenna btAntenna;
    private WifiAntenna wifiAntenna;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_probe, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewModelProvider provider = new ViewModelProvider(requireActivity());
        probeStore = provider.get(ProbeStore.class);
        btAntenna = provider.get(BluetoothAntenna.class);
        wifiAntenna = provider.get(WifiAntenna.class);
    }
}