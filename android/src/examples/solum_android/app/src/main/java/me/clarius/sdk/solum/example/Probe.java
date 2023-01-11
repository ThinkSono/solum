package me.clarius.sdk.solum.example;

import androidx.annotation.NonNull;

public class Probe {
    public String name;
    public String bluetoothAddr;
    public String bssid;
    public WifiInfo wifiInfo;
    public boolean powered;

    @NonNull
    @Override
    public String toString() {
        if (name != null) {
            return name;
        }
        return "Unknown probe";
    }
}
