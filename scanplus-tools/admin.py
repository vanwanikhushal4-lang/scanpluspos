#!/usr/bin/env python3
"""Administrator CLI. Credentials remain in the private configuration file."""
import argparse,json,ssl,uuid,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def request(path,body=None,method=None):
    data=json.loads((ROOT/'.scanplus-local/admin.json').read_text())
    context=ssl.create_default_context(cafile=str(ROOT/'.scanplus-local/server.pem'))
    req=urllib.request.Request(data.get('adminBaseUrl',data['baseUrl'])+path,data=None if body is None else json.dumps(body).encode(),headers={'Authorization':'Bearer '+data['adminToken'],'Content-Type':'application/json'},method=method)
    with urllib.request.urlopen(req,context=context,timeout=15)as response:
        content=response.read()
        return json.loads(content)if content else None

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=['devices','enable','disable','commands','audit','revoke'])
    parser.add_argument('--device')
    args=parser.parse_args()
    if args.action in ('enable','disable','commands','revoke'):
        if not args.device:parser.error('--device is required')
        device=str(uuid.UUID(args.device))
    if args.action=='devices':result=request('/api/admin/devices')
    elif args.action=='audit':result=request('/api/admin/audit')
    elif args.action=='commands':result=request('/api/admin/devices/'+device+'/commands')
    elif args.action=='revoke':result=request('/api/admin/devices/'+device,method='DELETE')
    else:result=request('/api/admin/devices/'+device+'/protection',{'requestId':str(uuid.uuid4()),'enabled':args.action=='enable','ttlSeconds':120})
    print(json.dumps(result,indent=2))
if __name__=='__main__':main()
