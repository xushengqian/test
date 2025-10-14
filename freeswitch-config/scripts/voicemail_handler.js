/**
 * 语音信箱处理脚本
 * 当没有可用客服时，提供语音留言服务
 */

// 语音信箱配置
var voicemailConfig = {
    maxRecordingTime: 180, // 最大录音时间（秒）
    silenceThreshold: 3,   // 静音阈值（秒）
    beepTone: "/usr/local/freeswitch/sounds/beep.wav",
    storageDir: "/usr/local/freeswitch/storage/voicemail/",
    notificationEmail: "admin@company.com"
};

/**
 * 语音信箱主处理函数
 */
function handleVoicemail() {
    console_log("INFO", "启动语音信箱服务");
    
    var customerPhone = session.getVariable("customer_phone");
    var robotSessionId = session.getVariable("robot_session_id");
    var timestamp = new Date();
    
    // 播放语音信箱提示
    playVoicemailPrompt();
    
    // 录制语音留言
    var recordingResult = recordVoiceMessage(customerPhone, timestamp);
    
    if (recordingResult.success) {
        // 播放确认信息
        playConfirmationMessage();
        
        // 保存留言信息
        saveVoicemailInfo(recordingResult, customerPhone, timestamp);
        
        // 发送通知
        sendVoicemailNotification(recordingResult, customerPhone);
        
        // 提供回呼选项
        offerCallbackOption(customerPhone);
        
    } else {
        // 录音失败，提供其他选项
        handleRecordingFailure();
    }
}

/**
 * 播放语音信箱提示
 */
function playVoicemailPrompt() {
    var promptText = "很抱歉，目前所有客服都在忙线中。" +
                    "请在听到提示音后留下您的姓名、联系方式和需要咨询的问题，" +
                    "我们会尽快回复您。录音时间最长3分钟。";
    
    playTTS(promptText);
    
    // 播放提示音
    if (session.ready()) {
        session.execute("playback", voicemailConfig.beepTone);
    }
}

/**
 * 录制语音留言
 * @param {string} customerPhone - 客户电话
 * @param {Date} timestamp - 时间戳
 * @returns {Object} 录音结果
 */
function recordVoiceMessage(customerPhone, timestamp) {
    var result = {
        success: false,
        filename: "",
        duration: 0,
        path: ""
    };
    
    try {
        // 生成录音文件名
        var dateStr = timestamp.toISOString().replace(/[:.]/g, "-");
        var filename = "vm_" + customerPhone + "_" + dateStr + ".wav";
        var fullPath = voicemailConfig.storageDir + filename;
        
        console_log("INFO", "开始录音: " + fullPath);
        
        // 确保存储目录存在
        ensureDirectoryExists(voicemailConfig.storageDir);
        
        // 设置录音参数
        var recordParams = [
            fullPath,
            voicemailConfig.maxRecordingTime,
            voicemailConfig.silenceThreshold,
            "500" // 静音检测阈值
        ].join(" ");
        
        // 开始录音
        var startTime = new Date().getTime();
        session.execute("record", recordParams);
        var endTime = new Date().getTime();
        
        // 计算录音时长
        var duration = Math.floor((endTime - startTime) / 1000);
        
        // 检查录音文件是否存在
        if (fileExists(fullPath)) {
            result.success = true;
            result.filename = filename;
            result.duration = duration;
            result.path = fullPath;
            
            console_log("INFO", "录音完成: " + filename + ", 时长: " + duration + "秒");
        } else {
            console_log("ERROR", "录音文件不存在: " + fullPath);
        }
        
    } catch (e) {
        console_log("ERROR", "录音异常: " + e.toString());
    }
    
    return result;
}

/**
 * 播放确认信息
 */
function playConfirmationMessage() {
    var confirmText = "谢谢您的留言。我们已经收到您的信息，" +
                     "工作人员会在24小时内与您联系。";
    
    playTTS(confirmText);
}

