package com.example.hardware;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Process;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/**
 * Explicitly provisioned by the device administrator, never self-elevates.
 * Only the installed application's Linux UID can use this local socket.
 * USB-only operations, no shell strings, file-path parameters, APK installs, or network listener.
 */
public final class PrivilegedUsbBridge {
    private static final File DIRECTORY = new File("/data/local/velox-security");
    private static final File BACKUP = new File(DIRECTORY, "usb-original.json");
    private static String command(String... argv) throws Exception {
        java.lang.Process process = new ProcessBuilder(argv).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[512]; int read;
        while ((read = process.getInputStream().read(buffer)) != -1) {
            if (output.size() + read > 16384) { process.destroy(); throw new IllegalStateException("Unexpected command output."); }
            output.write(buffer, 0, read);
        }
        int exit = process.waitFor();
        String text = new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
        if (exit != 0) throw new IllegalStateException(argv[0] + " failed: " + text);
        return text;
    }
    private static String property(String key) throws Exception { return command("/system/bin/getprop", key); }
    private static void property(String key, String value) throws Exception { command("/system/bin/setprop", key, value); }
    private static String file(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[8192]; int length = input.read(bytes);
            return length < 0 ? "" : new String(bytes, 0, length, StandardCharsets.UTF_8).trim();
        }
    }
    private static void backup(boolean replace) throws Exception {
        if (BACKUP.exists() && !replace) return;
        // Never overwrite usable recovery state with the already-sealed USB configuration.
        if (replace && BACKUP.exists() && "none".equals(property("sys.usb.config")))
            throw new IllegalStateException("USB is already sealed. Restore the existing recovery state before enabling protection again.");
        if (!DIRECTORY.isDirectory() && !DIRECTORY.mkdirs()) throw new IllegalStateException("Cannot save USB recovery state.");
        command("/system/bin/chmod", "700", DIRECTORY.getAbsolutePath());
        JSONObject state = new JSONObject().put("persistent", property("persist.sys.usb.config"))
            .put("current", property("sys.usb.config"))
            .put("adb", command("/system/bin/settings", "get", "global", "adb_enabled"));
        File temporary = new File(DIRECTORY, "usb-original.tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(state.toString().getBytes(StandardCharsets.UTF_8)); output.getFD().sync();
        }
        command("/system/bin/chmod", "600", temporary.getAbsolutePath());
        if (!temporary.renameTo(BACKUP)) throw new IllegalStateException("USB recovery state could not be committed.");
    }
    private static synchronized void seal() throws Exception {
        backup(false);
        property("service.adb.tcp.port", "-1");
        property("persist.adb.tcp.port", "-1");
        property("persist.sys.usb.config", "none");
        property("sys.usb.config", "none");
        property("ctl.stop", "adbd");
        File enable = new File("/sys/class/android_usb/android0/enable");
        if (enable.exists()) try (FileOutputStream output = new FileOutputStream(enable)) { output.write('0'); }
    }
    private static synchronized void restore() throws Exception {
        if (!BACKUP.exists()) throw new IllegalStateException("No USB recovery state is available.");
        JSONObject prior = new JSONObject(file(BACKUP));
        property("persist.sys.usb.config", prior.getString("persistent"));
        command("/system/bin/settings", "put", "global", "adb_enabled", prior.getString("adb"));
        property("sys.usb.config", prior.getString("current"));
        if ("1".equals(prior.getString("adb"))) property("ctl.start", "adbd");
    }
    private static JSONObject status() throws Exception {
        File enable = new File("/sys/class/android_usb/android0/enable");
        return new JSONObject().put("ok", true).put("uid", Process.myUid())
            .put("usb_config", property("sys.usb.config"))
            .put("persistent_usb_config", property("persist.sys.usb.config"))
            .put("usb_enabled", enable.exists() ? file(enable) : "unknown")
            .put("adb_service", property("init.svc.adbd"))
            .put("tcp_port", property("service.adb.tcp.port"));
    }
    public static void main(String[] arguments) throws Exception {
        if (Process.myUid() != 0 || arguments.length != 1) throw new SecurityException("Administrator provisioning is required.");
        final int clientUid = Integer.parseInt(arguments[0]);
        if (clientUid < 10000) throw new SecurityException("Invalid application identity.");
        try (LocalServerSocket server = new LocalServerSocket(RootBridge.socketName(clientUid))) {
            while (true) try (LocalSocket socket = server.accept()) {
                socket.setSoTimeout(3000);
                if (socket.getPeerCredentials().getUid() != clientUid) continue;
                JSONObject response;
                try {
                    String operation = new DataInputStream(socket.getInputStream()).readUTF();
                    synchronized (PrivilegedUsbBridge.class) {
                        switch (operation) {
                            case "STATUS": break;
                            case "PREPARE": backup(true); break;
                            case "SEAL": seal(); break;
                            case "RESTORE": restore(); break;
                            default: throw new SecurityException("Unsupported USB operation.");
                        }
                    }
                    response = status();
                } catch (Exception error) { response = new JSONObject().put("ok", false).put("error", error.getClass().getSimpleName() + ": " + error.getMessage()); }
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                output.writeUTF(response.toString()); output.flush();
            } catch (Exception error) { System.err.println(error); }
        }
    }
}
