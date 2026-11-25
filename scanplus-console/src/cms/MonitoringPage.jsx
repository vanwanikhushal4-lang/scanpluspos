import React from 'react';
import { AppWindow, Cpu } from 'lucide-react';
import { Card, PageHead } from './ui.jsx';
import './monitoring.css';

const components = [
  ['Complete terminal', 'PAX Computer Technology', 'China'],
  ['Main processor (MSM8909)', 'Qualcomm', 'United States'],
  ['Wi-Fi and Bluetooth (WCN3660B)', 'Qualcomm', 'United States'],
  ['Storage and RAM (JY974)', 'Micron Technology', 'United States'],
  ['Battery pack (IS900)', 'Icon Energy System', 'China'],
  ['Battery pack (105862)', 'Ji’an Lilai New Energy Technology', 'China'],
  ['Touch controller (GT5688)', 'Goodix', 'China'],
  ['LCD panel', 'Unknown', 'Unknown'],
  ['Battery charging chip (BQ24259)', 'Texas Instruments', 'United States'],
  ['Contactless reader (FM17550)', 'Shanghai Fudan Microelectronics', 'China'],
  ['Chip-card interface (NCN8025A)', 'onsemi', 'United States'],
  ['NOR flash (MX25L3233F)', 'Macronix', 'Taiwan'],
  ['Microcontroller (MK21FN1M0V12)', 'Freescale (now part of NXP)', 'United States (Freescale), Netherlands (NXP)'],
  ['Cellular RF (77638-11, 77912-61)', 'Skyworks Solutions', 'United States'],
  ['Wall adapter (GLH50D2000HW)', 'Shenzhen Sorghum Red Electronics Technology', 'China'],
  ['Cameras, printer, speaker, microphone', 'Unknown', 'Unknown'],
];

export default function MonitoringPage({ navigate }) {
  return <>
    <PageHead title="Monitoring">
      <button type="button" className="button white" onClick={() => navigate('apps')}>
        <AppWindow size={14} aria-hidden="true"/> Application Control
      </button>
    </PageHead>
    <Card className="hardware-inventory">
      <div className="hardware-inventory-heading">
        <span className="hardware-inventory-icon"><Cpu size={18} aria-hidden="true"/></span>
        <div>
          <h2 id="hardware-inventory-title">PAX A920 hardware inventory</h2>
          <p>Reference inventory from the component identification report.</p>
        </div>
      </div>
      <div className="hardware-inventory-table">
        <table className="data-table" aria-labelledby="hardware-inventory-title">
          <thead>
            <tr><th scope="col">Component</th><th scope="col">Manufacturer</th><th scope="col">Company origin</th></tr>
          </thead>
          <tbody>
            {components.map(([component, manufacturer, origin], index) => (
              <tr key={component} style={{ '--inventory-delay': `${index * 23}ms` }}>
                <th scope="row">{component}</th>
                <td>{manufacturer}</td>
                <td>{origin}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  </>;
}