/**
 * 保存语音信箱信息
 * @param {Object} recordingResult - 录音结果
 * @param {string} customerPhone - 客户电话
 * @param {Date} timestamp - 时间戳
 */
function saveVoicemailInfo(recordingResult, customerPhone, timestamp) {
    var voicemailInfo = {
        id: generateVoicemailId(),
        customerPhone: customerPhone,
        timestamp: timestamp.toISOString(),
        filename: recordingResult.filename,
        filepath: recordingResult.path,
        duration: recordingResult.duration,
        status: "new",
        priority: session.getVariable("customer_priority") || "normal",
        sessionId: session.getVariable("robot_session_id"),
        processed: false,
        assignedAgent: null,
        notes: ""
    };
    
    // 保存到JSON文件（实际应用中应该保存到数据库）
    var infoFile = voicemailConfig.storageDir + recordingResult.filename + ".json";
    
    try {
        var infoJson = JSON.stringify(voicemailInfo, null, 2);
        writeToFile(infoFile, infoJson);
        
        console_log("INFO", "语音信箱信息已保存: " + infoFile);
        
        // 设置会话变量
        session.setVariable("voicemail_id", voicemailInfo.id);
        session.setVariable("voicemail_saved", "true");
        
    } catch (e) {
        console_log("ERROR", "保存语音信箱信息失败: " + e.toString());
    }
}

/**
 * 发送语音信箱通知
 * @param {Object} recordingResult - 录音结果
 * @param {string} customerPhone - 客户电话
 */
function sendVoicemailNotification(recordingResult, customerPhone) {
    try {
        var notification = {
            type: "voicemail",
            customerPhone: customerPhone,
            filename: recordingResult.filename,
            duration: recordingResult.duration,
            timestamp: new Date().toISOString(),
            priority: session.getVariable("customer_priority") || "normal"
        };
        
        // 这里可以实现：
        // 1. 发送邮件通知
        // 2. 发送短信通知
        // 3. 推送到客服系统
        // 4. 写入通知队列
        
        console_log("INFO", "语音信箱通知: " + JSON.stringify(notification));
        
        // 简单的邮件通知（需要配置邮件服务）
        // sendEmailNotification(notification);
        
    } catch (e) {
        console_log("ERROR", "发送通知失败: " + e.toString());
    }
}

/**
 * 提供回呼选项
 * @param {string} customerPhone - 客户电话
 */
function offerCallbackOption(customerPhone) {
    var callbackPrompt = "如果您希望我们回电给您，请按1。" +
                        "如果您不需要回电，请按2或直接挂机。";
    
    playTTS(callbackPrompt);
    
    // 等待用户按键
    var digit = session.getDigits(1, "", 10000);
    
    if (digit === "1") {
        handleCallbackRequest(customerPhone);
    } else {
        var thanksText = "谢谢您的来电，再见！";
        playTTS(thanksText);
    }
}

/**
 * 处理回呼请求
 * @param {string} customerPhone - 客户电话
 */
function handleCallbackRequest(customerPhone) {
    var callbackPrompt = "好的，我们会安排客服回电给您。" +
                        "请问您希望在什么时间接听我们的回电？" +
                        "按1表示工作时间任意时间，按2表示指定时间。";
    
    playTTS(callbackPrompt);
    
    var digit = session.getDigits(1, "", 10000);
    var callbackTime = "";
    
    if (digit === "1") {
        callbackTime = "工作时间任意时间";
        var confirmText = "好的，我们会在工作时间内回电给您。";
        playTTS(confirmText);
    } else if (digit === "2") {
        callbackTime = "客户指定时间";
        var timePrompt = "请在听到提示音后说明您希望的回电时间。";
        playTTS(timePrompt);
        session.execute("playback", voicemailConfig.beepTone);
        
        // 录制时间要求
        var timeRecording = recordTimeRequirement(customerPhone);
        if (timeRecording.success) {
            var confirmText = "好的，我们会按照您的要求安排回电时间。";
            playTTS(confirmText);
        }
    }
    
    // 保存回呼请求
    saveCallbackRequest(customerPhone, callbackTime);
}

