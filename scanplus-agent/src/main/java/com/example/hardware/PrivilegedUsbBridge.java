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
import android.app.AppOpsManager;
import java.util.Arrays;

/**
 * Explicitly provisioned by the device administrator, never self-elevates.
 * Only the installed application's Linux UID can use this local socket.
 * Fixed USB, force-stop, removal and permission app-operation controls for user apps. No shell strings,
 * file-path parameters, APK installs, or network listener.
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
    private static void userApp(String pkg)throws Exception{
        if(!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")||pkg.equals("dev.velox.hardwaresecurity")||pkg.equals("com.android.systemui")||pkg.equals("com.android.settings")||pkg.endsWith(".packageinstaller"))throw new SecurityException("Protected application.");
        if(!command("/system/bin/pm","path",pkg).startsWith("package:/data/app/")||Integer.parseInt(command("/system/bin/stat","-c","%u","/data/user/0/"+pkg))<10000)throw new SecurityException("Only installed user applications may be managed.");
    }
    private static JSONObject permission(String operation)throws Exception{
        String[] fields=operation.split(":",4);if(fields.length!=4)throw new SecurityException("Invalid permission request.");
        String mode=fields[1],pkg=fields[2],name=fields[3];userApp(pkg);
        if(!Arrays.asList("STATUS","DENY","DEFAULT").contains(mode)||!Arrays.asList("CAMERA","RECORD_AUDIO","ACCESS_FINE_LOCATION","ACCESS_COARSE_LOCATION","READ_CONTACTS","WRITE_CONTACTS","GET_ACCOUNTS","READ_PHONE_STATE","CALL_PHONE","READ_CALL_LOG","WRITE_CALL_LOG","ADD_VOICEMAIL","USE_SIP","PROCESS_OUTGOING_CALLS","READ_CALENDAR","WRITE_CALENDAR","READ_EXTERNAL_STORAGE","WRITE_EXTERNAL_STORAGE","BODY_SENSORS","SEND_SMS","RECEIVE_SMS","READ_SMS","RECEIVE_WAP_PUSH","RECEIVE_MMS").contains(name.replace("android.permission.",""))||!name.startsWith("android.permission."))throw new SecurityException("Unsupported runtime permission.");
        String mapped=AppOpsManager.permissionToOp(name);if(mapped==null||!mapped.startsWith("android:"))throw new SecurityException("Firmware has no app operation for this permission.");
        String op=mapped.substring("android:".length()).toUpperCase(java.util.Locale.US);
        if(!mode.equals("STATUS"))command("/system/bin/appops","set","--user","0",pkg,op,mode.equals("DENY")?"ignore":"default");
        String observed=command("/system/bin/appops","get","--user","0",pkg,op).toLowerCase(java.util.Locale.US);
        boolean blocked=observed.contains(": ignore")||observed.contains(": deny")||observed.contains("mode=ignore")||observed.contains("mode=deny");
        if(mode.equals("DENY")&&!blocked||mode.equals("DEFAULT")&&blocked)throw new IllegalStateException("App-operation policy was not observed.");
        return new JSONObject().put("ok",true).put("operation",op).put("blocked",blocked);
    }
    private static JSONObject uninstall(String pkg)throws Exception{
        userApp(pkg);
        // Independently refuse removal while protection or USB restoration is active.
        boolean maintenance=false;
        org.xmlpull.v1.XmlPullParser parser=android.util.Xml.newPullParser();
        try(FileInputStream input=new FileInputStream("/data/user/0/dev.velox.hardwaresecurity/shared_prefs/protection.xml")){
            parser.setInput(input,"UTF-8");
            for(int event=parser.getEventType();event!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT;event=parser.next())if(event==org.xmlpull.v1.XmlPullParser.START_TAG&&parser.getName().equals("boolean")){
                String name=parser.getAttributeValue(null,"name"),value=parser.getAttributeValue(null,"value");
                if("active".equals(name)){if(!"false".equals(value))throw new SecurityException("Maintenance mode required.");maintenance=true;}
                if("usb_restore_pending".equals(name)&&!"false".equals(value))throw new SecurityException("Wait for USB restoration.");
            }
        }
        if(!maintenance)throw new SecurityException("Confirmed maintenance mode required.");
        for(String entry:command("/system/bin/pm","list","packages","-s",pkg).split("\\n"))if(entry.trim().equals("package:"+pkg))throw new SecurityException("System application is protected.");
        String result=command("/system/bin/pm","uninstall","--user","0",pkg);
        if(!result.equals("Success"))throw new IllegalStateException("User-app removal failed: "+result);
        for(String entry:command("/system/bin/pm","list","packages","--user","0",pkg).split("\\n"))if(entry.trim().equals("package:"+pkg))throw new IllegalStateException("Package remains installed.");
        return new JSONObject().put("ok",true).put("removed",true);
    }
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
            .put("tcp_port", property("service.adb.tcp.port")).put("appControlVersion",3);
    }
    public static void main(String[] arguments) throws Exception {
        if (Process.myUid() != 0 || arguments.length != 1) throw new SecurityException("Administrator provisioning is required.");
        final int clientUid = Integer.parseInt(arguments[0]);
        if (clientUid < 10000) throw new SecurityException("Invalid application identity.");
        try (LocalServerSocket server = new LocalServerSocket(RootBridge.socketName(clientUid))) {
            while (true) try (LocalSocket socket = server.accept()) {
                socket.setSoTimeout(3000);
                if (socket.getPeerCredentials().getUid() != clientUid) continue;
                JSONObject response=null;
                try {
                    String operation = new DataInputStream(socket.getInputStream()).readUTF();
                    synchronized (PrivilegedUsbBridge.class) {
                        switch (operation) {
                            case "STATUS": break;
                            case "PREPARE": backup(true); break;
                            case "SEAL": seal(); break;
                            case "RESTORE": restore(); break;
                            default:
                                if(operation.startsWith("APP_PERMISSION:")){response=permission(operation);break;}
                                if(operation.startsWith("APP_UNINSTALL:")){response=uninstall(operation.substring("APP_UNINSTALL:".length()));break;}
                                if(!operation.startsWith("APP_FORCE_STOP:"))throw new SecurityException("Unsupported management operation.");
                                String pkg=operation.substring("APP_FORCE_STOP:".length());
                                userApp(pkg);
                                command("/system/bin/am","force-stop",pkg);break;
                        }
                    }
                    if(response==null)response = status();
                } catch (Exception error) { response = new JSONObject().put("ok", false).put("error", error.getClass().getSimpleName() + ": " + error.getMessage()); }
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                output.writeUTF(response.toString()); output.flush();
            } catch (Exception error) { System.err.println(error); }
        }
    }
}
