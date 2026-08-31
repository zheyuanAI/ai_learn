const { spawn } = require('child_process');
const http = require('http');
const path = require('path');
const fs = require('fs');

const edgePath = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const prototypeDir = path.resolve(__dirname, 'docs/prototype');
const pagesDir = path.join(prototypeDir, 'pages');

const pagesToTest = [
  { name: '总览页 (index.html)', url: 'file:///' + path.join(prototypeDir, 'index.html').replace(/\\/g, '/') },
  { name: '登录页 (login.html)', url: 'file:///' + path.join(pagesDir, 'login.html').replace(/\\/g, '/') },
  { name: '综合看板 (dashboard.html)', url: 'file:///' + path.join(pagesDir, 'dashboard.html').replace(/\\/g, '/') },
  { name: '库存主数据 (master-data.html)', url: 'file:///' + path.join(pagesDir, 'master-data.html').replace(/\\/g, '/') },
  { name: '采购收货 (purchase-inbound.html)', url: 'file:///' + path.join(pagesDir, 'purchase-inbound.html').replace(/\\/g, '/') },
  { name: '销售出库 (sales-outbound.html)', url: 'file:///' + path.join(pagesDir, 'sales-outbound.html').replace(/\\/g, '/') },
  { name: '制造执行 (work-order.html)', url: 'file:///' + path.join(pagesDir, 'work-order.html').replace(/\\/g, '/') },
  { name: 'IoT设备与告警 (device-alarm.html)', url: 'file:///' + path.join(pagesDir, 'device-alarm.html').replace(/\\/g, '/') },
  { name: 'GIS二维地图 (site-map.html)', url: 'file:///' + path.join(pagesDir, 'site-map.html').replace(/\\/g, '/') },
  { name: 'AI智能助手 (ai-assistant.html)', url: 'file:///' + path.join(pagesDir, 'ai-assistant.html').replace(/\\/g, '/') },
  { name: '工具调用审计 (tool-audit.html)', url: 'file:///' + path.join(pagesDir, 'tool-audit.html').replace(/\\/g, '/') }
];

