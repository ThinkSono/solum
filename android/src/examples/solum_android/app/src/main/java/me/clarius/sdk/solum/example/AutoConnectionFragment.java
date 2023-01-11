package me.clarius.sdk.solum.example;

import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.clarius.sdk.Button;
import me.clarius.sdk.Connection;
import me.clarius.sdk.ImagingState;
import me.clarius.sdk.Platform;
import me.clarius.sdk.PosInfo;
import me.clarius.sdk.PowerDown;
import me.clarius.sdk.ProbeSettings;
import me.clarius.sdk.ProcessedImageInfo;
import me.clarius.sdk.RawImageInfo;
import me.clarius.sdk.Solum;
import me.clarius.sdk.SpectralImageInfo;
import me.clarius.sdk.solum.example.databinding.FragmentAutoConnectionBinding;

public class AutoConnectionFragment extends Fragment {

    private ProbeStore probeStore;
    private BluetoothAntenna btAntenna;
    private WifiAntenna wifiAntenna;

    private FragmentAutoConnectionBinding binding;
    private ArrayAdapter<Probe> probeAdapter;

    private Solum solum;
    private ImageConverter imageConverter;
    private final ExecutorService executorService = Executors.newFixedThreadPool(1);
    public final MutableLiveData<Bitmap> processedImage = new MutableLiveData<>();

    public static final String TAG = "AutoConnectionFragment";

    private final Solum.Listener solumListener = new Solum.Listener() {
        @Override
        public void error(String msg) {
            showError(msg);
        }

        @Override
        public void connectionResult(Connection result, int port, String status) {
            Log.d(TAG, "Connection result: " + result + ", port: " + port + ", status: " + status);
            if (result == Connection.ProbeConnected) {
                AutoConnectionFragment.this.solumnConnected = true;
                showMessage("Connected");
            } else if (result == Connection.SwUpdateRequired) {
                AutoConnectionFragment.this.solumnConnected = true;
                showMessage("Firmware update needed");
            } else {
                AutoConnectionFragment.this.solumnConnected = false;
                showMessage("Disconnected");
            }
            binding.getRoot().post(() ->  { nextStep(); updateChecklist(); });
        }

        @Override
        public void certInfo(int daysValid) {
            updateCertificateStatus(daysValid);
            showMessage("Days valid for cert: " + daysValid);
        }

        @Override
        public void imaging(ImagingState state, boolean imaging) {
            showMessage("Imaging state: " + state + " imaging? " + imaging);
        }

        @Override
        public void newProcessedImage(ByteBuffer buffer, ProcessedImageInfo info, PosInfo[] pos) {
            showMessage("new image");
            imageConverter.convertImage(buffer, info);
        }

        @Override
        public void newRawImageFn(ByteBuffer buffer, RawImageInfo info, PosInfo[] pos) {
        }

        @Override
        public void newSpectralImageFn(ByteBuffer buffer, SpectralImageInfo info) {
        }

        @Override
        public void poweringDown(PowerDown reason, int seconds) {
            Log.d(TAG, "Powering down in " + seconds + " seconds (reason: " + reason + ")");
        }

        @Override
        public void buttonPressed(Button button, int count) {
            Log.d(TAG, "Button '" + button + "' pressed, count: " + count);
        }
    };

    private String getCertDir() {
        return requireContext().getDir("cert", Context.MODE_PRIVATE).toString();
    }

    private String getCertificate() {
        return "certificate"; // get certificate from https://cloud.clarius.com/api/public/v0/devices/oem/
    }

    private void updateCertificateStatus(int daysValid) {
        binding.getRoot().post(() -> {
            String text = "N/A";
            certificateValid = daysValid >= 0;
            if (certificateValid) {
                text = "Valid for " + daysValid + " days";
            } else {
                text = "Invalid";
            }
            binding.certificateValid.setText(text);
            nextStep();
            updateChecklist();
        });
    }

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

        solum = new Solum(requireContext(), solumListener);
        solum.initialize(getCertDir(), new Solum.InitializationResult() {
            @Override
            public void accept(boolean connected) {
                Log.d(TAG, "Initialization result: " + connected);
                if (connected) {
                    solum.setCertificate(getCertificate());
                    solum.getFirmwareVersion(Platform.HD,
                            maybeVersion -> showMessage("Retrieved FW version: " + maybeVersion.orElse("???")));
                }
            }
        });
        ProbeSettings probeSettings = new ProbeSettings();
        solum.setProbeSettings(probeSettings);
        imageConverter = new ImageConverter(executorService, new ImageCallback(processedImage));
        processedImage.observe(getViewLifecycleOwner(), binding.imageView::setImageBitmap);

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
            if (currentStep == Step.SELECT_PROBE) {
                shouldConnect = true;
                nextStep();
                updateChecklist();
            } else {
                reset();
                solum.powerDown();
                solum.disconnect();
                wifiAntenna.disconnectWifi();
                btAntenna.disconnect();
                updateChecklist();
            }
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

