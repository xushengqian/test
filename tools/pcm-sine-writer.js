import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const OUT = process.env.AUDIO_PATH || path.join(__dirname, "..", "data", "live.pcm");

// 与 server.js 的默认格式保持一致：PCM s16le / 48kHz / mono
const SAMPLE_RATE = Number(process.env.SAMPLE_RATE || 48000);
const FREQ = Number(process.env.FREQ || 440);
const CHUNK_MS = Number(process.env.CHUNK_MS || 20);
const AMP = Math.max(0, Math.min(1, Number(process.env.AMP || 0.2)));

await fs.promises.mkdir(path.dirname(OUT), { recursive: true });
await fs.promises.writeFile(OUT, Buffer.alloc(0)); // truncate

const fh = await fs.promises.open(OUT, "a");

let t = 0;
const chunkSamples = Math.max(1, Math.floor((SAMPLE_RATE * CHUNK_MS) / 1000));

console.log(`Writing sine PCM to ${OUT}`);
console.log(`fmt=pcm_s16le sr=${SAMPLE_RATE}Hz ch=1 freq=${FREQ}Hz chunk=${CHUNK_MS}ms amp=${AMP}`);

function makeChunk() {
  const buf = Buffer.alloc(chunkSamples * 2);
  for (let i = 0; i < chunkSamples; i++) {
    const s = Math.sin((2 * Math.PI * FREQ * t) / SAMPLE_RATE) * AMP;
    let v = Math.round(s * 32767);
    if (v > 32767) v = 32767;
    if (v < -32768) v = -32768;
    buf.writeInt16LE(v, i * 2);
    t++;
  }
  return buf;
}

const interval = setInterval(async () => {
  try {
    await fh.write(makeChunk());
  } catch (e) {
    console.error("write failed:", e?.message || e);
  }
}, CHUNK_MS);

process.on("SIGINT", async () => {
  clearInterval(interval);
  try {
    await fh.close();
  } catch {}
  process.exit(0);
});

