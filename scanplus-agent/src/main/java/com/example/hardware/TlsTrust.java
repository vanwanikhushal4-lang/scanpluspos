package com.example.hardware;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/** Explicit CA trust for old Android firmware. Never accepts an untrusted leaf. */
final class TlsTrust {
    static X509TrustManager trustManager(InputStream pem)throws Exception {
        Collection<? extends Certificate> certificates=CertificateFactory.getInstance("X.509").generateCertificates(pem);
        if(certificates.isEmpty()||certificates.size()>256)throw new CertificateException("Invalid CA bundle.");
        KeyStore roots=KeyStore.getInstance(KeyStore.getDefaultType());roots.load(null);
        int index=0;
        for(Certificate certificate:certificates){
            X509Certificate root=(X509Certificate)certificate;
            if(root.getBasicConstraints()<0)throw new CertificateException("Trust bundle contains a non-CA certificate.");
            roots.setCertificateEntry("scanplus-ca-"+(index++),root);
        }
        TrustManagerFactory factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());factory.init(roots);
        for(TrustManager manager:factory.getTrustManagers())if(manager instanceof X509TrustManager)return (X509TrustManager)manager;
        throw new CertificateException("No X509 trust manager available.");
    }
    static SSLSocketFactory socketFactory(InputStream pem)throws Exception {
        SSLContext tls=SSLContext.getInstance("TLSv1.2");
        tls.init(null,new TrustManager[]{trustManager(pem)},null);
        return tls.getSocketFactory();
    }
}
