class PcmPlayerProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.srcSampleRate = 48000;
    this.dstSampleRate = sampleRate;
    this.ratio = this.srcSampleRate / this.dstSampleRate;

    this.queue = new Float32Array(0);
    this.qRead = 0;
    this.lastSample = 0;

    this.port.onmessage = (ev) => {
      const msg = ev.data;
      if (!msg || !msg.type) return;
      if (msg.type === "fmt" && msg.fmt?.sampleRate) {
        this.srcSampleRate = msg.fmt.sampleRate;
        this.dstSampleRate = msg.dstSampleRate || sampleRate;
        this.ratio = this.srcSampleRate / this.dstSampleRate;
      }
      if (msg.type === "pcm" && msg.pcm) {
        this._enqueuePcm16(msg.pcm);
      }
    };
  }

  _enqueuePcm16(arrayBuffer) {
    const bytes = new Uint8Array(arrayBuffer);
    const n = Math.floor(bytes.length / 2);
    if (n <= 0) return;

    const f32 = new Float32Array(n);
    for (let i = 0; i < n; i++) {
      const lo = bytes[i * 2];
      const hi = bytes[i * 2 + 1];
      let v = (hi << 8) | lo;
      if (v & 0x8000) v = v - 0x10000;
      f32[i] = v / 32768;
    }

    // 拼接到队列尾部（简单实现：copy）
    const remaining = this.queue.length - this.qRead;
    const next = new Float32Array(remaining + f32.length);
    if (remaining > 0) next.set(this.queue.subarray(this.qRead), 0);
    next.set(f32, remaining);
    this.queue = next;
    this.qRead = 0;
  }

  process(_inputs, outputs) {
    const out = outputs[0];
    const ch0 = out[0];
    const frames = ch0.length;

    // 线性插值重采样：src -> dst
    // 注意：这里只做单声道输出；多声道可扩展为 interleaved decode + 多通道 queue
    let qLen = this.queue.length - this.qRead;
    if (qLen <= 0) {
      ch0.fill(0);
      if (out.length > 1) out[1].fill(0);
      return true;
    }

    let srcPos = this._srcPos || 0;
    const ratio = this.ratio;

    for (let i = 0; i < frames; i++) {
      const idx = Math.floor(srcPos);
      const frac = srcPos - idx;

      // 需要 idx 与 idx+1 两个样本；如果不够就静音填充并保留状态
      const aIndex = this.qRead + idx;
      const bIndex = aIndex + 1;
      if (bIndex >= this.queue.length) {
        ch0[i] = 0;
        continue;
      }
      const a = this.queue[aIndex];
      const b = this.queue[bIndex];
      const y = a + (b - a) * frac;
      ch0[i] = y;
      srcPos += ratio;
    }

    // 消费掉已经用过的整数部分
    const consumed = Math.floor(srcPos);
    if (consumed > 0) {
      this.qRead += consumed;
      srcPos -= consumed;
      if (this.qRead > 4096 && this.qRead > this.queue.length / 2) {
        // 偶尔压缩队列避免无限增长
        const remaining2 = this.queue.length - this.qRead;
        const next = new Float32Array(remaining2);
        next.set(this.queue.subarray(this.qRead));
        this.queue = next;
        this.qRead = 0;
      }
    }

    this._srcPos = srcPos;

    // 复制到其他声道（如果有）
    for (let c = 1; c < out.length; c++) out[c].set(ch0);
    return true;
  }
}

registerProcessor("pcm-player", PcmPlayerProcessor);

