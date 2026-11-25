import React, { useEffect, useState } from 'react';
import { ArrowUpRight, Cable, Download, Fingerprint, LockKeyhole, Radio, ShieldCheck, Wifi } from 'lucide-react';
import { getWifi } from '../api.js';
import { online } from '../model.mjs';
import { startPolling } from '../polling.mjs';
import { complianceEvidence } from './compliance-evidence.mjs';
import { DeviceSelect } from './DevicePages.jsx';
import { Badge, Card, PageHead, dateTime } from './ui.jsx';
import './compliance.css';

const poiReference = 'https://blog.pcisecuritystandards.org/just-published-pts-poi-v7-0';
const p2peReference = 'https://www.pcisecuritystandards.org/standards/point-to-point-encryption-p2pe/';

const p2peControls = [
  ['Encryption at the point of capture', 'Encrypt payment account data within the secure capture device.'],
  ['Protection through the payment path', 'Maintain encrypted data until it reaches the decryption environment.'],
  ['Controlled decryption environment', 'Restrict decryption to authorised systems and personnel.'],
  ['Cryptographic key lifecycle', 'Control key generation, distribution, rotation and retirement.'],
  ['Dual control and split knowledge', 'Separate key responsibilities and protect cleartext key components.'],
  ['Secure key injection', 'Load keys through controlled facilities and secure cryptographic devices.'],
  ['Device custody and tamper inspections', 'Track device custody and inspect terminals for substitution or tampering.'],
  ['Incident response and replacement', 'Isolate affected devices and follow documented recovery procedures.'],
];

const controlGroups = [
  {
    title: 'Terminal hardening',
    reference: 'B17',
    icon: Cable,
    controls: [
      ['USB debugging restriction', 'Disables Android debugging in full protection mode.', 'Agent + Android', 'protection'],
      ['USB file transfer restriction', 'Blocks USB file transfer through Android device-owner policy.', 'Agent + Android', 'protection'],
      ['USB data port control', 'Disables USB data on supported terminals and checks that the data interface is off.', 'Agent + USB helper', 'usb'],
      ['Safe mode restriction', 'Blocks Android safe mode through device-owner policy.', 'Agent + Android', 'protection'],
      ['Factory reset restriction', 'Blocks factory reset from Android Settings in full protection mode.', 'Agent + Android', 'protection'],
      ['Local user restriction', 'Blocks the creation of additional Android users in full protection mode.', 'Agent + Android', 'protection'],
    ],
  },
  {
    title: 'Application security',
    reference: 'B2.1, B17',
    icon: ShieldCheck,
    controls: [
      ['Unknown-source install restriction', 'Blocks installation from unknown sources in full protection mode.', 'Agent + Android', 'protection'],
      ['Local install restriction', 'Blocks new local app installations in full protection mode.', 'Agent + Android', 'protection'],
      ['Android app verification', 'Keeps the Android package verifier enabled through managed policy.', 'Agent + Android', 'protection'],
      ['App permission control', 'Lets administrators manage supported runtime permissions per application.', 'Agent + Android'],
      ['Management agent removal protection', 'Blocks removal of the ScanPlus agent in full protection mode.', 'Agent + Android', 'protection'],
      ['Restricted local app settings', 'Prevents local users from changing app settings covered by Android device-owner policy.', 'Agent + Android', 'protection'],
    ],
  },
  {
    title: 'Secure app deployment',
    reference: 'B2.1, B5, B17',
    icon: Download,
    controls: [
      ['Package signature verification', 'Managed installs remain subject to Android and terminal vendor signature checks.', 'Android + vendor'],
      ['Package integrity check', 'Checks the downloaded APK size and SHA-256 digest before installation.', 'Agent'],
      ['Package identity check', 'Confirms the downloaded APK matches the expected Android package.', 'Agent'],
      ['Android compatibility check', 'Checks the APK’s Android version requirements before installation.', 'Agent'],
      ['Device-scoped download access', 'Limits APK downloads to the terminal assigned to the active deployment.', 'Backend'],
      ['Maintenance-gated installation', 'Requires confirmed maintenance mode to install or remove managed apps.', 'Agent + backend'],
    ],
  },
  {
    title: 'Administrative access and audit',
    reference: 'B5, B6, B22',
    icon: Fingerprint,
    controls: [
      ['Single-use enrollment', 'Uses a short-lived enrollment code bound to the terminal’s serial number.', 'Agent + backend'],
      ['Separate management credentials', 'Issues per-terminal credentials and keeps administrator credentials server-side.', 'Agent + backend + gateway'],
      ['Allowlisted management actions', 'Limits the management gateway to predefined routes and validated requests.', 'Console gateway'],
      ['Time-limited commands', 'Expires queued commands and uses request IDs to deduplicate retries.', 'Agent + backend'],
      ['Terminal-specific command results', 'Accepts a command acknowledgment only from the terminal assigned that command.', 'Backend'],
      ['Management audit trail', 'Records enrollment, administrator requests and terminal results for review.', 'Backend'],
    ],
  },
  {
    title: 'Communication and wireless security',
    reference: 'D4, D7, D9',
    icon: Wifi,
    controls: [
      ['Encrypted management traffic', 'Uses HTTPS for management traffic between the terminal agent and backend.', 'Agent + backend'],
      ['Server certificate validation', 'Checks the server certificate against trusted certificate authorities or a configured dedicated certificate.', 'Agent'],
      ['Wi-Fi settings lock', 'Restricts local Wi-Fi changes when full Wi-Fi protection is enabled.', 'Agent + Android', 'wifiLock'],
      ['Open Wi-Fi connection guard', 'On the Android 7 A920, disconnects from open networks after detection when the guard is enabled.', 'Agent + Android', 'wifiGuard'],
      ['Secured Wi-Fi before activation', 'Requires a recent report of a secured connection before enabling Wi-Fi protection or the open-network guard.', 'Agent + backend'],
      ['Wireless security visibility', 'Reports the connected network’s security type and flags unknown security for review.', 'Agent', 'wifiSecurity'],
    ],
  },
];

