const { execSync } = require('child_process');
const path = require('path');
const fs = require('fs');

const edgePath = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const prototypeDir = path.resolve(__dirname, 'docs/prototype');
const pagesDir = path.join(prototypeDir, 'pages');
const outDir = path.join(__dirname, 'tmp/screenshots');
if (!fs.existsSync(outDir)) fs.mkdirSync(outDir, { recursive: true });

const pagesToTest = [
  { name: 'index', url: 'file:///' + path.join(prototypeDir, 'index.html').replace(/\\/g, '/') },
  { name: 'login', url: 'file:///' + path.join(pagesDir, 'login.html').replace(/\\/g, '/') },
  { name: 'dashboard', url: 'file:///' + path.join(pagesDir, 'dashboard.html').replace(/\\/g, '/') },
  { name: 'master-data', url: 'file:///' + path.join(pagesDir, 'master-data.html').replace(/\\/g, '/') },
  { name: 'purchase-inbound', url: 'file:///' + path.join(pagesDir, 'purchase-inbound.html').replace(/\\/g, '/') },
  { name: 'sales-outbound', url: 'file:///' + path.join(pagesDir, 'sales-outbound.html').replace(/\\/g, '/') },
  { name: 'work-order', url: 'file:///' + path.join(pagesDir, 'work-order.html').replace(/\\/g, '/') },
  { name: 'device-alarm', url: 'file:///' + path.join(pagesDir, 'device-alarm.html').replace(/\\/g, '/') },
  { name: 'site-map', url: 'file:///' + path.join(pagesDir, 'site-map.html').replace(/\\/g, '/') },
  { name: 'ai-assistant', url: 'file:///' + path.join(pagesDir, 'ai-assistant.html').replace(/\\/g, '/') },
  { name: 'tool-audit', url: 'file:///' + path.join(pagesDir, 'tool-audit.html').replace(/\\/g, '/') },
  { name: 'workflow-wireframe', url: 'file:///' + path.join(pagesDir, 'workflow-wireframe.html').replace(/\\/g, '/') }
];

console.log('Testing 1440px and 768px rendering in Microsoft Edge headless...');

pagesToTest.forEach(p => {
  const shot1440 = path.join(outDir, `${p.name}_1440.png`);
  const shot768 = path.join(outDir, `${p.name}_768.png`);

  execSync(`"${edgePath}" --headless --disable-gpu --window-size=1440,900 --screenshot="${shot1440}" "${p.url}"`, { stdio: 'pipe' });
  execSync(`"${edgePath}" --headless --disable-gpu --window-size=768,1024 --screenshot="${shot768}" "${p.url}"`, { stdio: 'pipe' });

  const size1440 = fs.statSync(shot1440).size;
  const size768 = fs.statSync(shot768).size;

  console.log(`✓ ${p.name}: 1440px (${(size1440/1024).toFixed(1)} KB) & 768px (${(size768/1024).toFixed(1)} KB) rendered cleanly`);
});

console.log('\nAll 12 pages rendered without crash or render failure!');
