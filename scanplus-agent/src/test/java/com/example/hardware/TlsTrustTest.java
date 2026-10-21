package com.example.hardware;

import org.junit.Test;
import java.io.*;
import java.security.cert.*;
import javax.net.ssl.X509TrustManager;
import static org.junit.Assert.*;

public final class TlsTrustTest {
    private InputStream resource(String name){return getClass().getResourceAsStream("/"+name);}
    private X509Certificate certificate(String name)throws Exception {
        try(InputStream pem=resource(name)){return (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(pem);}
    }
    private X509TrustManager publicTrust()throws Exception {
        try(InputStream pem=new FileInputStream("src/main/assets/public-roots.crt")){return TlsTrust.trustManager(pem);}
    }
    @Test public void dedicatedCaAcceptsItsServerChain()throws Exception {
        try(InputStream pem=resource("test-ca.crt")){
            TlsTrust.trustManager(pem).checkServerTrusted(new X509Certificate[]{certificate("test-leaf.crt"),certificate("test-ca.crt")},"RSA");
        }
    }
    @Test public void publicTrustRejectsAnUnrelatedPrivateServer()throws Exception {
        X509TrustManager trust=publicTrust();assertTrue(trust.getAcceptedIssuers().length>100);
        assertThrows(CertificateException.class,()->trust.checkServerTrusted(new X509Certificate[]{certificate("test-leaf.crt"),certificate("test-ca.crt")},"RSA"));
    }
    @Test public void leafCannotBeProvisionedAsAnAuthority()throws Exception {
        try(InputStream pem=resource("test-leaf.crt")){assertThrows(CertificateException.class,()->TlsTrust.trustManager(pem));}
    }
    @Test public void emptyAndMalformedBundlesFailClosed()throws Exception {
        for(String text:new String[]{"","not a certificate"}){
            try(InputStream pem=new ByteArrayInputStream(text.getBytes("US-ASCII"))){assertThrows(CertificateException.class,()->TlsTrust.trustManager(pem));}
        }
    }
}