export default function CompliancePage({ model }) {
  const terminalKey = model.selected ? `${model.selected.id}:${Boolean(model.selected.revoked)}` : 'none';
  return <Controls key={terminalKey} model={model}/>;
}

function Controls({ model }) {
  const device = model.selected;
  const deviceId = device?.revoked ? null : device?.id;
  const [wifi, setWifi] = useState(null);
  const [wifiError, setWifiError] = useState(false);
  const [wifiLoading, setWifiLoading] = useState(Boolean(deviceId));

  useEffect(() => {
    if (!deviceId) return;
    const controller = new AbortController();
    const poll = startPolling(async () => {
      try {
        const report = await getWifi(deviceId, controller.signal);
        if (!controller.signal.aborted) {
          setWifi(report);
          setWifiError(false);
        }
      } catch {
        if (controller.signal.aborted) return;
        setWifiError(true);
        return false;
      } finally {
        if (!controller.signal.aborted) setWifiLoading(false);
      }
    }, { interval: 30000 });
    return () => { controller.abort(); poll.stop(); };
  }, [deviceId]);

  const evidence = complianceEvidence({ device, ready: model.ready, now: model.now, wifi, wifiError, wifiLoading });
  const onlineNow = model.ready && online(device, model.now);

  return <>
    <PageHead title="Compliance">
      <a className="button white pci-source" href={poiReference} target="_blank" rel="noopener noreferrer">
        PCI PTS reference <ArrowUpRight size={14} aria-hidden="true"/>
      </a>
    </PageHead>
    <div className="pci-compliance">
      <Card className="pci-evidence-card">
        <div className="pci-group-head">
          <span className="pci-icon"><Radio size={18} aria-hidden="true"/></span>
          <h2>Terminal evidence</h2>
          <DeviceSelect model={model}/>
        </div>
        <dl className="pci-evidence-facts">
          <div><dt>Agent version</dt><dd>{device?.version || 'Not reported'}</dd></div>
          <div><dt>Device owner</dt><dd>{device?.owner_active == null ? 'Not reported' : device.owner_active ? 'Reported enrolled' : 'Reported unenrolled'}</dd></div>
          <div><dt>Last check-in</dt><dd>{dateTime(device?.last_seen)} {device && <Badge tone={onlineNow ? 'green' : 'muted'}>{device.revoked ? 'Revoked' : !model.ready ? 'Status unavailable' : onlineNow ? 'Online' : 'Offline'}</Badge>}</dd></div>
          <div><dt>Last Wi-Fi report</dt><dd>{dateTime(wifi?.reportedAt)}</dd></div>
        </dl>
        <p className="pci-scope">Full protection is reported as one combined status. Controls shown as capabilities do not have a separate status in the terminal report.</p>
      </Card>
      <Card className="pci-control-group pci-p2pe">
        <div className="pci-group-head">
          <span className="pci-icon"><LockKeyhole size={18} aria-hidden="true"/></span>
          <h2 id="pci-p2pe-heading">PCI P2PE · Payment data protection</h2>
          <a className="button white pci-source" href={p2peReference} target="_blank" rel="noopener noreferrer">
            PCI P2PE reference <ArrowUpRight size={14} aria-hidden="true"/>
          </a>
        </div>
        <p className="pci-scope">Reference controls for protecting payment account data from capture through decryption.</p>
        <ul className="pci-controls" aria-labelledby="pci-p2pe-heading">
          {p2peControls.map(([name, description]) => (
            <li key={name}>
              <h3>{name}</h3>
              <p>{description}</p>
            </li>
          ))}
        </ul>
      </Card>
      {controlGroups.map(({ title, reference, icon: Icon, controls }, index) => {
        const headingId = `pci-control-group-${index}`;
        return <Card key={title} className="pci-control-group">
          <div className="pci-group-head">
            <span className="pci-icon"><Icon size={18} aria-hidden="true"/></span>
            <h2 id={headingId}>{title}</h2>
            <Badge tone="blue">PTS POI v7.0 · {reference}</Badge>
          </div>
          <ul className="pci-controls" aria-labelledby={headingId}>
            {controls.map(([name, description, owner, observation]) => (
              <li key={name}>
                <h3>{name}</h3>
                <p>{description}</p>
                <div className="pci-control-evidence">
                  <Badge>{owner}</Badge>
                  {observation
                    ? <Badge tone={evidence[observation].tone}>{evidence[observation].label}</Badge>
                    : <Badge>Capability</Badge>}
                </div>
              </li>
            ))}
          </ul>
        </Card>;
      })}
    </div>
  </>;
}
