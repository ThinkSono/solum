package me.clarius.sdk.solum.example;

import android.app.Activity;
import android.app.Application;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import java.nio.ByteBuffer;
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

public class SolumProbe extends AndroidViewModel {

    public ProbeStore probeStore;
    public BluetoothAntenna btAntenna;
    public WifiAntenna wifiAntenna;

    private boolean initialized = false;

    private Solum solum;
    private ImageConverter imageConverter;
    private final ExecutorService executorService = Executors.newFixedThreadPool(1);
    public final MutableLiveData<Bitmap> processedImage = new MutableLiveData<>();

    private String probeCereal = null; // yum!

    public void setProbeCereal(String cereal) {
        probeCereal = cereal;
    }

    public static final String TAG = "SolumProbe";

    private final Solum.Listener solumListener = new Solum.Listener() {
        @Override
        public void error(String msg) {
            showError(msg);
        }

        @Override
        public void connectionResult(Connection result, int port, String status) {
            Log.d(TAG, "Connection result: " + result + ", port: " + port + ", status: " + status);
            if (result == Connection.ProbeConnected) {
                SolumProbe.this.state.solumnConnected = true;
                showMessage("Connected");
            } else if (result == Connection.SwUpdateRequired) {
                SolumProbe.this.state.solumnConnected = true;
                showMessage("Firmware update needed");
            } else {
                SolumProbe.this.state.solumnConnected = false;
                showMessage("Disconnected");
            }
            nextStep();
        }

        @Override
        public void certInfo(int daysValid) {
            SolumProbe.this.state.certificateValid = daysValid >= 0;
            nextStep();
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

    public SolumProbe(@NonNull Application application) {
        super(application);
    }

    public void init(FragmentActivity activity) {
        if (initialized) {
            return;
        }
        initialized = true;

        ViewModelProvider provider = new ViewModelProvider(activity);
        probeStore = provider.get(ProbeStore.class);
        btAntenna = provider.get(BluetoothAntenna.class);
        wifiAntenna = provider.get(WifiAntenna.class);
        btAntenna.connectProbeStore(probeStore);

        solum = new Solum(getApplication(), solumListener);
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
        imageConverter = new ImageConverter(executorService, new SolumProbe.ImageCallback(processedImage));

        probeStore.probeUpdated.observeForever(probe -> {
            updateProbeState();
            nextStep();
        });

        btAntenna.connectionState.observeForever(state -> {
            SolumProbe.this.state.bluetoothConnected = state == BluetoothProfile.STATE_CONNECTED;
            nextStep();
        });

        wifiAntenna.network.observeForever(network -> {
            state.wifiConnected = network != null;
            nextStep();
        });
    }

    public void connect() {
        state.shouldConnect = true;
        if (getSelectedProbe() == null && btAntenna.scanStatus.getValue() != BluetoothAntenna.ScanStatus.SCANNING) {
            btAntenna.startScan();
        }
        nextStep();
    }

    public void disconnect() {
        reset();
        solum.powerDown();
        solum.disconnect();
        wifiAntenna.disconnectWifi();
        btAntenna.disconnect();
    }

    public void toggleImaging() {
        state.imaging = !state.imaging;
        solum.run(state.imaging);
    }

    private String getCertDir() {
        return getApplication().getDir("cert", Context.MODE_PRIVATE).toString();
    }

    private String getCertificate() {
        return ""; // get certificate from https://cloud.clarius.com/api/public/v0/devices/oem/
    }


    private synchronized void nextStep() {
        Log.d("SolumProbe", "nextStep");
        SolumProbe.Step originalStep = state.currentStep;
        SolumProbe.Step nextStep = state.currentStep;
        do {
            state.currentStep = nextStep;
            nextStep = transitionStateMachine();
        } while (state.currentStep != nextStep);

        stateLD.postValue(state);

        if (originalStep == state.currentStep) {
            return;
        }

        Probe selectedProbe = getSelectedProbe();

        switch (state.currentStep) {
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
                state.applicationLoaded = true;
                nextStep();
            } break;
        }
    }

    public SolumProbe.Step transitionStateMachine() {
        Probe selectedProbe = getSelectedProbe();
        switch(state.currentStep) {
            case SELECT_PROBE: {
                if (selectedProbe != null && state.shouldConnect) {
                    return SolumProbe.Step.CONNECT_BLUETOOTH;
                }
            } break;
            case CONNECT_BLUETOOTH: {
                if (selectedProbe == null) {
                    return SolumProbe.Step.SELECT_PROBE;
                }
                if (state.bluetoothConnected) {
                    return SolumProbe.Step.POWER_PROBE;
                }
            } break;
            case POWER_PROBE: {
                if (!state.bluetoothConnected) {
                    return SolumProbe.Step.CONNECT_BLUETOOTH;
                }
                if (state.probePowered) {
                    return SolumProbe.Step.WAIT_WIFI;
                }
            } break;
            case WAIT_WIFI: {
                if (!state.bluetoothConnected) {
                    return SolumProbe.Step.CONNECT_BLUETOOTH;
                }
                if (!state.probePowered) {
                    return SolumProbe.Step.POWER_PROBE;
                }
                if (state.wifiEnabled) {
                    return SolumProbe.Step.CONNECT_WIFI;
                }
            } break;
            case CONNECT_WIFI: {
                if (!state.wifiEnabled) {
                    return SolumProbe.Step.WAIT_WIFI;
                }
                if (state.wifiConnected) {
                    return SolumProbe.Step.CONNECT_SOLUM;
                }
            } break;
            case CONNECT_SOLUM: {
                if (!state.wifiConnected) {
                    return SolumProbe.Step.CONNECT_WIFI;
                }
                if (state.solumnConnected) {
                    return SolumProbe.Step.CHECK_CERTIFICATE;
                }
            } break;
            case CHECK_CERTIFICATE:
                if (!state.solumnConnected) {
                    return SolumProbe.Step.CONNECT_SOLUM;
                }
                if (state.certificateValid) {
                    return SolumProbe.Step.LOAD_APPLICATION;
                }
            case LOAD_APPLICATION: {
                if (!state.solumnConnected) {
                    return SolumProbe.Step.CONNECT_SOLUM;
                }
                if (state.applicationLoaded) {
                    return SolumProbe.Step.IMAGING_READY;
                }
            } break;
            case IMAGING_READY: {
                if (!state.solumnConnected) {
                    return SolumProbe.Step.CONNECT_SOLUM;
                }
                if (state.imaging) {
                    return SolumProbe.Step.IMAGING;
                }
            } break;
            case IMAGING: {
                if (!state.solumnConnected) {
                    return SolumProbe.Step.CONNECT_SOLUM;
                }
                if (!state.imaging) {
                    return SolumProbe.Step.IMAGING_READY;
                }
            }
        }

        return state.currentStep;
    }

    private void updateProbeState() {
        Probe selectedProbe = getSelectedProbe();
        if (selectedProbe == null) {
            state.probePowered = false;
            state.wifiEnabled = false;
            return;
        }

        state.probePowered = selectedProbe.powered;
        if (selectedProbe.wifiInfo == null) {
            state.wifiEnabled = false;
        } else {
            state.wifiEnabled = selectedProbe.wifiInfo.state.equals("connected");
        }
    }

    public Probe getSelectedProbe() {
        if (probeCereal == null) {
            return null;
        }
        return probeStore.probeMap.get(probeCereal);
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

    static class State {
        public SolumProbe.Step currentStep = SolumProbe.Step.SELECT_PROBE;
        public boolean shouldConnect = false;
        public boolean bluetoothConnected = false;
        public boolean probePowered = false;
        public boolean wifiEnabled = false;
        public boolean wifiConnected = false;
        public boolean solumnConnected = false;
        public boolean certificateValid = false;
        public boolean applicationLoaded = false;
        public boolean imaging = false;
    }

    public State state = new State();
    public MutableLiveData<State> stateLD = new MutableLiveData<>(state);

    private void reset() {
        state.currentStep = SolumProbe.Step.SELECT_PROBE;
        state.shouldConnect = false;
        state.bluetoothConnected = false;
        state.probePowered = false;
        state.wifiEnabled = false;
        state.wifiConnected = false;
        state.solumnConnected = false;
        state.applicationLoaded = false;
        state.imaging = false;
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
