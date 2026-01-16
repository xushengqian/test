const { Readable } = require('stream');
const path = require('path');
const UuidDisplace = require('./UuidDisplace');

// 模拟生成音频数据流 (简单的正弦波 PCM 数据)
function createSimulatedAudioStream(durationSeconds = 2, sampleRate = 44100) {
    const stream = new Readable({
        read() {} 
    });

    const frequency = 440; // A4 Note
    const amplitude = 32760; // 16-bit signed integer max (approx)
    const numSamples = durationSeconds * sampleRate;
    const bitDepth = 16;
    const byteDepth = bitDepth / 8;
    
    // 我们分块发送数据以模拟真实流
    const chunkSize = 1024; // 较小的 chunk size
    let currentSample = 0;

    // 使用 setImmediate 模拟异步生成，避免阻塞主线程太久
    const generate = () => {
        if (currentSample >= numSamples) {
            stream.push(null); // End of stream
            return;
        }

        const endSample = Math.min(currentSample + chunkSize, numSamples);
        const buffer = Buffer.alloc((endSample - currentSample) * byteDepth);

        for (let i = 0; i < endSample - currentSample; i++) {
            const t = (currentSample + i) / sampleRate;
            const value = Math.sin(2 * Math.PI * frequency * t) * amplitude;
            buffer.writeInt16LE(Math.floor(value), i * byteDepth);
        }

        // push 返回 false 意味着应该暂停读取
        if (stream.push(buffer)) {
             setImmediate(generate);
        } else {
             // 只有当流准备好再次接收数据时才恢复
             stream.once('drain', generate);
        }
        currentSample = endSample;
    };

    setImmediate(generate);

    return stream;
}

async function main() {
    const outputDir = path.join(__dirname, '../output');
    
    // 场景 1: 写入 FS
    console.log('--- Scenario 1: Write Stream to FS ---');
    const processor1 = new UuidDisplace();
    const stream1 = createSimulatedAudioStream(1); // 1 second
    
    try {
        const savedPath = await processor1.saveToFs(stream1, outputDir, 'pcm');
        console.log(`Success! File saved at: ${savedPath}`);
    } catch (err) {
        console.error('Failed to save file:', err);
    }

    // 场景 2: 播放流 (模拟)
    console.log('\n--- Scenario 2: Play Stream ---');
    const processor2 = new UuidDisplace();
    const stream2 = createSimulatedAudioStream(1); // 1 second
    processor2.play(stream2);
}

main();
