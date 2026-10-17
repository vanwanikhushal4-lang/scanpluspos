#!/usr/bin/env python3
"""Create a private development TLS server configuration and run the Spring MVC service."""
import json,os,secrets,shutil,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
PRIVATE=ROOT/'.scanplus-local'
JAVA=Path(os.environ['JAVA_HOME']) if os.environ.get('JAVA_HOME') else Path(shutil.which('java') or '/missing/java').resolve().parents[1]

def prepare(host):
    PRIVATE.mkdir(mode=0o700,exist_ok=True)
    settings=PRIVATE/'admin.json'
    if settings.exists():
        data=json.loads(settings.read_text())
        if data['host']!=host:raise SystemExit('Host changed; create a new server certificate explicitly before changing the enrolled origin.')
        return data
    data={'host':host,'baseUrl':f'https://{host}:9443','adminToken':secrets.token_urlsafe(40),'storePassword':secrets.token_urlsafe(40),'databasePassword':secrets.token_urlsafe(40)}
    keyfile=PRIVATE/'server.p12';cert=PRIVATE/'server.pem'
    env=os.environ.copy();env['SCANPLUS_TLS_PASSWORD']=data['storePassword']
    subprocess.run([str(JAVA/'bin/keytool'),'-genkeypair','-alias','scanplus','-keyalg','RSA','-keysize','3072','-sigalg','SHA256withRSA','-validity','365','-storetype','PKCS12','-keystore',str(keyfile),'-storepass:env','SCANPLUS_TLS_PASSWORD','-keypass:env','SCANPLUS_TLS_PASSWORD','-dname','CN=SCANPLUS Management Backend','-ext',f'SAN=IP:{host},IP:127.0.0.1,DNS:localhost','-ext','BC=ca:true'],env=env,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    subprocess.run([str(JAVA/'bin/keytool'),'-exportcert','-rfc','-alias','scanplus','-keystore',str(keyfile),'-storepass:env','SCANPLUS_TLS_PASSWORD','-file',str(cert)],env=env,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    settings.write_text(json.dumps(data,indent=2)+'\n')
    for f in (settings,keyfile,cert):f.chmod(0o600)
    return data

def main():
    os.umask(0o077)
    if not (JAVA/'bin/java').is_file() or not (JAVA/'bin/keytool').is_file():
        raise SystemExit('Set JAVA_HOME to a Java 17 installation with java and keytool.')
    data=prepare(sys.argv[1] if len(sys.argv)>1 else '192.168.0.30')
    env=os.environ.copy();env.update(JAVA_HOME=str(JAVA),SCANPLUS_ADMIN_TOKEN=data['adminToken'],SCANPLUS_BIND='0.0.0.0',SCANPLUS_DB_PASSWORD=data['databasePassword'],SERVER_SSL_ENABLED='true',SERVER_SSL_KEY_STORE='file:'+str(PRIVATE/data.get('tlsKeyStore','server.p12')),SERVER_SSL_KEY_STORE_PASSWORD=data['storePassword'],SERVER_SSL_KEY_STORE_TYPE='PKCS12',SERVER_SSL_KEY_ALIAS=data.get('tlsKeyAlias','scanplus'),SERVER_SSL_ENABLED_PROTOCOLS='TLSv1.2,TLSv1.3')
    print('Starting SCANPLUS HTTPS backend on '+data['baseUrl'],flush=True)
    os.chdir(ROOT/'scanplus-backend')
    os.execve(str(ROOT/'scanplus-backend/build/install/scanplus-security-backend/bin/scanplus-security-backend'),['scanplus-security-backend'],env)
if __name__=='__main__':main()
