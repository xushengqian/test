/**
 * ASR (Automatic Speech Recognition) 处理模块
 * 负责语音识别和语音输入处理
 */

// ASR 配置
var asrConfig = {
    engine: "pocketsphinx", // 可选: pocketsphinx, google, baidu, iflytek
    language: "zh-CN",
    timeout: 5000,
    maxSilence: 2000,
    minSpeechTimeout: 1000,
    maxSpeechTimeout: 10000,
    confidenceThreshold: 0.5
};

/**
 * 执行语音识别
 * @param {Object} options - ASR 参数
 * @returns {Object} 识别结果
 */
function performASR(options) {
    if (!session.ready()) {
        console_log("ERROR", "ASR失败: 会话不可用");
        return null;
    }
    
    options = options || {};
    var timeout = options.timeout || asrConfig.timeout;
    var maxSilence = options.maxSilence || asrConfig.maxSilence;
    var grammar = options.grammar || "builtin:speech/transcribe";
    
    console_log("INFO", "开始语音识别...");
    
    try {
        // 设置 ASR 参数
        session.execute("set", "asr_engine=" + asrConfig.engine);
        session.execute("set", "asr_language=" + asrConfig.language);
        
        // 执行语音识别
        var result = session.detectSpeech(
            asrConfig.engine,
            grammar,
            "",
            timeout,
            ""
        );
        
        // 等待识别结果
        var startTime = new Date().getTime();
        var maxWaitTime = timeout + 2000;
        
        while (session.ready()) {
            var currentTime = new Date().getTime();
            if (currentTime - startTime > maxWaitTime) {
                console_log("WARN", "ASR超时");
                break;
            }
            
            // 检查是否有识别结果
            var speechResult = session.getVariable("detect_speech_result");
            if (speechResult) {
                return parseASRResult(speechResult);
            }
            
            session.sleep(100);
        }
        
        console_log("WARN", "ASR未获取到结果");
        return null;
        
    } catch (e) {
        console_log("ERROR", "ASR异常: " + e.toString());
        return null;
    } finally {
        // 停止语音检测
        try {
            session.execute("detect_speech", "stop");
        } catch (e) {
            // 忽略停止异常
        }
    }
}

/**
 * 解析 ASR 结果
 * @param {string} rawResult - 原始识别结果
 * @returns {Object} 解析后的结果
 */
function parseASRResult(rawResult) {
    try {
        var result = {
            text: "",
            confidence: 0,
            alternatives: [],
            duration: 0,
            success: false
        };
        
        if (!rawResult) {
            return result;
        }
        
        // 尝试解析 JSON 格式结果
        if (rawResult.charAt(0) === '{') {
            var jsonResult = JSON.parse(rawResult);
            
            if (jsonResult.text) {
                result.text = jsonResult.text;
                result.confidence = jsonResult.confidence || 0;
                result.alternatives = jsonResult.alternatives || [];
                result.success = true;
            }
        } else {
            // 简单文本格式
            result.text = rawResult.trim();
            result.confidence = 0.8; // 默认置信度
            result.success = result.text.length > 0;
        }
        
        // 后处理文本
        result.text = postProcessASRText(result.text);
        
        console_log("INFO", "ASR结果: " + JSON.stringify(result));
        return result;
        
    } catch (e) {
        console_log("ERROR", "ASR结果解析失败: " + e.toString());
        return {
            text: rawResult || "",
            confidence: 0,
            alternatives: [],
            duration: 0,
            success: false
        };
    }
}

/**
 * ASR 文本后处理
 * @param {string} text - 原始识别文本
 * @returns {string} 处理后的文本
 */
function postProcessASRText(text) {
    if (!text) return "";
    
    // 去除多余空格
    text = text.replace(/\s+/g, " ").trim();
    
    // 数字转换
    text = text.replace(/零/g, "0");
    text = text.replace(/一/g, "1");
    text = text.replace(/二/g, "2");
    text = text.replace(/三/g, "3");
    text = text.replace(/四/g, "4");
    text = text.replace(/五/g, "5");
    text = text.replace(/六/g, "6");
    text = text.replace(/七/g, "7");
    text = text.replace(/八/g, "8");
    text = text.replace(/九/g, "9");
    text = text.replace(/十/g, "10");
    
    // 常见错误修正
    var corrections = {
        "人工客户": "人工客服",
        "转人公": "转人工",
        "机器人不行": "机器人不行",
        "我要找人": "我要找人工"
    };
    
    for (var wrong in corrections) {
        text = text.replace(new RegExp(wrong, "g"), corrections[wrong]);
    }
    
    return text;
}