        binding.toggleImaging.setOnClickListener(v -> {
            imaging = !imaging;
            solum.run(imaging);
            updateChecklist();
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
                btAntenna.connect(selectedProbe.bluetoothAddr, false);
            } break;
            case POWER_PROBE: {
                byte[] payload = new byte[]{1};
                btAntenna.writeCharacteristic(BluetoothAntenna.powerServiceUUID, BluetoothAntenna.powerRequestUUID, payload);
            } break;
            case WAIT_WIFI: break;
            case CONNECT_WIFI: {
                wifiAntenna.connectWifi(selectedProbe);
            } break;
            case CONNECT_SOLUM: {
                solum.connect(selectedProbe.wifiInfo.ipAddr, selectedProbe.wifiInfo.controlPort);
            } break;
            case CHECK_CERTIFICATE: break;
            case LOAD_APPLICATION: {
                solum.loadApplication("L7HD", "vascular");
                applicationLoaded = true;
                nextStep();
            } break;
        }
    }

    public Step transitionStateMachine() {
        Probe selectedProbe = getSelectedProbe();
        switch(currentStep) {
            case SELECT_PROBE: {
                if (selectedProbe != null && shouldConnect) {
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
            case CONNECT_SOLUM: {
                if (!wifiConnected) {
                    return Step.CONNECT_WIFI;
                }
                if (solumnConnected) {
                    return Step.CHECK_CERTIFICATE;
                }
            } break;
            case CHECK_CERTIFICATE:
                if (!solumnConnected) {
                    return Step.CONNECT_SOLUM;
                }
                if (certificateValid) {
                    return Step.LOAD_APPLICATION;
                }
            case LOAD_APPLICATION: {
                if (!solumnConnected) {
                    return Step.CONNECT_SOLUM;
                }
                if (applicationLoaded) {
                    return Step.IMAGING_READY;
                }
            } break;
            case IMAGING_READY: {
                if (!solumnConnected) {
                    return Step.CONNECT_SOLUM;
                }
                if (imaging) {
                    return Step.IMAGING;
                }
            } break;
            case IMAGING: {
                if (!solumnConnected) {
                    return Step.CONNECT_SOLUM;
                }
                if (!imaging) {
                    return Step.IMAGING_READY;
                }
            }
        }

        return currentStep;
    }

    private void updateChecklist() {
        Probe selectedProbe = getSelectedProbe();
        String wifiInfoText = "N/A";
        if (selectedProbe != null && selectedProbe.wifiInfo != null) {
            wifiInfoText = selectedProbe.wifiInfo.toString();
        }

        if (currentStep == Step.SELECT_PROBE) {
            binding.connectToProbe.setText("Connect");
        } else {
            binding.connectToProbe.setText("Disconnect");
        }
        binding.currentStep.setText(currentStep.toString());
        binding.bluetoothConnected.setText(Boolean.toString(bluetoothConnected));
        binding.probePowered.setText(Boolean.toString(probePowered));
        binding.wifiInfo.setText(wifiInfoText);
        binding.wifiConnected.setText(Boolean.toString(wifiConnected));
        binding.solumConnected.setText(Boolean.toString(solumnConnected));
        binding.loadedApplication.setText(Boolean.toString(applicationLoaded));
        binding.imaging.setText(Boolean.toString(imaging));
        binding.toggleImaging.setEnabled(solumnConnected && applicationLoaded && certificateValid);
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
        CHECK_CERTIFICATE,
        LOAD_APPLICATION,
        IMAGING_READY,
        IMAGING,
        EOC,
    }

    private Step currentStep = Step.SELECT_PROBE;
    private boolean shouldConnect = false;
    private boolean bluetoothConnected = false;
    private boolean probePowered = false;
    private boolean wifiEnabled = false;
    private boolean wifiConnected = false;
    private boolean solumnConnected = false;
    private boolean certificateValid = false;
    private boolean applicationLoaded = false;
    private boolean imaging = false;

    private void reset() {
        currentStep = Step.SELECT_PROBE;
        shouldConnect = false;
        bluetoothConnected = false;
        probePowered = false;
        wifiEnabled = false;
        wifiConnected = false;
        solumnConnected = false;
        applicationLoaded = false;
        imaging = false;
    }

    private void showMessage(String msg) {
        Log.d(TAG, msg);
    }

    private void showError(String err) {
        Log.e(TAG, err);
    }

    private class ImageCallback implements ImageConverter.Callback {
        private final MutableLiveData<Bitmap> dest;

        ImageCallback(MutableLiveData<Bitmap> dest) {
            this.dest = dest;
        }

        @Override
        public void onResult(Bitmap bitmap) {
            dest.postValue(bitmap);
        }

        @Override
        public void onError(Exception e) {
            showError("Error while converting image: " + e);
        }
    }
}