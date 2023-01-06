package me.clarius.sdk.solum.example;

public class WifiInfo {
    public String state;
    public String ipAddr;
    public String ssid;
    public String passphrase;
    public int controlPort;
    public int castPort;

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
