// Crop odins_logo.png down to its circular emblem (no wordmark) for the splash
// icon. Pure Node (zlib), handles PNG colorType 2 (RGB), 8-bit. Args: x0 y0 size out
import fs from "node:fs";
import zlib from "node:zlib";

const SRC = "app/src/main/res/drawable/odins_logo.png";
const [x0, y0, size, out] = [
  parseInt(process.argv[2] ?? "152"),
  parseInt(process.argv[3] ?? "10"),
  parseInt(process.argv[4] ?? "950"),
  process.argv[5] ?? (process.env.TEMP || "/tmp") + "/emblem.png",
];

function readPng(path) {
  const b = fs.readFileSync(path);
  let off = 8, width, height, colorType, bitDepth;
  const idat = [];
  while (off < b.length) {
    const len = b.readUInt32BE(off);
    const type = b.toString("ascii", off + 4, off + 8);
    const data = b.subarray(off + 8, off + 8 + len);
    if (type === "IHDR") { width = data.readUInt32BE(0); height = data.readUInt32BE(4); bitDepth = data[8]; colorType = data[9]; }
    if (type === "IDAT") idat.push(data);
    off += 12 + len;
    if (type === "IEND") break;
  }
  if (colorType !== 2 || bitDepth !== 8) throw new Error(`unsupported PNG colorType=${colorType} depth=${bitDepth}`);
  return { width, height, raw: zlib.inflateSync(Buffer.concat(idat)) };
}

function unfilter(raw, width, height, bpp) {
  const stride = width * bpp;
  const o = Buffer.alloc(height * stride);
  let pos = 0;
  for (let y = 0; y < height; y++) {
    const ft = raw[pos++];
    for (let x = 0; x < stride; x++) {
      const rb = raw[pos++];
      const a = x >= bpp ? o[y * stride + x - bpp] : 0;
      const b = y > 0 ? o[(y - 1) * stride + x] : 0;
      const c = x >= bpp && y > 0 ? o[(y - 1) * stride + x - bpp] : 0;
      let v;
      if (ft === 0) v = rb;
      else if (ft === 1) v = rb + a;
      else if (ft === 2) v = rb + b;
      else if (ft === 3) v = rb + ((a + b) >> 1);
      else { const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c); v = rb + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c); }
      o[y * stride + x] = v & 0xff;
    }
  }
  return o;
}

function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const t = Buffer.from(type, "ascii");
  const crc = Buffer.alloc(4); crc.writeUInt32BE(zlib.crc32(Buffer.concat([t, data])) >>> 0);
  return Buffer.concat([len, t, data, crc]);
}

const bpp = 3;
const DOWN = parseInt(process.argv[6] ?? "2"); // box-downsample factor to keep the asset light
const { width, height, raw } = readPng(SRC);
const px = unfilter(raw, width, height, bpp);
const wc = Math.min(size, width - x0), hc = Math.min(size, height - y0);
const stride = width * bpp;

// Crop, then box-average downsample by DOWN (the splash only renders ~384 px).
const ow = Math.floor(wc / DOWN), oh = Math.floor(hc / DOWN);
const ostride = ow * bpp;
const rawF = Buffer.alloc(oh * (ostride + 1));
for (let oy = 0; oy < oh; oy++) {
  rawF[oy * (ostride + 1)] = 0;
  for (let ox = 0; ox < ow; ox++) {
    for (let ch = 0; ch < bpp; ch++) {
      let sum = 0;
      for (let dy = 0; dy < DOWN; dy++) {
        for (let dx = 0; dx < DOWN; dx++) {
          const sy = y0 + oy * DOWN + dy, sx = x0 + ox * DOWN + dx;
          sum += px[sy * stride + sx * bpp + ch];
        }
      }
      rawF[oy * (ostride + 1) + 1 + ox * bpp + ch] = Math.round(sum / (DOWN * DOWN));
    }
  }
}
const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(ow, 0); ihdr.writeUInt32BE(oh, 4); ihdr[8] = 8; ihdr[9] = 2;
const png = Buffer.concat([
  Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
  chunk("IHDR", ihdr),
  chunk("IDAT", zlib.deflateSync(rawF, { level: 9 })),
  chunk("IEND", Buffer.alloc(0)),
]);
fs.writeFileSync(out, png);
console.log(`wrote ${out} (${ow}x${oh}, downsampled /${DOWN}) from crop x0=${x0} y0=${y0} size=${size}`);
