#!/usr/bin/env python3
"""Issue a LAN server certificate under the existing device-trusted CA after DHCP changes.

Preserves the CA, administrator token, database password, and device identities.
Restart the local backend and update enrolled devices' baseUrl separately.
"""
import hashlib,ipaddress,json,os,secrets,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
PRIVATE=ROOT/'.scanplus-local'
JAVA=Path(os.environ.get('JAVA_HOME','/Users/apple/Library/Java/JavaVirtualMachines/jbr-17.0.14/Contents/Home'))

def main():
    os.umask(0o077)
    host=str(ipaddress.ip_address(sys.argv[1]));settings=PRIVATE/'admin.json';data=json.loads(settings.read_text())
    ca=PRIVATE/'server.pem';ca_hash=hashlib.sha256(ca.read_bytes()).hexdigest()
    suffix=secrets.token_hex(6);leaf=PRIVATE/('server-leaf-'+suffix+'.p12');csr=PRIVATE/('request-'+suffix+'.csr');cert=PRIVATE/('leaf-'+suffix+'.pem')
    env=os.environ.copy();env['SCANPLUS_TLS_PASSWORD']=data['storePassword']
    def keytool(*args):subprocess.run([str(JAVA/'bin/keytool'),*args],env=env,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    password=['-storepass:env','SCANPLUS_TLS_PASSWORD']
    san='SAN=IP:'+host+',IP:127.0.0.1,DNS:localhost'
    try:
        keytool('-genkeypair','-alias','scanplus-server','-keyalg','RSA','-keysize','3072','-sigalg','SHA256withRSA','-validity','180','-storetype','PKCS12','-keystore',str(leaf),*password,'-dname','CN=SCANPLUS Backend','-ext',san)
        keytool('-certreq','-alias','scanplus-server','-keystore',str(leaf),*password,'-file',str(csr),'-ext',san)
        keytool('-gencert','-alias','scanplus','-keystore',str(PRIVATE/'server.p12'),*password,'-infile',str(csr),'-outfile',str(cert),'-rfc','-validity','180','-ext',san,'-ext','BC=ca:false','-ext','KU=digitalSignature,keyEncipherment','-ext','EKU=serverAuth')
        keytool('-importcert','-noprompt','-alias','scanplus-ca','-keystore',str(leaf),*password,'-file',str(ca))
        keytool('-importcert','-noprompt','-alias','scanplus-server','-keystore',str(leaf),*password,'-file',str(cert))
        assert hashlib.sha256(ca.read_bytes()).hexdigest()==ca_hash
        backup=PRIVATE/'backups'/('admin-before-address-'+suffix+'.json');backup.parent.mkdir(mode=0o700,exist_ok=True);backup.write_text(settings.read_text());backup.chmod(0o600)
        old=data['baseUrl'];data.update(host=host,baseUrl='https://'+host+':9443',adminBaseUrl='https://127.0.0.1:9443',tlsKeyStore=leaf.name,tlsKeyAlias='scanplus-server')
        temp=settings.with_suffix('.tmp');temp.write_text(json.dumps(data,indent=2)+'\n');temp.chmod(0o600);temp.replace(settings);leaf.chmod(0o600)
        print(json.dumps({'previousAddress':old,'deviceAddress':data['baseUrl'],'administratorAddress':data['adminBaseUrl'],'trustedCaPreserved':True,'enrollmentCredentialsPreserved':True}))
    finally:csr.unlink(missing_ok=True);cert.unlink(missing_ok=True)

if __name__=='__main__':main()