/**
 * 带提示的语音输入
 * @param {string} prompt - 提示语
 * @param {Object} options - ASR 选项
 * @returns {Object} 识别结果
 */
function promptAndRecognize(prompt, options) {
    if (prompt) {
        playTTS(prompt);
        session.sleep(500); // 短暂停顿
    }
    
    return performASR(options);
}

/**
 * 多轮语音输入（带重试）
 * @param {string} prompt - 提示语
 * @param {number} maxRetries - 最大重试次数
 * @param {Object} options - ASR 选项
 * @returns {Object} 识别结果
 */
function multiTurnASR(prompt, maxRetries, options) {
    maxRetries = maxRetries || 3;
    var retryCount = 0;
    
    while (retryCount < maxRetries && session.ready()) {
        var result = promptAndRecognize(prompt, options);
        
        if (result && result.success && result.confidence >= asrConfig.confidenceThreshold) {
            return result;
        }
        
        retryCount++;
        
        if (retryCount < maxRetries) {
            var retryPrompt = "抱歉，我没有听清楚，请您再说一遍。";
            playTTS(retryPrompt);
            session.sleep(1000);
        }
    }
    
    console_log("WARN", "多轮ASR失败，重试次数: " + retryCount);
    return null;
}

/**
 * DTMF 输入检测
 * @param {number} timeout - 超时时间
 * @param {number} maxDigits - 最大位数
 * @returns {string} DTMF 输入
 */
function getDTMFInput(timeout, maxDigits) {
    timeout = timeout || 5000;
    maxDigits = maxDigits || 10;
    
    try {
        var digits = session.getDigits(maxDigits, "", timeout);
        console_log("INFO", "DTMF输入: " + digits);
        return digits;
    } catch (e) {
        console_log("ERROR", "DTMF输入失败: " + e.toString());
        return "";
    }
}

/**
 * 混合输入模式（语音 + DTMF）
 * @param {string} prompt - 提示语
 * @param {Object} options - 选项
 * @returns {Object} 输入结果
 */
function hybridInput(prompt, options) {
    options = options || {};
    var timeout = options.timeout || 10000;
    
    if (prompt) {
        playTTS(prompt + "您可以说话或按键输入。");
    }
    
    // 同时启动语音识别和 DTMF 检测
    var startTime = new Date().getTime();
    
    // 启动语音识别
    var asrStarted = false;
    try {
        session.execute("detect_speech", asrConfig.engine + " default default");
        asrStarted = true;
    } catch (e) {
        console_log("ERROR", "启动语音识别失败: " + e.toString());
    }
    
    while (session.ready()) {
        var currentTime = new Date().getTime();
        if (currentTime - startTime > timeout) {
            break;
        }
        
        // 检查 DTMF 输入
        var dtmf = session.getDigits(1, "", 100);
        if (dtmf && dtmf.length > 0) {
            // 停止语音识别
            if (asrStarted) {
                try {
                    session.execute("detect_speech", "stop");
                } catch (e) {}
            }
            
            return {
                type: "dtmf",
                value: dtmf,
                success: true
            };
        }
        
        // 检查语音识别结果
        if (asrStarted) {
            var speechResult = session.getVariable("detect_speech_result");
            if (speechResult) {
                var asrResult = parseASRResult(speechResult);
                if (asrResult && asrResult.success) {
                    return {
                        type: "speech",
                        value: asrResult.text,
                        confidence: asrResult.confidence,
                        success: true
                    };
                }
            }
        }
        
        session.sleep(100);
    }
    
    // 清理
    if (asrStarted) {
        try {
            session.execute("detect_speech", "stop");
        } catch (e) {}
    }
    
    return {
        type: "none",
        value: "",
        success: false
    };
}

/**
 * 设置 ASR 配置
 * @param {Object} config - ASR 配置
 */
function setASRConfig(config) {
    if (config.engine) asrConfig.engine = config.engine;
    if (config.language) asrConfig.language = config.language;
    if (config.timeout) asrConfig.timeout = config.timeout;
    if (config.maxSilence) asrConfig.maxSilence = config.maxSilence;
    if (config.confidenceThreshold) asrConfig.confidenceThreshold = config.confidenceThreshold;
    
    console_log("INFO", "ASR配置已更新: " + JSON.stringify(asrConfig));
}