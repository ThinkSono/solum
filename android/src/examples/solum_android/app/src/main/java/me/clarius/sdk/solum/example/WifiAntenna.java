package me.clarius.sdk.solum.example;

import android.app.Application;
import android.content.Context;
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

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

public class WifiAntenna extends AndroidViewModel {
    public final MutableLiveData<Network> network = new MutableLiveData<>(null);
    public final MutableLiveData<Boolean> tryingToConnect = new MutableLiveData<>(false);

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
