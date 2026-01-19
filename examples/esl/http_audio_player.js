/**
 * FreeSWITCH ESL HTTP 音频播放器
 * Node.js 版本
 * 
 * 依赖安装：
 *   npm install modesl
 * 
 * 使用示例：
 *   const player = new FreeSwitchHttpAudioPlayer('127.0.0.1', 8021, 'ClueCon');
 *   await player.connect();
 *   await player.playAudio('channel-uuid', 'http://audio.example.com/welcome.mp3');
 *   player.disconnect();
 */

const EventEmitter = require('events');

// 尝试加载 modesl
let esl;
try {
    esl = require('modesl');
} catch (e) {
    console.warn('modesl not installed. Please install with: npm install modesl');
}

/**
 * 播放结果类
 */
class PlaybackResult {
    constructor(success, response, playbackUrl = null, error = null) {
        this.success = success;
        this.response = response;
        this.playbackUrl = playbackUrl;
        this.error = error;
    }

    toString() {
        return JSON.stringify({
            success: this.success,
            response: this.response,
            playbackUrl: this.playbackUrl,
            error: this.error
        }, null, 2);
    }
}

/**
 * FreeSWITCH HTTP 音频播放器
 */
class FreeSwitchHttpAudioPlayer extends EventEmitter {
    /**
     * 构造函数
     * @param {string} host - FreeSWITCH 主机地址
     * @param {number} port - ESL 端口
     * @param {string} password - ESL 密码
     */
    constructor(host = '127.0.0.1', port = 8021, password = 'ClueCon') {
        super();
        this.host = host;
        this.port = port;
        this.password = password;
        this.conn = null;
        this.connected = false;
    }

    /**
     * 连接到 FreeSWITCH
     * @returns {Promise<boolean>}
     */
    connect() {
        return new Promise((resolve, reject) => {
            if (!esl) {
                reject(new Error('modesl library not available'));
                return;
            }

            this.conn = new esl.Connection(this.host, this.port, this.password, () => {
                this.connected = true;
                console.log(`Connected to FreeSWITCH at ${this.host}:${this.port}`);
                this.emit('connected');
                resolve(true);
            });

            this.conn.on('error', (err) => {
                console.error('Connection error:', err);
                this.connected = false;
                this.emit('error', err);
                reject(err);
            });

            this.conn.on('esl::end', () => {
                this.connected = false;
                this.emit('disconnected');
            });
        });
    }

    /**
     * 断开连接
     */
    disconnect() {
        if (this.conn) {
            this.conn.disconnect();
            this.connected = false;
            console.log('Disconnected from FreeSWITCH');
        }
    }

    /**
     * 检查是否已连接
     * @returns {boolean}
     */
    isConnected() {
        return this.connected && this.conn !== null;
    }

    /**
     * 获取播放 URL
     * @param {string} audioUrl - 原始音频 URL
     * @returns {string}
     */
    _getPlaybackUrl(audioUrl) {
        const lowerUrl = audioUrl.toLowerCase();

        // MP3 格式使用 shout 协议
        if (lowerUrl.endsWith('.mp3') || lowerUrl.includes('.mp3?')) {
            return `shout://${audioUrl}`;
        }

        // 其他格式使用 http_cache
        if (audioUrl.startsWith('http://') || audioUrl.startsWith('https://')) {
            return `http_cache://${audioUrl}`;
        }

        // 默认添加 http:// 前缀
        return `http_cache://http://${audioUrl}`;
    }

    /**
     * 执行 API 命令
     * @param {string} command - API 命令
     * @returns {Promise<string>}
     */
    _api(command) {
        return new Promise((resolve, reject) => {
            if (!this.isConnected()) {
                reject(new Error('Not connected to FreeSWITCH'));
                return;
            }

            this.conn.api(command, (res) => {
                const response = res ? res.getBody() : '';
                resolve(response);
            });
        });
    }

    /**
     * 播放 HTTP 音频
     * @param {string} uuid - 通道 UUID
     * @param {string} audioUrl - HTTP 音频 URL
     * @param {string} leg - 播放方向 (aleg, bleg, both)
     * @returns {Promise<PlaybackResult>}
     */
    async playAudio(uuid, audioUrl, leg = 'both') {
        if (!this.isConnected()) {
            return new PlaybackResult(false, '', null, 'Not connected to FreeSWITCH');
        }

        const playbackUrl = this._getPlaybackUrl(audioUrl);
        const command = `uuid_broadcast ${uuid} ${playbackUrl} ${leg}`;

        console.log(`Executing: ${command}`);

        try {
            const response = await this._api(command);
            const success = !response.includes('-ERR');

            if (success) {
                console.log(`Playback started for UUID: ${uuid}`);
            } else {
                console.warn(`Playback failed for UUID: ${uuid}, response: ${response}`);
            }

            return new PlaybackResult(success, response, playbackUrl);
        } catch (err) {
            return new PlaybackResult(false, '', null, err.message);
        }
    }

