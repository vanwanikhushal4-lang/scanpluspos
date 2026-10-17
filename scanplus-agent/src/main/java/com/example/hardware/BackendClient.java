package com.example.hardware;

import org.json.JSONObject;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;

final class BackendClient {
    private final AgentConfiguration config;
    private final SSLSocketFactory sockets;
    BackendClient(AgentConfiguration config)throws Exception {
        this.config=config;
        KeyStore trust=KeyStore.getInstance(KeyStore.getDefaultType());trust.load(null);
        try(InputStream pem=new ByteArrayInputStream(config.data.getString("caPem").getBytes(StandardCharsets.US_ASCII))){
            trust.setCertificateEntry("scanplus-server",CertificateFactory.getInstance("X.509").generateCertificate(pem));
        }
        TrustManagerFactory managers=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());managers.init(trust);
        SSLContext tls=SSLContext.getInstance("TLSv1.2");tls.init(null,managers.getTrustManagers(),null);sockets=tls.getSocketFactory();
    }
    JSONObject post(String path,JSONObject body,boolean authenticated)throws Exception {
        URL url=new URL(config.baseUrl()+path);
        HttpsURLConnection connection=(HttpsURLConnection)url.openConnection(Proxy.NO_PROXY);
        connection.setSSLSocketFactory(sockets); // Default hostname verification remains enabled.
        connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(15000);
        connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");
        if(authenticated){connection.setRequestProperty("Authorization","Bearer "+config.data.getString("deviceToken"));connection.setRequestProperty("X-Device-Id",config.data.getString("deviceId"));}
        byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);
        try {
            try(OutputStream output=connection.getOutputStream()){output.write(bytes);}
            int status=connection.getResponseCode();
            if(status!=200)throw new IOException("Backend returned HTTP "+status);
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream response=new ByteArrayOutputStream()){
                byte[] buffer=new byte[2048];int count;
                while((count=input.read(buffer))!=-1){if(response.size()+count>131072)throw new IOException("Backend response exceeds limit.");response.write(buffer,0,count);}
                return new JSONObject(new String(response.toByteArray(),StandardCharsets.UTF_8));
            }
        } finally {connection.disconnect();}
    }
    void downloadApk(String artifact,File destination,String expectedHash,long expectedSize)throws Exception {
        java.util.UUID.fromString(artifact);if(!expectedHash.matches("[a-f0-9]{64}")||expectedSize<1||expectedSize>64L*1024*1024)throw new SecurityException("Invalid APK metadata.");
        HttpsURLConnection connection=(HttpsURLConnection)new URL(config.baseUrl()+"/api/device/apks/"+artifact).openConnection(Proxy.NO_PROXY);
        connection.setSSLSocketFactory(sockets);connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(30000);
        connection.setRequestProperty("Authorization","Bearer "+config.data.getString("deviceToken"));connection.setRequestProperty("X-Device-Id",config.data.getString("deviceId"));
        try{if(connection.getResponseCode()!=200)throw new IOException("APK download rejected.");java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");long total=0,deadline=android.os.SystemClock.elapsedRealtime()+300000;try(InputStream input=connection.getInputStream();OutputStream output=new FileOutputStream(destination)){byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1){total+=n;if(total>expectedSize||android.os.SystemClock.elapsedRealtime()>deadline)throw new IOException("APK exceeded its size or download deadline.");digest.update(buffer,0,n);output.write(buffer,0,n);}}StringBuilder actual=new StringBuilder();for(byte value:digest.digest())actual.append(String.format(java.util.Locale.US,"%02x",value&255));if(total!=expectedSize||!expectedHash.equals(actual.toString()))throw new SecurityException("APK checksum does not match administrator upload.");}finally{connection.disconnect();}
    }
}
