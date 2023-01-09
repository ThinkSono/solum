package me.clarius.sdk.solum.example;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.wifi.WifiNetworkSpecifier;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

public class WifiAntenna extends AndroidViewModel {
    public final MutableLiveData<Network> network = new MutableLiveData<>(null);
    public final MutableLiveData<Boolean> tryingToConnect = new MutableLiveData<>(false);
    public final MutableLiveData<String> ssid = new MutableLiveData<>("");
    public final MutableLiveData<String> passphrase = new MutableLiveData<>("");

    private ConnectivityManager.NetworkCallback networkCallback;

    public WifiAntenna(@NonNull Application application) {
        super(application);
    }

    public void connectWifi() {
        Log.d("Antenna", "Connecting to wifi with prefix " + ssid + " and password " + passphrase);
        final NetworkSpecifier specifier =
                new WifiNetworkSpecifier.Builder()
                        .setSsid(ssid.getValue())
                        .setWpa2Passphrase(passphrase.getValue())
                        .build();

        final NetworkRequest request =
                new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .setNetworkSpecifier(specifier)
                        .build();

        final ConnectivityManager connectivityManager = (ConnectivityManager)
                getApplication().getSystemService(Context.CONNECTIVITY_SERVICE);

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                WifiAntenna.this.network.postValue(network);
                connectivityManager.bindProcessToNetwork(network);
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
