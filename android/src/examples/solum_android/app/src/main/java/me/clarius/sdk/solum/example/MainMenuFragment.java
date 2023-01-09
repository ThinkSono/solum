package me.clarius.sdk.solum.example;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import me.clarius.sdk.solum.example.databinding.FragmentMainMenuBinding;

public class MainMenuFragment extends Fragment {

    private FragmentMainMenuBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_main_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentMainMenuBinding.bind(view);

        binding.buttonBluetooth.setOnClickListener(view1 -> NavHostFragment.findNavController(MainMenuFragment.this)
                .navigate(R.id.action_mainMenuFragment_to_BluetoothFragment));

        binding.buttonWifi.setOnClickListener(v -> NavHostFragment.findNavController(MainMenuFragment.this)
                .navigate(R.id.action_mainMenuFragment_to_wifiFragment));

        binding.buttonSolum.setOnClickListener(v -> NavHostFragment.findNavController(MainMenuFragment.this)
                .navigate(R.id.action_mainMenuFragment_to_FirstFragment));

        binding.buttonAutoConnection.setOnClickListener(v -> NavHostFragment.findNavController(MainMenuFragment.this)
                .navigate(R.id.action_mainMenuFragment_to_probeFragment));
    }
}