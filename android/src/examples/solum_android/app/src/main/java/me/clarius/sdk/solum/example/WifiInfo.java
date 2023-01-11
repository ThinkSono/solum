package me.clarius.sdk.solum.example;

import androidx.annotation.NonNull;

public class WifiInfo {
    public String state;
    public String ipAddr;
    public String ssid;
    public String bssid;
    public String passphrase;
    public int controlPort;
    public int castPort;

    @NonNull
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("state: ").append(this.state);
        builder.append("\nssid: ").append(this.ssid);
        builder.append("\nbssid: ").append(this.bssid);
        builder.append("\npassphrase: ").append(this.passphrase);
        builder.append("\nipAddr: ").append(this.ipAddr);
        builder.append("\ncontrolPort: ").append(this.controlPort);
        builder.append("\ncastPort: ").append(this.castPort);
        return builder.toString();
    }

    public static WifiInfo fromPayload(String payload) {
        String[] tokens = payload.split("\n");
        WifiInfo info = new WifiInfo();
        for (String token : tokens) {
            String[] parts = token.split(":");
            if (parts.length != 2) {
                continue;
            }
            String key = parts[0].trim();
            String value = parts[1].trim();
            switch (key) {
                case "state":
                    info.state = value;
                    break;
                case "ip4":
                    info.ipAddr = value;
                    break;
                case "ssid":
                    info.ssid = value;
                    break;
                case "pw":
                    info.passphrase = value;
                    break;
                case "ctl":
                    try {
                        info.controlPort = Integer.parseInt(value, 10);
                    } catch (NumberFormatException ignored) {
                    }
                    break;
                case "cast":
                    try {
                        info.castPort = Integer.parseInt(value, 10);
                    } catch (NumberFormatException ignored) {
                    }
                    break;
            }
        }
        return info;
    }
}
