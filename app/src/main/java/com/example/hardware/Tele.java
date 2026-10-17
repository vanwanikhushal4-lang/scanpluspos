package com.example.hardware;
import android.os.Bundle;
import android.telephony.TelephonyManager;
public class Tele extends DiagnosticActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TelephonyManager manager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        if (manager == null) { status.setText("Telephony not available"); return; }
        String sim;
        switch (manager.getSimState()) {
            case TelephonyManager.SIM_STATE_ABSENT: sim = "No SIM inserted"; break;
            case TelephonyManager.SIM_STATE_READY: sim = "SIM ready"; break;
            case TelephonyManager.SIM_STATE_PIN_REQUIRED: sim = "SIM PIN required"; break;
            case TelephonyManager.SIM_STATE_PUK_REQUIRED: sim = "SIM PUK required"; break;
            case TelephonyManager.SIM_STATE_NETWORK_LOCKED: sim = "SIM network locked"; break;
            default: sim = "SIM state unavailable";
        }
        status.setText(sim);
        detail.setText("This test reads the SIM state. It does not place calls or change SIM settings.");
    }
}
