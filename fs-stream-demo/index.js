const esl = require('modesl');

// ==========================================
// 配置区域
// ==========================================
const LISTEN_PORT = 8021;
const LISTEN_IP = '0.0.0.0';

// 演示用的推流地址
// 1. shout:// 协议用于 MP3/HTTP 流 (需要加载 mod_shout)
// 2. tone_stream:// 用于生成测试音 (无需网络)
// 建议测试时使用 tone_stream 确保环境没问题，之后换成真实的 shout:// URL
// const STREAM_URL = 'shout://icecast.stream.com/live.mp3'; 
const STREAM_URL = 'tone_stream://%(20000,0,350,440)'; // 播放20秒的 440Hz 音

console.log(`启动 ESL Server 监听 ${LISTEN_IP}:${LISTEN_PORT}...`);
console.log('请在 FreeSWITCH Dialplan 中配置: <action application="socket" data="127.0.0.1:8021 async full"/>');

const server = new esl.Server({port: LISTEN_PORT, host: LISTEN_IP, myevents: true}, function() {
    const conn = this;
    const uuid = conn.uuid;
    
    console.log(`[${uuid}] 新呼叫进入`);

    // 1. 注册事件处理
    // 订阅 DTMF (按键) 和 DETECTED_SPEECH (语音检测 - 需要配置 VAD/ASR)
    // 'myevents' 参数在 new Server 时已开启，会自动订阅该 UUID 的相关事件
    // 但为了确保收到特定的 custom 事件，我们可以显式订阅
    conn.subscribe(['DTMF', 'DETECTED_SPEECH', 'CHANNEL_HANGUP']);

    // 2. 接听电话
    conn.execute('answer', '', () => {
        console.log(`[${uuid}] 电话已接听`);

        // 3. 模拟开启语音检测 (VAD/ASR)
        // 在真实场景中，这里会启动 mod_unimrcp 的 detect_speech
        // 或者 mod_vmd (Voice Mail Detection)
        // conn.execute('detect_speech', 'unimrcp:ali-cloud default');
        
        console.log(`[${uuid}] 开启信号检测...`);

        // 4. 开始播放流
        playStream(conn);
    });

    // ==========================================
    // 事件监听：打断逻辑核心
    // ==========================================

    // 处理 DTMF 打断 (用户按键)
    conn.on('esl::event::DTMF::*', (evt) => {
        const digit = evt.getHeader('DTMF-Digit');
        console.log(`[${uuid}] 收到 DTMF 按键: ${digit}，触发打断`);
        stopPlayback(conn);
    });

    // 处理语音打断 (需要 ASR/VAD 模块触发此事件)
    // 注意：如果是使用 mod_dptools 的 detect_audio 或 mod_vmd，事件名称可能不同
    conn.on('esl::event::DETECTED_SPEECH::*', (evt) => {
        const type = evt.getHeader('Speech-Type'); // 开始说话(start-of-speech) 或 结束(end-of-speech)
        console.log(`[${uuid}] 检测到语音事件 (${type})，触发打断`);
        
        // 通常在检测到开始说话时打断
        if (type.includes('start')) {
            stopPlayback(conn);
        }
    });

    conn.on('esl::event::CHANNEL_HANGUP::*', () => {
        console.log(`[${uuid}] 呼叫挂断`);
    });
});

/**
 * 执行播放命令
 */
function playStream(conn) {
    console.log(`[${conn.uuid}] 开始播放流: ${STREAM_URL}`);
    
    // playback 是阻塞式 App，但在 ESL Socket 模式下，
    // 我们发送命令后，FS 会执行。Node.js 继续运行（处理事件）。
    // 当 playback 完成（或被打断）时，回调会被触发。
    conn.execute('playback', STREAM_URL, (res) => {
        console.log(`[${conn.uuid}] 播放结束`);
        // 挂断或进行下一步
        // conn.execute('hangup');
    });
}

/**
 * 执行打断命令
 */
function stopPlayback(conn) {
    // uuid_break 是实现打断的关键 API
    // 它会停止当前正在执行的 Application (这里是 playback)
    // 并让 Dialplan 继续往下走，或者结束当前 App 的执行
    conn.api('uuid_break', conn.uuid, (res) => {
        console.log(`[${conn.uuid}] 打断指令发送结果:`, res.getBody().trim());
    });
}
