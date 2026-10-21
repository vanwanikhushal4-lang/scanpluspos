#!/usr/bin/env python3
"""Store the Windows administrator credential locally, never in React or chat."""
import getpass
import json
import os
from pathlib import Path
import re
import tempfile
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]

def main():
    origin = input('Public HTTPS backend [https://scanplus-backend-gateway.secureitpos.workers.dev]: ').strip() or 'https://scanplus-backend-gateway.secureitpos.workers.dev'
    parsed = urlsplit(origin)
    if parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ('', '/'):
        raise ValueError('Enter an HTTPS origin without a path or credentials.')
    token = getpass.getpass('Windows admin token (hidden; paste locally, never in chat): ').strip()
    if not re.fullmatch(r'[A-Za-z0-9_-]{32,128}', token):
        raise ValueError('The administrator token has an invalid format. No configuration was saved.')
    folder = ROOT / '.scanplus-local'
    folder.mkdir(mode=0o700, exist_ok=True)
    destination = folder / 'admin.json'
    if destination.exists():
        raise FileExistsError('An administrator configuration already exists. Back it up before replacing it.')
    descriptor, temporary = tempfile.mkstemp(prefix='.admin-', dir=folder)
    try:
        with os.fdopen(descriptor, 'w') as stream:
            json.dump({'baseUrl': origin.rstrip('/'), 'trustMode': 'public', 'adminToken': token}, stream)
            stream.write('\n')
        os.chmod(temporary, 0o600)
        os.replace(temporary, destination)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    print('Private cloud console configuration saved. No credentials were printed.')

if __name__ == '__main__':
    try:
        main()
    except (Exception, KeyboardInterrupt) as error:
        print('Configuration was not completed:', str(error))
        raise SystemExit(1)
