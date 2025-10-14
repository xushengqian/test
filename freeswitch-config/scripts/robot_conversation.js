/**
 * FreeSwitch 机器人对话脚本
 * 处理机器人呼出对话逻辑和转人工意图识别
 */

// 引入必要的模块
include("/usr/local/freeswitch/scripts/intent_recognition.js");
include("/usr/local/freeswitch/scripts/tts_handler.js");
include("/usr/local/freeswitch/scripts/asr_handler.js");

// 全局变量
var session = session;
var customerPhone = session.getVariable("customer_phone");
var robotSessionId = session.getVariable("robot_session_id");
var transferToAgent = false;
var conversationContext = {
    step: 0,
    userIntents: [],
    transferRequests: 0
};

// 主对话流程
function startRobotConversation() {
    console_log("INFO", "机器人对话开始 - 客户: " + customerPhone);
    
    // 检查通话是否已接通
    if (!session.ready()) {
        console_log("ERROR", "通话未接通");
        return;
    }
    
    // 开场白
    playWelcomeMessage();
    
    // 主对话循环
    var maxTurns = 10; // 最大对话轮数
    var currentTurn = 0;
    
    while (session.ready() && currentTurn < maxTurns && !transferToAgent) {
        currentTurn++;
        console_log("INFO", "对话轮次: " + currentTurn);
        
        // 获取用户语音输入
        var userInput = getUserSpeechInput();
        if (!userInput) {
            handleNoInput();
            continue;
        }
        
        // 意图识别
        var intent = recognizeIntent(userInput);
        conversationContext.userIntents.push(intent);
        
        // 检查转人工意图
        if (checkTransferIntent(intent, userInput)) {
            transferToAgent = true;
            break;
        }
        
        // 生成机器人回复
        var robotResponse = generateRobotResponse(intent, userInput, conversationContext);
        
        // 播放机器人回复
        playRobotResponse(robotResponse);
        
        // 更新对话上下文
        updateConversationContext(intent, userInput);
    }
    
    // 设置转人工标志
    session.setVariable("transfer_to_agent", transferToAgent.toString());
    
    if (transferToAgent) {
        console_log("INFO", "检测到转人工意图，准备转接");
        playTransferMessage();
    } else {
        console_log("INFO", "机器人对话正常结束");
        playGoodbyeMessage();
    }
}

// 播放欢迎语
function playWelcomeMessage() {
    var welcomeText = "您好，我是智能客服机器人，很高兴为您服务。请问有什么可以帮助您的吗？";
    playTTS(welcomeText);
}

// 获取用户语音输入
function getUserSpeechInput() {
    console_log("INFO", "等待用户语音输入...");
    
    // 设置ASR参数
    var asrParams = {
        timeout: 5000,
        maxSilence: 2000,
        grammar: "builtin:speech/transcribe"
    };
    
    // 执行语音识别
    var result = performASR(asrParams);
    
    if (result && result.text) {
        console_log("INFO", "用户输入: " + result.text);
        return result.text;
    }
    
    return null;
}

// 处理无输入情况
function handleNoInput() {
    var noInputText = "抱歉，我没有听清您说的话，请您再说一遍好吗？";
    playTTS(noInputText);
}

// 检查转人工意图
function checkTransferIntent(intent, userInput) {
    // 直接转人工关键词
    var transferKeywords = [
        "转人工", "人工客服", "人工服务", "转接人工",
        "我要找人工", "要人工", "人工", "客服",
        "转接", "转人", "找人", "真人",
        "不要机器人", "机器人不行", "你不行"
    ];
    
    // 检查关键词匹配
    for (var i = 0; i < transferKeywords.length; i++) {
        if (userInput.indexOf(transferKeywords[i]) !== -1) {
            console_log("INFO", "检测到转人工关键词: " + transferKeywords[i]);
            conversationContext.transferRequests++;
            return true;
        }
    }
    
    // 检查意图类型
    if (intent.type === "TRANSFER_TO_AGENT" || intent.confidence < 0.3) {
        console_log("INFO", "通过意图识别检测到转人工需求");
        conversationContext.transferRequests++;
        return true;
    }
    
    // 检查连续无法理解的情况
    if (intent.type === "UNKNOWN" && conversationContext.transferRequests >= 2) {
        console_log("INFO", "连续无法理解用户意图，建议转人工");
        return true;
    }
    
    // 检查用户情绪（愤怒、不满等）
    if (intent.emotion && (intent.emotion === "angry" || intent.emotion === "frustrated")) {
        console_log("INFO", "检测到用户负面情绪，建议转人工");
        conversationContext.transferRequests++;
        return conversationContext.transferRequests >= 1;
    }
    
    return false;
}

// 生成机器人回复
function generateRobotResponse(intent, userInput, context) {
    var response = "";
    
    switch (intent.type) {
        case "GREETING":
            response = "您好！很高兴为您服务，请问有什么可以帮助您的？";
            break;
            
        case "PRODUCT_INQUIRY":
            response = "关于产品咨询，我可以为您介绍我们的主要产品和服务。您想了解哪方面的信息呢？";
            break;
            
        case "COMPLAINT":
            response = "非常抱歉给您带来不便，我理解您的困扰。让我为您转接专业的客服人员来处理您的问题。";
            conversationContext.transferRequests++;
            break;
            
        case "TECHNICAL_SUPPORT":
            response = "关于技术问题，我建议您联系我们的技术专家。让我为您转接技术支持团队。";
            conversationContext.transferRequests++;
            break;
            
        case "UNKNOWN":
            response = "抱歉，我没有完全理解您的问题。您可以换个说法，或者我为您转接人工客服？";
            conversationContext.transferRequests++;
            break;
            
        default:
            response = "我明白了，让我为您查询相关信息。如果您需要更详细的帮助，我可以为您转接人工客服。";
            break;
    }
    
    return response;
}

// 播放机器人回复
function playRobotResponse(text) {
    console_log("INFO", "机器人回复: " + text);
    playTTS(text);
}

// 更新对话上下文
function updateConversationContext(intent, userInput) {
    conversationContext.step++;
    
    // 记录对话历史
    var logEntry = {
        step: conversationContext.step,
        timestamp: new Date().toISOString(),
        userInput: userInput,
        intent: intent,
        transferRequests: conversationContext.transferRequests
    };
    
    console_log("INFO", "对话上下文更新: " + JSON.stringify(logEntry));
}

// 播放转接提示
function playTransferMessage() {
    var transferText = "好的，我现在为您转接人工客服，请稍等片刻。";
    playTTS(transferText);
}

// 播放结束语
function playGoodbyeMessage() {
    var goodbyeText = "感谢您的来电，祝您生活愉快，再见！";
    playTTS(goodbyeText);
}

// 主函数入口
try {
    startRobotConversation();
} catch (e) {
    console_log("ERROR", "机器人对话异常: " + e.toString());
    session.setVariable("transfer_to_agent", "true");
}