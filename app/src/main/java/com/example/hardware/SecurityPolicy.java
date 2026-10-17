package com.example.hardware;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.UserManager;
import android.provider.Settings;

/** Uses real, persistent Android device-owner restrictions; never simulates enforcement. */
final class SecurityPolicy {
    static final String[] RESTRICTIONS = {
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, UserManager.DISALLOW_INSTALL_APPS,
        UserManager.ENSURE_VERIFY_APPS, UserManager.DISALLOW_USB_FILE_TRANSFER,
        UserManager.DISALLOW_SAFE_BOOT, UserManager.DISALLOW_ADD_USER,
        UserManager.DISALLOW_FACTORY_RESET, UserManager.DISALLOW_APPS_CONTROL,
        UserManager.DISALLOW_DEBUGGING_FEATURES
    };
    final Context context;
    final DevicePolicyManager manager;
    final ComponentName admin;
    final SharedPreferences prefs;
    SecurityPolicy(Context context) {
        this.context = context.getApplicationContext();
        manager = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        admin = new ComponentName(context, SecurityAdminReceiver.class);
        prefs = context.getSharedPreferences("protection", Context.MODE_PRIVATE);
    }
    boolean owner() { return manager != null && manager.isDeviceOwnerApp(context.getPackageName()); }
    boolean active() { return prefs.getBoolean("active", false); }
    boolean restricted(String name) {
        UserManager users = (UserManager) context.getSystemService(Context.USER_SERVICE);
        return users != null && users.hasUserRestriction(name);
    }
    synchronized void apply() throws Exception {
        if (!owner()) throw new SecurityException("Managed-device enrollment is required.");
        if (!active() && !prefs.getBoolean("snapshot", false)) {
            Bundle prior = manager.getUserRestrictions(admin);
            if (prior == null) prior = Bundle.EMPTY;
            SharedPreferences.Editor edit = prefs.edit().putBoolean("snapshot", true)
                .putInt("previous_adb", Settings.Global.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 0))
                .putBoolean("previous_uninstall", manager.isUninstallBlocked(admin, context.getPackageName()));
            for (String key : RESTRICTIONS) edit.putBoolean("previous_" + key, prior.getBoolean(key));
            if (!edit.commit()) throw new IllegalStateException("Policy recovery state could not be saved.");
        }
        try {
            // Mark intent before the final restriction can close the host's connection.
            if (!prefs.edit().putBoolean("active", true).commit()) throw new IllegalStateException("Protection state could not be saved.");
            manager.setUninstallBlocked(admin, context.getPackageName(), true);
            for (String key : RESTRICTIONS) manager.addUserRestriction(admin, key);
            for (String key : RESTRICTIONS) if (!restricted(key)) throw new IllegalStateException("Restriction not enforced: " + key);
        } catch (Exception error) {
            try { maintenance(false); } catch (Exception rollback) { error.addSuppressed(rollback); }
            throw error;
        }
    }
    synchronized void maintenance(boolean enableUsbDebugging) throws Exception {
        if (!owner()) throw new SecurityException("Managed-device enrollment is required.");
        if (prefs.getBoolean("snapshot", false)) {
            for (String key : RESTRICTIONS) {
                if (prefs.getBoolean("previous_" + key, false)) manager.addUserRestriction(admin, key);
                else manager.clearUserRestriction(admin, key);
            }
            manager.setUninstallBlocked(admin, context.getPackageName(), prefs.getBoolean("previous_uninstall", false));
        }
        boolean hadSnapshot=prefs.getBoolean("snapshot",false);
        if (enableUsbDebugging || hadSnapshot) {
            int previous=enableUsbDebugging?1:prefs.getInt("previous_adb",0);
            manager.setGlobalSetting(admin,Settings.Global.ADB_ENABLED,Integer.toString(previous));
        }
        if (!prefs.edit().putBoolean("active", false).putBoolean("snapshot", false).commit())
            throw new IllegalStateException("Maintenance state could not be saved. Retry maintenance.");
    }
}
