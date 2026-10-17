package com.example.hardware;

import android.content.Context;
import android.os.UserManager;
import android.provider.Settings;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

final class ProtectionStatus {
    static final class Item {
        final String title, state, detail;
        final int level; // 0 enforced, 1 attention, 2 vendor remediation
        Item(String title, String state, String detail, int level) { this.title=title; this.state=state; this.detail=detail; this.level=level; }
    }
    final List<Item> items = new ArrayList<>();
    String summary;
    boolean bridge;
    static String property(String name) {
        try {
            java.lang.Process process = new ProcessBuilder("/system/bin/getprop", name).start();
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[256]; int count;
            while ((count = process.getInputStream().read(buffer)) != -1 && out.size() < 4096) out.write(buffer,0,count);
            return new String(out.toByteArray(),StandardCharsets.UTF_8).trim();
        } catch (Exception error) { return ""; }
    }
    private static String readFile(String path) {
        try (FileInputStream input = new FileInputStream(path)) {
            byte[] bytes = new byte[64]; int count=input.read(bytes);
            return count<0?"":new String(bytes,0,count,StandardCharsets.UTF_8).trim();
        } catch (Exception error) { return ""; }
    }
    static ProtectionStatus read(Context context) {
        ProtectionStatus result = new ProtectionStatus(); SecurityPolicy policy = new SecurityPolicy(context);
        JSONObject privileged = null;
        try { privileged=RootBridge.request("STATUS"); result.bridge=true; } catch (Exception ignored) { }
        String usb = property("sys.usb.config");
        String usbEnabled=privileged==null?readFile("/sys/class/android_usb/android0/enable"):privileged.optString("usb_enabled");
        boolean usbOff = "none".equals(usb) && "0".equals(usbEnabled);
        int adb = Settings.Global.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, -1);
        boolean debug = policy.restricted(UserManager.DISALLOW_DEBUGGING_FEATURES) && adb == 0;
        boolean installs = policy.restricted(UserManager.DISALLOW_INSTALL_APPS) && policy.restricted(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
        boolean transfer = policy.restricted(UserManager.DISALLOW_USB_FILE_TRANSFER);
        result.items.add(new Item("USB handshakes and accessories", usbOff ? "USB data disabled" : "USB seal not verified", "Disables the USB data gadget, including the vendor ADB, MTP, serial and accessory entry points. Current USB mode: " + (usb.isEmpty()?"unavailable":usb) + ".", usbOff?0:1));
        result.items.add(new Item("Debugging and command access", debug?"Blocked by device policy":"Not blocked by Velox", "Prevents USB debugging from being enabled in Android. The vendor interface also requires the USB seal above.", debug?0:1));
        result.items.add(new Item("App sideloading and updates", installs?"New installations blocked":"Install block is off", "Blocks new app installations, including manual APKs and PAXSTORE updates. Existing apps keep running. Enter PIN-protected maintenance to update apps.", installs?0:1));
        result.items.add(new Item("USB file transfer", transfer?"Blocked by device policy":"File-transfer block is off", "Stops files being copied through Android USB file transfer. Wi-Fi and payment networking stay available.", transfer?0:1));
        boolean verify = policy.restricted(UserManager.ENSURE_VERIFY_APPS) && "yes".equals(property("ro.build.paxdroid"))
            && Settings.Global.getInt(context.getContentResolver(),"package_verifier_enable",0)==1;
        result.items.add(new Item("App verification", verify?"Verification policy required":"Review verification settings", "Requires Android app verification and checks that the PAX installation gate is enabled. A process that already has root can still change memory properties.", verify?0:1));
        boolean pin = new AdminPin(context).configured();
        boolean managed=pin&&policy.owner()&&policy.active();
        boolean allRestrictions=true;
        for (String restriction:SecurityPolicy.RESTRICTIONS) allRestrictions &= policy.restricted(restriction);
        boolean uninstallBlocked=false;
        if (policy.owner()) try { uninstallBlocked=policy.manager.isUninstallBlocked(policy.admin,context.getPackageName()); } catch (RuntimeException ignored) { }
        boolean administration=managed&&allRestrictions&&uninstallBlocked;
        result.items.add(new Item("Settings and maintenance", administration?"PIN and management policy active":"Administrator protection incomplete", "An 8–32 digit PIN protects Velox controls. Active policy blocks removing Velox, app controls, safe boot and factory reset from Settings. Hardware recovery is separate.",administration?0:1));
        String tcp = property("service.adb.tcp.port"), persistentTcp=property("persist.adb.tcp.port");
        boolean adbStopped="stopped".equals(property("init.svc.adbd"));
        boolean tcpOff = "-1".equals(tcp) && "-1".equals(persistentTcp) && debug && adbStopped;
        result.items.add(new Item("Wi-Fi debugging", tcpOff?"TCP debugging disabled":"TCP debugging not verified", "USB sealing also disables the configured ADB TCP ports. It does not intercept payment traffic or office Wi-Fi traffic.",tcpOff?0:1));
        result.items.add(new Item("Vendor privileged-command weakness", "PAX firmware fix required", "Closing USB removes the entry point used in our tests. This app cannot patch the vendor command parser or promise to contain a process that already has root.",2));
        String enforcing=readFile("/sys/fs/selinux/enforce");
        result.items.add(new Item("SELinux security boundary", "1".equals(enforcing)?"Kernel reports enforcing":"Vendor policy review required", "SELinux must be enforced by supported firmware. This app does not change kernel policy or treat a permissive system as hardened.","1".equals(enforcing)?0:2));
        String verified=property("ro.boot.verifiedbootstate"), locked=property("ro.boot.flash.locked");
        result.items.add(new Item("Bootloader, recovery and OS integrity", "Firmware enforcement required", "An Android app cannot block physical recovery, flashing or every processor mode. PAX must enforce signed recovery, a locked bootloader and verified boot. Reported boot state: " + (verified.isEmpty()?"unavailable":verified) + "; lock flag: " + (locked.isEmpty()?"unavailable":locked) + ".",2));
        boolean complete=usbOff&&"none".equals(property("persist.sys.usb.config"))&&debug&&installs&&transfer&&verify&&administration&&tcpOff;
        result.summary=policy.prefs.getBoolean("usb_restore_pending",false)?"Maintenance incomplete: USB restoration needs attention":
            policy.active()?(complete?"Access controls active":"Protection needs attention"):"Protection is not enabled";
        return result;
    }
}
