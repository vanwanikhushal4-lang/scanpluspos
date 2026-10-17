package com.example.hardware;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Process;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.json.JSONObject;

/** Fixed-operation local channel. There is no network port or arbitrary command API. */
final class RootBridge {
    static final class Unavailable extends IOException {
        Unavailable(IOException cause) { super("Privileged USB service is unavailable.",cause); }
    }
    static String socketName(int uid) { return "velox.security." + uid; }
    static synchronized JSONObject request(String operation) throws Exception {
        try (LocalSocket socket = new LocalSocket()) {
            try { socket.connect(new LocalSocketAddress(socketName(Process.myUid()))); }
            catch (IOException unavailable) { throw new Unavailable(unavailable); }
            socket.setSoTimeout(operation.startsWith("APP_UNINSTALL:")?30000:5000);
            if (socket.getPeerCredentials().getUid() != 0) throw new SecurityException("Privileged-service identity check failed.");
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeUTF(operation); output.flush();
            JSONObject result = new JSONObject(new DataInputStream(socket.getInputStream()).readUTF());
            if (!result.optBoolean("ok")) throw new IllegalStateException(result.optString("error", "USB operation failed."));
            return result;
        }
    }
}
