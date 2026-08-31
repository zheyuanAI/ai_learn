const fs = require('fs');
const path = require('path');

const prototypeDir = path.resolve(__dirname, 'docs/prototype');
const pagesDir = path.join(prototypeDir, 'pages');

const htmlFiles = [
  path.join(prototypeDir, 'index.html'),
  ...fs.readdirSync(pagesDir).filter(f => f.endsWith('.html')).map(f => path.join(pagesDir, f))
];

let allValid = true;

htmlFiles.forEach(filePath => {
  const html = fs.readFileSync(filePath, 'utf8');
  const baseDir = path.dirname(filePath);
  const relPath = path.relative(prototypeDir, filePath);
  
  // check link tags
  const linkMatches = [...html.matchAll(/<link[^>]+href=["']([^"']+)["']/g)];
  linkMatches.forEach(m => {
    const href = m[1];
    if (href.startsWith('data:') || href.startsWith('http')) return;
    const target = path.resolve(baseDir, href);
    if (!fs.existsSync(target)) {
      console.error('✗ Missing stylesheet in ' + relPath + ': ' + href);
      allValid = false;
    }
  });

  // check script tags
  const scriptMatches = [...html.matchAll(/<script[^>]+src=["']([^"']+)["']/g)];
  scriptMatches.forEach(m => {
    const src = m[1];
    if (src.startsWith('data:') || src.startsWith('http')) return;
    const target = path.resolve(baseDir, src);
    if (!fs.existsSync(target)) {
      console.error('✗ Missing script in ' + relPath + ': ' + src);
      allValid = false;
    }
  });

  console.log('✓ Assets verified in: ' + relPath);
});

if (!allValid) {
  process.exit(1);
} else {
  console.log('\nAll HTML assets and script references are 100% valid!');
}
