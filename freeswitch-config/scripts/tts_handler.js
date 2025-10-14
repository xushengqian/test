/**
 * TTS (Text-to-Speech) 处理模块
 * 负责将文本转换为语音并播放
 */

// TTS 配置
var ttsConfig = {
    engine: "tts_commandline", // 可选: tts_commandline, mod_tts, mod_say
    voice: "xiaofang", // 中文语音
    rate: "medium",
    volume: "medium",
    timeout: 30000
};

/**
 * 播放 TTS 语音
 * @param {string} text - 要转换的文本
 * @param {Object} options - 可选参数
 */
function playTTS(text, options) {
    if (!text || !session.ready()) {
        console_log("ERROR", "TTS播放失败: 文本为空或会话不可用");
        return false;
    }
    
    options = options || {};
    var voice = options.voice || ttsConfig.voice;
    var rate = options.rate || ttsConfig.rate;
    var volume = options.volume || ttsConfig.volume;
    
    console_log("INFO", "TTS播放: " + text);
    
    try {
        // 使用 FreeSwitch 的 speak 应用
        var ttsCommand = "speak:" + voice + "|" + text;
        session.execute("speak", ttsCommand);
        
        // 等待播放完成
        session.sleep(500);
        
        return true;
    } catch (e) {
        console_log("ERROR", "TTS播放异常: " + e.toString());
        
        // 备用方案：使用预录音频文件
        return playFallbackAudio(text);
    }
}

/**
 * 备用音频播放方案
 * @param {string} text - 原始文本
 */
function playFallbackAudio(text) {
    try {
        // 根据文本内容选择预录音频
        var audioFile = getPreRecordedAudio(text);
        
        if (audioFile) {
            session.execute("playback", audioFile);
            return true;
        } else {
            // 播放通用提示音
            session.execute("playback", "/usr/local/freeswitch/sounds/robot_speaking.wav");
            return true;
        }
    } catch (e) {
        console_log("ERROR", "备用音频播放失败: " + e.toString());
        return false;
    }
}

/**
 * 获取预录音频文件
 * @param {string} text - 文本内容
 * @returns {string} 音频文件路径
 */
function getPreRecordedAudio(text) {
    var audioMap = {
        "您好": "/usr/local/freeswitch/sounds/greeting.wav",
        "转人工": "/usr/local/freeswitch/sounds/transfer_to_agent.wav",
        "再见": "/usr/local/freeswitch/sounds/goodbye.wav",
        "请稍等": "/usr/local/freeswitch/sounds/please_wait.wav",
        "抱歉": "/usr/local/freeswitch/sounds/sorry.wav"
    };
    
    // 查找匹配的关键词
    for (var keyword in audioMap) {
        if (text.indexOf(keyword) !== -1) {
            return audioMap[keyword];
        }
    }
    
    return null;
}

/**
 * 播放带停顿的长文本
 * @param {string} text - 长文本
 * @param {number} pauseMs - 停顿时间（毫秒）
 */
function playLongText(text, pauseMs) {
    pauseMs = pauseMs || 1000;
    
    // 按句号分割文本
    var sentences = text.split(/[。！？.!?]/);
    
    for (var i = 0; i < sentences.length; i++) {
        var sentence = sentences[i].trim();
        if (sentence.length > 0) {
            playTTS(sentence);
            
            // 句间停顿
            if (i < sentences.length - 1) {
                session.sleep(pauseMs);
            }
        }
    }
}

/**
 * 播放数字
 * @param {string|number} number - 数字
 */
function playNumber(number) {
    try {
        session.execute("say", "zh number pronounced " + number);
        return true;
    } catch (e) {
        console_log("ERROR", "数字播放失败: " + e.toString());
        return playTTS(number.toString());
    }
}

/**
 * 播放日期时间
 * @param {Date} date - 日期对象
 */
function playDateTime(date) {
    try {
        var timestamp = Math.floor(date.getTime() / 1000);
        session.execute("say", "zh current_date_time pronounced " + timestamp);
        return true;
    } catch (e) {
        console_log("ERROR", "日期时间播放失败: " + e.toString());
        return playTTS(date.toLocaleString("zh-CN"));
    }
}

/**
 * 设置 TTS 参数
 * @param {Object} config - TTS 配置
 */
function setTTSConfig(config) {
    if (config.voice) ttsConfig.voice = config.voice;
    if (config.rate) ttsConfig.rate = config.rate;
    if (config.volume) ttsConfig.volume = config.volume;
    if (config.timeout) ttsConfig.timeout = config.timeout;
    
    console_log("INFO", "TTS配置已更新: " + JSON.stringify(ttsConfig));
}