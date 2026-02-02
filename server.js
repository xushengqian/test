import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { WebSocketServer } from "ws";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = Number(process.env.PORT || 5173);
const PUBLIC_DIR = path.join(__dirname, "public");
const AUDIO_PATH = process.env.AUDIO_PATH || path.join(__dirname, "data", "live.pcm");

// 约定：文件内容为 PCM s16le, 48kHz, mono（你也可以自行改这里 + 前端 worklet）
const AUDIO_FMT = {
  type: "pcm_s16le",
  sampleRate: 48000,
  channels: 1
};

function contentType(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  switch (ext) {
    case ".html":
      return "text/html; charset=utf-8";
    case ".js":
      return "text/javascript; charset=utf-8";
    case ".css":
      return "text/css; charset=utf-8";
    case ".json":
      return "application/json; charset=utf-8";
    default:
      return "application/octet-stream";
  }
}

const server = http.createServer((req, res) => {
  try {
    const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
    let rel = decodeURIComponent(url.pathname);
    if (rel === "/") rel = "/index.html";

    // 防止目录穿越
    const abs = path.join(PUBLIC_DIR, rel);
    if (!abs.startsWith(PUBLIC_DIR)) {
      res.writeHead(403);
      res.end("Forbidden");
      return;
    }

    fs.readFile(abs, (err, data) => {
      if (err) {
        res.writeHead(404);
        res.end("Not Found");
        return;
      }
      res.writeHead(200, { "Content-Type": contentType(abs) });
      res.end(data);
    });
  } catch {
    res.writeHead(400);
    res.end("Bad Request");
  }
});

const wss = new WebSocketServer({ server, path: "/ws" });

function broadcastBinary(buf) {
  for (const client of wss.clients) {
    if (client.readyState === 1) client.send(buf);
  }
}

wss.on("connection", (ws) => {
  ws.send(JSON.stringify({ type: "fmt", ...AUDIO_FMT }));
});

let readOffset = 0;
let carry = Buffer.alloc(0); // 保证 int16 对齐（2 字节）

async function readNewBytesIfAny() {
  let st;
  try {
    st = await fs.promises.stat(AUDIO_PATH);
  } catch {
    return;
  }

  if (st.size < readOffset) {
    // 文件被截断/轮转
    readOffset = 0;
    carry = Buffer.alloc(0);
  }
  if (st.size === readOffset) return;

  const len = st.size - readOffset;
  const fh = await fs.promises.open(AUDIO_PATH, "r");
  try {
    const buf = Buffer.allocUnsafe(len);
    await fh.read(buf, 0, len, readOffset);
    readOffset = st.size;

    let out = carry.length ? Buffer.concat([carry, buf]) : buf;
    const alignedLen = out.length - (out.length % 2);
    carry = out.subarray(alignedLen);
    out = out.subarray(0, alignedLen);

    if (out.length) broadcastBinary(out);
  } finally {
    await fh.close();
  }
}

// 使用轮询，跨平台/稳定：把“增长文件”当作实时流
setInterval(() => {
  readNewBytesIfAny().catch(() => {
    // 忽略临时读错
  });
}, 30);

server.listen(PORT, () => {
  console.log(`Server listening on http://localhost:${PORT}`);
  console.log(`WebSocket: ws://localhost:${PORT}/ws`);
  console.log(`Audio file: ${AUDIO_PATH}`);
});

