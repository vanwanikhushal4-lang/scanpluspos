package com.example.hardware;

import org.junit.Test;
import static org.junit.Assert.*;

public class WifiSecurityTest {
    @Test public void distinguishesOpenEncryptedAndUnknownCapabilities(){
        assertEquals("OPEN",WifiSecurity.classify("[ESS][WPS]"));
        assertEquals("OPEN",WifiSecurity.classify("[OWE_TRANSITION][ESS]"));
        assertEquals("ENHANCED_OPEN",WifiSecurity.classify("[OWE][ESS]"));
        assertEquals("WPA2",WifiSecurity.classify("[WPA2-PSK-CCMP][ESS]"));
        assertEquals("WPA3",WifiSecurity.classify("[RSN-SAE-CCMP][ESS]"));
        assertEquals("ENTERPRISE",WifiSecurity.classify("[WPA2-EAP-CCMP][ESS]"));
        assertEquals("WEP",WifiSecurity.classify("[WEP][ESS]"));
        assertEquals("UNKNOWN",WifiSecurity.classify("[VENDOR-SECURITY][ESS]"));
        assertEquals("UNKNOWN",WifiSecurity.classify(null));
    }
}
