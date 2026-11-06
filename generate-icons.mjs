import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

const publicDir = path.resolve('public');
const wellKnownDir = path.join(publicDir, '.well-known');
fs.mkdirSync(wellKnownDir, { recursive: true });

// 1. Brand SVG Logo & Icon (/public/icon.svg)
const iconSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" fill="none">
  <rect width="512" height="512" rx="112" fill="#0F3D8A"/>
  <path d="M168 196C168 147.399 207.399 108 256 108C304.601 108 344 147.399 344 196" stroke="#FFFFFF" stroke-width="32" stroke-linecap="round"/>
  <rect x="116" y="188" width="280" height="224" rx="44" fill="#FFFFFF"/>
  <path d="M196 256V344H284" stroke="#0F3D8A" stroke-width="34" stroke-linecap="round" stroke-linejoin="round"/>
  <circle cx="332" cy="260" r="24" fill="#F97316"/>
  <path d="M348 188H396V236C396 236 364 236 348 220C332 204 348 188 348 188Z" fill="#F97316"/>
</svg>
`;
fs.writeFileSync(path.join(publicDir, 'icon.svg'), iconSvg, 'utf8');

// 2. Pure Node PNG encoder (valid RGBA PNG with anti-aliased geometric Lubumarket icon)
function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) {
    c ^= buf[i];
    for (let k = 0; k < 8; k++) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
  }
  return (c ^ 0xffffffff) >>> 0;
}

function pngChunk(type, data) {
  const typeBuf = Buffer.from(type, 'ascii');
  const lenBuf = Buffer.alloc(4);
  lenBuf.writeUInt32BE(data.length, 0);
  const crcBuf = Buffer.alloc(4);
  const combined = Buffer.concat([typeBuf, data]);
  crcBuf.writeUInt32BE(crc32(combined), 0);
  return Buffer.concat([lenBuf, combined, crcBuf]);
}

function insideRoundedRect(x, y, rx, ry, rw, rh, rad) {
  if (x < rx || x >= rx + rw || y < ry || y >= ry + rh) return false;
  const cx = Math.max(rx + rad, Math.min(x, rx + rw - rad));
  const cy = Math.max(ry + rad, Math.min(y, ry + rh - rad));
  const dx = x - cx;
  const dy = y - cy;
  return dx * dx + dy * dy <= rad * rad;
}

function generateLubumarketPng(size, maskable = false) {
  const raw = Buffer.alloc(size * (size * 4 + 1));
  const scale = maskable ? 0.76 : 0.92;
  const offset = (size * (1 - scale)) / 2;

  for (let y = 0; y < size; y++) {
    const rowStart = y * (size * 4 + 1);
    raw[rowStart] = 0; // filter none
    for (let x = 0; x < size; x++) {
      const px = rowStart + 1 + x * 4;
      // Full-bleed background for maskable, rounded squircle for 'any'
      const inCanvas = maskable
        ? true
        : insideRoundedRect(x, y, 0, 0, size, size, size * 0.21);

      if (!inCanvas) {
        raw[px] = 0;
        raw[px + 1] = 0;
        raw[px + 2] = 0;
        raw[px + 3] = 0;
        continue;
      }

      // Default primary Lubumarket Blue (#0F3D8A -> 15, 61, 138)
      let r = 15, g = 61, b = 138, a = 255;

      // Normalized coordinates inside safe icon box [0..512]
      const nx = ((x - offset) / (size * scale)) * 512;
      const ny = ((y - offset) / (size * scale)) * 512;

      if (nx >= 0 && nx <= 512 && ny >= 0 && ny <= 512) {
        // Bag handle arc: center (256, 196), radius 88, stroke 32, y <= 196
        const hdx = nx - 256;
        const hdy = ny - 196;
        const hDist = Math.sqrt(hdx * hdx + hdy * hdy);
        if (ny <= 198 && hDist >= 72 && hDist <= 104) {
          r = 255; g = 255; b = 255;
        }

        // White shopping parcel body: rect(116, 188, 280, 224, rx=42)
        if (insideRoundedRect(nx, ny, 116, 188, 280, 224, 42)) {
          r = 255; g = 255; b = 255;

          // Orange corner tag top-right of bag
          if (nx >= 332 && ny <= 236 && (nx - 332) + (236 - ny) >= 34) {
            r = 249; g = 115; b = 22; // #F97316 Lubumarket Orange
          }

          // 'L' monogram in Lubumarket Blue inside bag
          const inVerticalL = insideRoundedRect(nx, ny, 182, 238, 34, 122, 14);
          const inHorizontalL = insideRoundedRect(nx, ny, 182, 326, 112, 34, 14);
          if (inVerticalL || inHorizontalL) {
            r = 15; g = 61; b = 138;
          }

          // Orange accent dot inside bag
          const odx = nx - 328;
          const ody = ny - 266;
          if (odx * odx + ody * ody <= 24 * 24) {
            r = 249; g = 115; b = 22;
          }
        }
      }

      raw[px] = r;
      raw[px + 1] = g;
      raw[px + 2] = b;
      raw[px + 3] = a;
    }
  }

  const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // RGBA
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;

  const idat = zlib.deflateSync(raw, { level: 9 });
  return Buffer.concat([
    signature,
    pngChunk('IHDR', ihdr),
    pngChunk('IDAT', idat),
    pngChunk('IEND', Buffer.alloc(0)),
  ]);
}

fs.writeFileSync(path.join(publicDir, 'apple-touch-icon.png'), generateLubumarketPng(180, true));
fs.writeFileSync(path.join(publicDir, 'pwa-192x192.png'), generateLubumarketPng(192, false));
fs.writeFileSync(path.join(publicDir, 'pwa-512x512.png'), generateLubumarketPng(512, false));
fs.writeFileSync(path.join(publicDir, 'pwa-maskable-512x512.png'), generateLubumarketPng(512, true));

// 3. Digital Asset Links template (/public/.well-known/assetlinks.json)
const assetLinks = [
  {
    relation: ['delegate_permission/common.handle_all_urls'],
    target: {
      namespace: 'android_app',
      package_name: 'com.lubumarket.app',
      sha256_cert_fingerprints: [
        'A_REMPLACER_PAR_EMPREINTE_SHA256_GOOGLE_PLAY_CONSOLE',
      ],
    },
  },
];
fs.writeFileSync(
  path.join(wellKnownDir, 'assetlinks.json'),
  JSON.stringify(assetLinks, null, 2) + '\n',
  'utf8'
);

console.log('Lubumarket icons and Digital Asset Links generated successfully.');