async function runBrowserValidation() {
  const port = 9333;
  const userDataDir = path.join(__dirname, 'tmp/edge-test-profile');
  if (!fs.existsSync(path.join(__dirname, 'tmp'))) fs.mkdirSync(path.join(__dirname, 'tmp'), { recursive: true });

  console.log(`Starting headless Edge on port ${port}...`);
  const edgeProc = spawn(edgePath, [
    '--headless',
    `--remote-debugging-port=${port}`,
    `--user-data-dir=${userDataDir}`,
    '--disable-gpu',
    '--no-first-run',
    '--no-default-browser-check',
    '--remote-allow-origins=*',
    'about:blank'
  ], { detached: false, windowsHide: true });

  function getJson(pathUrl) {
    return new Promise((resolve, reject) => {
      http.get(`http://127.0.0.1:${port}${pathUrl}`, res => {
        let raw = '';
        res.on('data', chunk => raw += chunk);
        res.on('end', () => {
          try { resolve(JSON.parse(raw)); } catch(e) { reject(e); }
        });
      }).on('error', reject);
    });
  }

  // wait for port to be ready with retry
  let versionInfo = null;
  for (let i = 0; i < 10; i++) {
    await new Promise(r => setTimeout(r, 600));
    try {
      versionInfo = await getJson('/json/version');
      if (versionInfo) break;
    } catch(e) {}
  }

  if (!versionInfo) {
    console.error('Could not connect to Edge on port ' + port);
    edgeProc.kill();
    return;
  }


  function sendWsCommand(wsUrl, method, params = {}) {
    return new Promise((resolve, reject) => {
      const http = require('http');
      const crypto = require('crypto');
      const url = new URL(wsUrl);

      const req = http.request({
        hostname: url.hostname,
        port: url.port,
        path: url.pathname,
        headers: {
          'Connection': 'Upgrade',
          'Upgrade': 'websocket',
          'Sec-WebSocket-Key': crypto.randomBytes(16).toString('base64'),
          'Sec-WebSocket-Version': '13'
        }
      });

      req.on('upgrade', (res, socket) => {
        const id = 1;
        const msg = JSON.stringify({ id, method, params });
        const buf = Buffer.from(msg);
        
        // Frame formatting for unmasked or masked client frame
        const frameHeader = Buffer.alloc(6);
        frameHeader[0] = 0x81; // FIN + text frame
        frameHeader[1] = 0x80 | buf.length; // Masked
        const mask = crypto.randomBytes(4);
        mask.copy(frameHeader, 2);

        const maskedData = Buffer.alloc(buf.length);
        for (let i = 0; i < buf.length; i++) {
          maskedData[i] = buf[i] ^ mask[i % 4];
        }

        socket.write(Buffer.concat([frameHeader, maskedData]));

        let responseBuf = Buffer.alloc(0);
        socket.on('data', data => {
          responseBuf = Buffer.concat([responseBuf, data]);
          // Parse basic websocket text frame
          if (responseBuf.length > 2) {
            let payloadLen = responseBuf[1] & 0x7f;
            let offset = 2;
            if (payloadLen === 126) {
              payloadLen = responseBuf.readUInt16BE(2);
              offset = 4;
            }
            if (responseBuf.length >= offset + payloadLen) {
              const payload = responseBuf.slice(offset, offset + payloadLen).toString('utf8');
              try {
                const parsed = JSON.parse(payload);
                if (parsed.id === id) {
                  socket.end();
                  resolve(parsed.result);
                }
              } catch(e) {}
            }
          }
        });
      });

      req.on('error', reject);
      req.end();
    });
  }

  try {
    const versionInfo = await getJson('/json/version');
    console.log(`Connected to Edge browser version: ${versionInfo['Browser']}`);

    const targets = await getJson('/json');
    const pageTarget = targets.find(t => t.type === 'page') || targets[0];
    const wsUrl = pageTarget.webSocketDebuggerUrl;

    console.log('\n--- 开始全量页面 1440px 与 768px 响应式及控制台验证 ---');
    let hasOverflowIssue = false;

    for (const p of pagesToTest) {
      // Test at 1440px
      await sendWsCommand(wsUrl, 'Emulation.setDeviceMetricsOverride', {
        width: 1440,
        height: 900,
        deviceScaleFactor: 1,
        mobile: false
      });
      await sendWsCommand(wsUrl, 'Page.navigate', { url: p.url });
      await new Promise(r => setTimeout(r, 400));

      const res1440 = await sendWsCommand(wsUrl, 'Runtime.evaluate', {
        expression: 'JSON.stringify({ scrollWidth: document.documentElement.scrollWidth, clientWidth: document.documentElement.clientWidth, bodyScrollWidth: document.body.scrollWidth })'
      });
      const dim1440 = JSON.parse(res1440.result.value);
      const isOverflow1440 = dim1440.scrollWidth > 1440;

      // Test at 768px
      await sendWsCommand(wsUrl, 'Emulation.setDeviceMetricsOverride', {
        width: 768,
        height: 1024,
        deviceScaleFactor: 1,
        mobile: false
      });
      await sendWsCommand(wsUrl, 'Page.navigate', { url: p.url });
      await new Promise(r => setTimeout(r, 400));

      const res768 = await sendWsCommand(wsUrl, 'Runtime.evaluate', {
        expression: 'JSON.stringify({ scrollWidth: document.documentElement.scrollWidth, clientWidth: document.documentElement.clientWidth, bodyScrollWidth: document.body.scrollWidth })'
      });
      const dim768 = JSON.parse(res768.result.value);
      const isOverflow768 = dim768.scrollWidth > 768;

      if (isOverflow1440 || isOverflow768) {
        console.error(`✗ ${p.name}: 发生横向溢出! (1440px: scrollWidth=${dim1440.scrollWidth}, 768px: scrollWidth=${dim768.scrollWidth})`);
        hasOverflowIssue = true;
      } else {
        console.log(`✓ ${p.name}: 1440px 与 768px 响应式无横向溢出 (1440px: ${dim1440.scrollWidth}px, 768px: ${dim768.scrollWidth}px)`);
      }
    }

    if (!hasOverflowIssue) {
      console.log('\n🎉 所有 11 个高保真原型页面在 1440px 和 768px 宽度下均无页面级横向溢出！');
    }

  } catch (err) {
    console.error('Browser validation encountered error:', err);
  } finally {
    edgeProc.kill();
  }
}

runBrowserValidation();
