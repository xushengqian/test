const fs = require('fs');
const path = require('path');
const { v4: uuidv4 } = require('uuid');

class UuidDisplace {
    constructor(id = null) {
        this.id = id || uuidv4();
        console.log(`[UuidDisplace] Initialized with ID: ${this.id}`);
    }

    /**
     * 将音频流写入文件系统
     * @param {import('stream').Readable} audioStream - 音频输入流
     * @param {string} outputDir - 输出目录
     * @param {string} extension - 文件扩展名 (e.g., 'wav', 'mp3', 'raw')
     * @returns {Promise<string>} - 返回写入的文件路径
     */
    saveToFs(audioStream, outputDir, extension = 'raw') {
        return new Promise((resolve, reject) => {
            // 确保目录存在
            if (!fs.existsSync(outputDir)) {
                fs.mkdirSync(outputDir, { recursive: true });
            }

            const fileName = `${this.id}.${extension}`;
            const filePath = path.join(outputDir, fileName);
            const fileStream = fs.createWriteStream(filePath);

            console.log(`[UuidDisplace] Starting to write stream to: ${filePath}`);

            // 将输入流 pipe 到文件输出流
            audioStream.pipe(fileStream);

            fileStream.on('finish', () => {
                console.log(`[UuidDisplace] Finished writing to: ${filePath}`);
                resolve(filePath);
            });

            fileStream.on('error', (err) => {
                console.error(`[UuidDisplace] Error writing file: ${err.message}`);
                reject(err);
            });
            
            audioStream.on('error', (err) => {
                 console.error(`[UuidDisplace] Error in input stream: ${err.message}`);
                 fileStream.end();
                 reject(err);
            });
        });
    }

    /**
     * 播放音频流
     * 注意：实际播放需要 'speaker' 模块和音频硬件支持。
     * 这里演示代码逻辑。
     * @param {import('stream').Readable} audioStream 
     */
    play(audioStream) {
        console.log(`[UuidDisplace] Preparing to play stream for ID: ${this.id}`);
        
        try {
            // 示例代码：如果安装了 speaker 模块
            // const Speaker = require('speaker');
            // const speaker = new Speaker({
            //   channels: 2,          // 2 channels
            //   bitDepth: 16,         // 16-bit samples
            //   sampleRate: 44100     // 44,100 Hz sample rate
            // });
            // audioStream.pipe(speaker);
            
            console.log('[UuidDisplace] 模拟播放: Stream is being processed...');
            
            // 仅仅为了演示流的消耗，我们消耗数据事件
            let bytesPlayed = 0;
            audioStream.on('data', (chunk) => {
                bytesPlayed += chunk.length;
            });

            audioStream.on('end', () => {
                console.log(`[UuidDisplace] Playback finished. Total bytes processed: ${bytesPlayed}`);
            });

        } catch (error) {
            console.error('[UuidDisplace] Playback error:', error);
        }
    }
}

module.exports = UuidDisplace;
