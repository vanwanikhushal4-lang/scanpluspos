package com.example.hardware;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** Administrator installs a private bootstrap file; tokens never enter URLs, logs or APK resources. */
final class AgentConfiguration {
    final AtomicFile file;
    final JSONObject data;
    private final Context context;
    AgentConfiguration(Context context)throws Exception {
        this.context=context.getApplicationContext();
        file=new AtomicFile(new File(context.getFilesDir(),"scanplus-bootstrap.json"));
        byte[] bytes=file.readFully();
        if(bytes.length>16384)throw new SecurityException("Invalid provisioning file size.");
        data=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        URI uri=new URI(data.getString("baseUrl"));
        if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!(uri.getPath().isEmpty()||uri.getPath().equals("/")))
            throw new SecurityException("Backend must be an HTTPS origin.");
        String mode=trustMode();
        if(!mode.equals("public")&&!mode.equals("dedicated"))throw new SecurityException("Unknown backend trust mode.");
        if(mode.equals("dedicated")&&!data.has("caPem"))throw new SecurityException("Provisioning must include the dedicated backend certificate authority.");
    }
    String trustMode(){return data.optString("trustMode","dedicated");}
    String trustKey()throws Exception{return trustMode().equals("public")?"public-2026-10-17":data.getString("caPem");}
    InputStream trustBundle()throws Exception{
        return trustMode().equals("public")?context.getAssets().open("public-roots.crt"):
            new ByteArrayInputStream(data.getString("caPem").getBytes(StandardCharsets.US_ASCII));
    }
    String baseUrl()throws Exception {String url=data.getString("baseUrl");return url.endsWith("/")?url.substring(0,url.length()-1):url;}
    boolean enrolled(){return data.has("deviceId")&&data.has("deviceToken");}
    void saveIdentity(JSONObject identity)throws Exception {
        data.put("deviceId",identity.getString("deviceId"));data.put("deviceToken",identity.getString("deviceToken"));data.remove("enrollmentCode");
        FileOutputStream out=null;
        try {out=file.startWrite();out.write(data.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}
        catch(Exception error){if(out!=null)file.failWrite(out);throw error;}
    }
}