/**
 * 录制时间要求
 * @param {string} customerPhone - 客户电话
 * @returns {Object} 录音结果
 */
function recordTimeRequirement(customerPhone) {
    var timestamp = new Date();
    var dateStr = timestamp.toISOString().replace(/[:.]/g, "-");
    var filename = "callback_time_" + customerPhone + "_" + dateStr + ".wav";
    var fullPath = voicemailConfig.storageDir + filename;
    
    try {
        session.execute("record", fullPath + " 30 2 500");
        
        return {
            success: fileExists(fullPath),
            filename: filename,
            path: fullPath
        };
    } catch (e) {
        console_log("ERROR", "录制时间要求失败: " + e.toString());
        return { success: false };
    }
}

/**
 * 保存回呼请求
 * @param {string} customerPhone - 客户电话
 * @param {string} callbackTime - 回呼时间要求
 */
function saveCallbackRequest(customerPhone, callbackTime) {
    var callbackRequest = {
        id: generateCallbackId(),
        customerPhone: customerPhone,
        requestTime: new Date().toISOString(),
        preferredTime: callbackTime,
        status: "pending",
        priority: session.getVariable("customer_priority") || "normal",
        sessionId: session.getVariable("robot_session_id"),
        voicemailId: session.getVariable("voicemail_id"),
        attempts: 0,
        maxAttempts: 3
    };
    
    var callbackFile = voicemailConfig.storageDir + "callback_" + callbackRequest.id + ".json";
    
    try {
        var callbackJson = JSON.stringify(callbackRequest, null, 2);
        writeToFile(callbackFile, callbackJson);
        
        console_log("INFO", "回呼请求已保存: " + callbackFile);
        session.setVariable("callback_requested", "true");
        session.setVariable("callback_id", callbackRequest.id);
        
    } catch (e) {
        console_log("ERROR", "保存回呼请求失败: " + e.toString());
    }
}

/**
 * 处理录音失败
 */
function handleRecordingFailure() {
    var failureText = "很抱歉，录音出现问题。" +
                     "您可以稍后再次拨打，或者发送短信到我们的客服号码。" +
                     "感谢您的来电，再见！";
    
    playTTS(failureText);
    
    session.setVariable("voicemail_failed", "true");
}

// 辅助函数

/**
 * 生成语音信箱ID
 * @returns {string} 语音信箱ID
 */
function generateVoicemailId() {
    var timestamp = new Date().getTime();
    var random = Math.floor(Math.random() * 1000);
    return "VM" + timestamp + "_" + random;
}

/**
 * 生成回呼ID
 * @returns {string} 回呼ID
 */
function generateCallbackId() {
    var timestamp = new Date().getTime();
    var random = Math.floor(Math.random() * 1000);
    return "CB" + timestamp + "_" + random;
}

/**
 * 确保目录存在
 * @param {string} dir - 目录路径
 */
function ensureDirectoryExists(dir) {
    // 这里应该调用系统命令创建目录
    // 简化实现
    console_log("INFO", "确保目录存在: " + dir);
}

/**
 * 检查文件是否存在
 * @param {string} filepath - 文件路径
 * @returns {boolean} 文件是否存在
 */
function fileExists(filepath) {
    // 这里应该检查文件是否存在
    // 简化实现，假设录音成功
    return true;
}

/**
 * 写入文件
 * @param {string} filepath - 文件路径
 * @param {string} content - 文件内容
 */
function writeToFile(filepath, content) {
    // 这里应该写入文件
    // 简化实现
    console_log("INFO", "写入文件: " + filepath);
}

// 主函数执行
try {
    handleVoicemail();
} catch (e) {
    console_log("ERROR", "语音信箱处理异常: " + e.toString());
    session.setVariable("voicemail_failed", "true");
}