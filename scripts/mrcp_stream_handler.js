/**
 * FreeSWITCH MRCP 实时语音流处理 - Node.js 示例
 * 使用 mod_event_socket 获取实时语音流
 */

const net = require('net');
const fs = require('fs');
const EventEmitter = require('events');

class FreeSwitchESL extends EventEmitter {
    constructor(host = '127.0.0.1', port = 8021, password = 'ClueCon') {
        super();
        this.host = host;
        this.port = port;
        this.password = password;
        this.socket = null;
        this.connected = false;
        this.buffer = '';
    }

    connect() {
        return new Promise((resolve, reject) => {
            this.socket = net.createConnection(this.port, this.host, () => {
                console.log('已连接到 FreeSWITCH');
            });

            this.socket.on('data', (data) => {
                this.buffer += data.toString();
                this.processBuffer();
            });

            this.socket.on('error', (err) => {
                console.error('连接错误:', err);
                reject(err);
            });

            this.socket.on('close', () => {
                console.log('连接已关闭');
                this.connected = false;
            });

            // 等待欢迎消息
            setTimeout(() => {
                this.authenticate()
                    .then(() => {
                        this.connected = true;
                        resolve();
                    })
                    .catch(reject);
            }, 100);
        });
    }

    authenticate() {
        return new Promise((resolve, reject) => {
            this.sendCommand(`auth ${this.password}`)
                .then((response) => {
                    if (response.includes('OK')) {
                        console.log('认证成功');
                        resolve();
                    } else {
                        reject(new Error('认证失败'));
                    }
                });
        });
    }

    sendCommand(command) {
        return new Promise((resolve) => {
            if (!this.connected && command !== `auth ${this.password}`) {
                resolve('');
                return;
            }

            const responseHandler = (data) => {
                if (data.includes('\n\n')) {
                    this.socket.removeListener('data', responseHandler);
                    resolve(data);
                }
            };

            this.socket.once('data', responseHandler);
            this.socket.write(`${command}\n\n`);
        });
    }

    processBuffer() {
        while (this.buffer.includes('\n\n')) {
            const index = this.buffer.indexOf('\n\n');
            const message = this.buffer.substring(0, index);
            this.buffer = this.buffer.substring(index + 2);
            this.emit('message', message);
        }
    }

    subscribeEvents() {
        return this.sendCommand('event plain ALL');
    }

    originateCall(extension, context = 'default') {
        const command = `bgapi originate {origination_caller_id_number=1000,origination_caller_id_name=NodeJS}user/${extension} ${context} ${extension} 1`;
        return this.sendCommand(command);
    }

    getRealtimeStream(uuid, outputFile) {
        const command = `api uuid_record ${uuid} start ${outputFile}`;
        return this.sendCommand(command);
    }

    stopRecord(uuid) {
        const command = `api uuid_record ${uuid} stop`;
        return this.sendCommand(command);
    }

    disconnect() {
        if (this.socket) {
            this.socket.end();
        }
    }
}

// 使用示例
async function main() {
    console.log('FreeSWITCH ESL 实时语音流示例 (Node.js)');
    console.log('='.repeat(50));

    const esl = new FreeSwitchESL('127.0.0.1', 8021, 'ClueCon');

    try {
        // 连接
        await esl.connect();

        // 订阅事件
        await esl.subscribeEvents();
        console.log('已订阅事件');

        // 监听事件
        esl.on('message', (message) => {
            if (message.includes('CHANNEL_DATA')) {
                console.log('收到通道数据事件');
            }
        });

        // 发起呼叫
        console.log('\n发起呼叫到 MRCP 实时流扩展 (9999)...');
        await esl.originateCall('9999');

        // 等待处理
        console.log('等待 30 秒处理实时流...');
        await new Promise(resolve => setTimeout(resolve, 30000));

    } catch (error) {
        console.error('错误:', error);
    } finally {
        esl.disconnect();
    }
}

// 如果直接运行此脚本
if (require.main === module) {
    main().catch(console.error);
}

module.exports = FreeSwitchESL;