    /**
     * 停止播放
     * @param {string} uuid - 通道 UUID
     * @returns {Promise<PlaybackResult>}
     */
    async stopPlayback(uuid) {
        const command = `uuid_break ${uuid} all`;
        console.log(`Stopping playback for UUID: ${uuid}`);

        try {
            const response = await this._api(command);
            const success = !response.includes('-ERR');
            return new PlaybackResult(success, response);
        } catch (err) {
            return new PlaybackResult(false, '', null, err.message);
        }
    }

    /**
     * 叠加播放音频
     * @param {string} uuid - 通道 UUID
     * @param {string} audioUrl - HTTP 音频 URL
     * @param {number} volume - 音量调整 (-4 到 4)
     * @returns {Promise<PlaybackResult>}
     */
    async overlayAudio(uuid, audioUrl, volume = 0) {
        const playbackUrl = this._getPlaybackUrl(audioUrl);
        const command = `uuid_displace ${uuid} start ${playbackUrl} ${volume} mux`;

        console.log(`Overlay audio for UUID: ${uuid}`);

        try {
            const response = await this._api(command);
            const success = !response.includes('-ERR');
            return new PlaybackResult(success, response, playbackUrl);
        } catch (err) {
            return new PlaybackResult(false, '', null, err.message);
        }
    }

    /**
     * 停止叠加音频
     * @param {string} uuid - 通道 UUID
     * @param {string} audioUrl - 要停止的音频 URL
     * @returns {Promise<PlaybackResult>}
     */
    async stopOverlay(uuid, audioUrl) {
        const playbackUrl = this._getPlaybackUrl(audioUrl);
        const command = `uuid_displace ${uuid} stop ${playbackUrl}`;

        try {
            const response = await this._api(command);
            const success = !response.includes('-ERR');
            return new PlaybackResult(success, response);
        } catch (err) {
            return new PlaybackResult(false, '', null, err.message);
        }
    }

    /**
     * 播放音频列表
     * @param {string} uuid - 通道 UUID
     * @param {string[]} audioUrls - 音频 URL 列表
     * @param {number} intervalMs - 音频之间的间隔（毫秒）
     * @returns {Promise<PlaybackResult[]>}
     */
    async playPlaylist(uuid, audioUrls, intervalMs = 500) {
        const results = [];

        for (let i = 0; i < audioUrls.length; i++) {
            const audioUrl = audioUrls[i];
            console.log(`Playing ${i + 1}/${audioUrls.length}: ${audioUrl}`);

            const result = await this.playAudio(uuid, audioUrl);
            results.push(result);

            if (!result.success) {
                console.warn(`Failed to play: ${audioUrl}`);
            }

            if (i < audioUrls.length - 1) {
                await this._sleep(intervalMs);
            }
        }

        return results;
    }

    /**
     * 设置通道变量
     * @param {string} uuid - 通道 UUID
     * @param {string} name - 变量名
     * @param {string} value - 变量值
     * @returns {Promise<boolean>}
     */
    async setVariable(uuid, name, value) {
        const command = `uuid_setvar ${uuid} ${name} ${value}`;
        const response = await this._api(command);
        return !response.includes('-ERR');
    }

    /**
     * 获取通道变量
     * @param {string} uuid - 通道 UUID
     * @param {string} name - 变量名
     * @returns {Promise<string|null>}
     */
    async getVariable(uuid, name) {
        const command = `uuid_getvar ${uuid} ${name}`;
        const response = await this._api(command);

        if (!response.includes('-ERR')) {
            return response.trim();
        }
        return null;
    }

    /**
     * 发起呼叫
     * @param {string} destination - 目标号码
     * @param {Object} options - 选项
     * @returns {Promise<string|null>}
     */
    async originate(destination, options = {}) {
        const {
            callerIdNumber = '',
            callerIdName = '',
            gateway = 'default',
            context = 'default',
            extension = '&park'
        } = options;

        let varsStr = '';
        if (callerIdNumber) varsStr += `origination_caller_id_number=${callerIdNumber},`;
        if (callerIdName) varsStr += `origination_caller_id_name=${callerIdName},`;
        varsStr = varsStr.replace(/,$/, '');

        let command;
        if (varsStr) {
            command = `originate {${varsStr}}sofia/gateway/${gateway}/${destination} ${extension}`;
        } else {
            command = `originate sofia/gateway/${gateway}/${destination} ${extension}`;
        }

        try {
            const response = await this._api(command);

            if (!response.includes('-ERR') && response.startsWith('+OK')) {
                const uuid = response.replace('+OK', '').trim();
                console.log(`Originate successful, UUID: ${uuid}`);
                return uuid;
            }

            console.error(`Originate failed: ${response}`);
            return null;
        } catch (err) {
            console.error(`Originate error: ${err.message}`);
            return null;
        }
    }

    /**
     * 挂断通道
     * @param {string} uuid - 通道 UUID
     * @param {string} cause - 挂断原因
     * @returns {Promise<boolean>}
     */
    async hangup(uuid, cause = 'NORMAL_CLEARING') {
        const command = `uuid_kill ${uuid} ${cause}`;
        const response = await this._api(command);
        return !response.includes('-ERR');
    }

