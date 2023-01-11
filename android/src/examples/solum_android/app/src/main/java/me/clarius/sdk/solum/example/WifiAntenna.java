package me.clarius.sdk.solum.example;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import java.util.List;
import java.util.Map;

public class WifiAntenna extends AndroidViewModel {
    public final MutableLiveData<Network> network = new MutableLiveData<>(null);
    public final MutableLiveData<Boolean> tryingToConnect = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> permissionsGranted = new MutableLiveData<>(false);

    @RequiresApi(api = Build.VERSION_CODES.S)
    public void requestWifiPermissions(ActivityResultLauncher<String[]> launcher) {
        String[] permissions = new String[]{Manifest.permission.CHANGE_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
        boolean hasAllPermissions = true;
        for (String permission : permissions) {
            hasAllPermissions &= ContextCompat.checkSelfPermission(getApplication(), permission) == PackageManager.PERMISSION_GRANTED;
        }

        if (!hasAllPermissions) {
            launcher.launch(permissions);
        } else {
            permissionsGranted.postValue(true);
        }
    }

    public void onWifiPermissionsUpdate(Map<String, Boolean> results) {
        boolean hasAllPermissions = true;
        if (results != null) {
            for (boolean value : results.values()) {
                hasAllPermissions &= value;
            }
        }
        permissionsGranted.postValue(hasAllPermissions);
    }

    private ProbeStore probeStore;

    private ConnectivityManager.NetworkCallback networkCallback;

    public WifiAntenna(@NonNull Application application) {
        super(application);
    }

    public void setProbeStore(ProbeStore probeStore) {
        this.probeStore = probeStore;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public void connectWifi(Probe probe) {
        if (probe == null || probe.wifiInfo == null || probe.wifiInfo.ssid == null || probe.wifiInfo.passphrase == null) {
            return;
        }
        connectWifi(probe.wifiInfo.ssid, probe.wifiInfo.passphrase, probe);
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public void connectWifi(String ssid, String passphrase, Probe probe) {
        Log.d("Antenna", "Connecting to wifi with ssid " + ssid + " and passphrase " + passphrase);

        final WifiNetworkSpecifier.Builder builder = new WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(passphrase);

        if(probe != null && probe.bssid != null) {
            builder.setBssid(MacAddress.fromString(probe.bssid));
        }

        final NetworkSpecifier specifier = builder.build();

        final NetworkRequest request =
                new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .setNetworkSpecifier(specifier)
                        .build();

        final ConnectivityManager connectivityManager = (ConnectivityManager)
                getApplication().getSystemService(Context.CONNECTIVITY_SERVICE);

        networkCallback = new ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                Log.d("WifiAntenna", "onAvailable");
                WifiAntenna.this.network.postValue(network);
                connectivityManager.bindProcessToNetwork(network);
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities);
                WifiInfo wifiInfo = (WifiInfo) networkCapabilities.getTransportInfo();
                String bssid = wifiInfo.getBSSID();
                if (!bssid.equals("02:00:00:00:00:00")) {
                    if (probe != null) {
                        probe.bssid = bssid;
                        if (probeStore != null) {
                            probeStore.probeUpdated.postValue(probe);
                        }
                    }
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                Log.w("WifiFragment", "onLost");
                WifiAntenna.this.network.postValue(null);
            }

            @Override
            public void onUnavailable() {
                super.onUnavailable();
                Log.w("WifiFragment", "onUnavailable");
                WifiAntenna.this.network.postValue(null);
            }
        };
        connectivityManager.requestNetwork(request, networkCallback);
        tryingToConnect.postValue(true);
    }

    public void disconnectWifi() {
        if (networkCallback != null) {
            final ConnectivityManager connectivityManager = (ConnectivityManager)
                    getApplication().getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.unregisterNetworkCallback(networkCallback);
            networkCallback = null;
            connectivityManager.bindProcessToNetwork(null);
        }
        tryingToConnect.postValue(false);
        network.postValue(null);
    }
}
