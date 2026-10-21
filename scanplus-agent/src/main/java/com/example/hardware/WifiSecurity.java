package com.example.hardware;

import java.util.Locale;

final class WifiSecurity {
    private WifiSecurity() {}
    static String classify(String capabilities) {
        String value=capabilities==null?"":capabilities.toUpperCase(Locale.US);
        if(value.contains("EAP")||value.contains("IEEE8021X")) return "ENTERPRISE";
        if(value.contains("SAE")) return "WPA3";
        if(value.contains("WPA2")||value.contains("RSN")) return "WPA2";
        if(value.contains("WPA")) return "WPA";
        if(value.contains("WEP")) return "WEP";
        if(value.contains("OWE_TRANSITION")) return "OPEN"; // Includes an unencrypted compatibility path.
        if(value.contains("OWE")) return "ENHANCED_OPEN";
        String unknown=value.replaceAll("\\[(ESS|IBSS|WPS|WPS-PBC|WPS-PIN)\\]","");
        return !value.isEmpty()&&unknown.isEmpty()?"OPEN":"UNKNOWN";
    }
}