    /**
     * 转接通道
     * @param {string} uuid - 通道 UUID
     * @param {string} destination - 目标
     * @param {string} context - 上下文
     * @returns {Promise<boolean>}
     */
    async transfer(uuid, destination, context = 'default') {
        const command = `uuid_transfer ${uuid} ${destination} XML ${context}`;
        const response = await this._api(command);
        return !response.includes('-ERR');
    }

    /**
     * 辅助方法：等待
     * @param {number} ms - 毫秒
     * @returns {Promise<void>}
     */
    _sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }
}

/**
 * 带事件监听的播放器
 */
class FreeSwitchHttpAudioPlayerWithEvents extends FreeSwitchHttpAudioPlayer {
    constructor(host, port, password) {
        super(host, port, password);
        this._eventHandlers = {};
    }

    async connect() {
        await super.connect();

        // 订阅事件
        this.conn.subscribe(['PLAYBACK_START', 'PLAYBACK_STOP', 'CHANNEL_HANGUP']);

        // 监听事件
        this.conn.on('esl::event::PLAYBACK_START::*', (event) => {
            this._handleEvent('PLAYBACK_START', event);
        });

        this.conn.on('esl::event::PLAYBACK_STOP::*', (event) => {
            this._handleEvent('PLAYBACK_STOP', event);
        });

        this.conn.on('esl::event::CHANNEL_HANGUP::*', (event) => {
            this._handleEvent('CHANNEL_HANGUP', event);
        });

        return true;
    }

    _handleEvent(eventName, event) {
        const eventData = {
            eventName: eventName,
            uuid: event.getHeader('Unique-ID'),
            callerIdNumber: event.getHeader('Caller-Caller-ID-Number'),
            destinationNumber: event.getHeader('Caller-Destination-Number'),
            playbackFilePath: event.getHeader('Playback-File-Path')
        };

        this.emit(eventName.toLowerCase(), eventData);

        if (this._eventHandlers[eventName]) {
            this._eventHandlers[eventName].forEach(handler => {
                try {
                    handler(eventData);
                } catch (err) {
                    console.error(`Event handler error: ${err.message}`);
                }
            });
        }
    }

    /**
     * 注册播放开始事件处理器
     * @param {Function} handler
     */
    onPlaybackStart(handler) {
        if (!this._eventHandlers['PLAYBACK_START']) {
            this._eventHandlers['PLAYBACK_START'] = [];
        }
        this._eventHandlers['PLAYBACK_START'].push(handler);
    }

    /**
     * 注册播放结束事件处理器
     * @param {Function} handler
     */
    onPlaybackStop(handler) {
        if (!this._eventHandlers['PLAYBACK_STOP']) {
            this._eventHandlers['PLAYBACK_STOP'] = [];
        }
        this._eventHandlers['PLAYBACK_STOP'].push(handler);
    }

    /**
     * 注册挂断事件处理器
     * @param {Function} handler
     */
    onHangup(handler) {
        if (!this._eventHandlers['CHANNEL_HANGUP']) {
            this._eventHandlers['CHANNEL_HANGUP'] = [];
        }
        this._eventHandlers['CHANNEL_HANGUP'].push(handler);
    }
}

// 导出
module.exports = {
    FreeSwitchHttpAudioPlayer,
    FreeSwitchHttpAudioPlayerWithEvents,
    PlaybackResult
};

// 示例用法
async function main() {
    const player = new FreeSwitchHttpAudioPlayer('127.0.0.1', 8021, 'ClueCon');

    try {
        await player.connect();

        // 示例 1：播放 HTTP 音频
        const uuid = 'your-channel-uuid-here';
        const audioUrl = 'http://audio.example.com/welcome.mp3';

        const result = await player.playAudio(uuid, audioUrl);
        console.log('Playback result:', result.toString());

        // 示例 2：播放音频列表
        const playlist = [
            'http://audio.example.com/audio1.mp3',
            'http://audio.example.com/audio2.mp3',
            'http://audio.example.com/audio3.mp3'
        ];

        const results = await player.playPlaylist(uuid, playlist, 1000);
        results.forEach((r, i) => {
            console.log(`Playlist item ${i + 1}: ${r.success}`);
        });

        // 等待一段时间
        await new Promise(resolve => setTimeout(resolve, 5000));

        // 停止播放
        await player.stopPlayback(uuid);

    } catch (err) {
        console.error('Error:', err);
    } finally {
        player.disconnect();
    }
}

// 带事件的示例
async function exampleWithEvents() {
    const player = new FreeSwitchHttpAudioPlayerWithEvents('127.0.0.1', 8021, 'ClueCon');

    // 注册事件处理器
    player.onPlaybackStart((event) => {
        console.log('Playback started:', event);
    });

    player.onPlaybackStop((event) => {
        console.log('Playback stopped:', event);
    });

    try {
        await player.connect();

        const uuid = 'your-channel-uuid-here';
        await player.playAudio(uuid, 'http://audio.example.com/test.mp3');

        // 等待事件
        await new Promise(resolve => setTimeout(resolve, 10000));

    } finally {
        player.disconnect();
    }
}

// 如果直接运行
if (require.main === module) {
    main().catch(console.error);
}
