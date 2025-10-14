/**
 * 意图识别模块
 * 分析用户输入，识别用户意图和情感
 */

// 意图类型定义
var IntentTypes = {
    GREETING: "GREETING",
    PRODUCT_INQUIRY: "PRODUCT_INQUIRY", 
    COMPLAINT: "COMPLAINT",
    TECHNICAL_SUPPORT: "TECHNICAL_SUPPORT",
    TRANSFER_TO_AGENT: "TRANSFER_TO_AGENT",
    GOODBYE: "GOODBYE",
    UNKNOWN: "UNKNOWN"
};

// 情感类型定义
var EmotionTypes = {
    NEUTRAL: "neutral",
    HAPPY: "happy", 
    ANGRY: "angry",
    FRUSTRATED: "frustrated",
    SATISFIED: "satisfied"
};

// 意图识别规则
var intentRules = {
    [IntentTypes.GREETING]: {
        keywords: ["你好", "您好", "hello", "hi", "早上好", "下午好", "晚上好"],
        patterns: [/^(你好|您好|hello|hi)/i],
        confidence: 0.9
    },
    
    [IntentTypes.TRANSFER_TO_AGENT]: {
        keywords: [
            "转人工", "人工客服", "人工服务", "转接人工", "我要找人工",
            "要人工", "人工", "客服", "转接", "转人", "找人", "真人",
            "不要机器人", "机器人不行", "你不行", "转接客服"
        ],
        patterns: [
            /转.*人工/i,
            /人工.*客服/i,
            /不要.*机器人/i,
            /机器人.*不行/i
        ],
        confidence: 0.95
    },
    
    [IntentTypes.COMPLAINT]: {
        keywords: [
            "投诉", "抱怨", "不满意", "问题", "故障", "错误", "失望",
            "糟糕", "差劲", "不好", "退款", "赔偿", "解决不了"
        ],
        patterns: [
            /投诉|抱怨|不满/i,
            /有问题|出问题/i,
            /退款|赔偿/i
        ],
        confidence: 0.8,
        emotion: EmotionTypes.ANGRY
    },
    
    [IntentTypes.PRODUCT_INQUIRY]: {
        keywords: [
            "产品", "服务", "价格", "费用", "多少钱", "怎么样", "介绍",
            "功能", "特点", "优势", "购买", "订购"
        ],
        patterns: [
            /产品.*怎么样/i,
            /多少钱|什么价格/i,
            /介绍.*产品/i
        ],
        confidence: 0.7
    },
    
    [IntentTypes.TECHNICAL_SUPPORT]: {
        keywords: [
            "技术", "支持", "帮助", "使用", "操作", "设置", "配置",
            "安装", "连接", "登录", "密码", "账号"
        ],
        patterns: [
            /技术.*支持/i,
            /怎么.*使用/i,
            /如何.*操作/i
        ],
        confidence: 0.7
    },
    
    [IntentTypes.GOODBYE]: {
        keywords: ["再见", "拜拜", "谢谢", "好的", "没事了", "结束"],
        patterns: [/再见|拜拜|谢谢.*再见/i],
        confidence: 0.8
    }
};

// 情感识别规则
var emotionRules = {
    [EmotionTypes.ANGRY]: {
        keywords: [
            "气死了", "太气人", "愤怒", "生气", "火大", "烦死了",
            "什么破", "垃圾", "差劲", "糟糕透了"
        ],
        patterns: [/气死|愤怒|生气|火大/i]
    },
    
    [EmotionTypes.FRUSTRATED]: {
        keywords: [
            "郁闷", "烦躁", "无语", "头疼", "麻烦", "复杂",
            "搞不懂", "弄不明白", "太难了"
        ],
        patterns: [/郁闷|烦躁|无语|搞不懂/i]
    },
    
    [EmotionTypes.HAPPY]: {
        keywords: [
            "开心", "高兴", "满意", "不错", "很好", "棒",
            "谢谢", "感谢", "太好了"
        ],
        patterns: [/开心|高兴|满意|很好|太好了/i]
    }
};

/**
 * 主要意图识别函数
 * @param {string} userInput - 用户输入文本
 * @returns {Object} 识别结果
 */
function recognizeIntent(userInput) {
    if (!userInput || typeof userInput !== 'string') {
        return {
            type: IntentTypes.UNKNOWN,
            confidence: 0,
            emotion: EmotionTypes.NEUTRAL,
            keywords: [],
            rawInput: userInput
        };
    }
    
    var cleanInput = userInput.toLowerCase().trim();
    var result = {
        type: IntentTypes.UNKNOWN,
        confidence: 0,
        emotion: EmotionTypes.NEUTRAL,
        keywords: [],
        rawInput: userInput
    };
    
    // 遍历所有意图规则
    for (var intentType in intentRules) {
        var rule = intentRules[intentType];
        var score = calculateIntentScore(cleanInput, rule);
        
        if (score > result.confidence) {
            result.type = intentType;
            result.confidence = score;
            result.keywords = findMatchedKeywords(cleanInput, rule.keywords);
            
            // 设置关联的情感
            if (rule.emotion) {
                result.emotion = rule.emotion;
            }
        }
    }
    
    // 单独进行情感识别
    if (result.emotion === EmotionTypes.NEUTRAL) {
        result.emotion = recognizeEmotion(cleanInput);
    }
    
    // 记录识别结果
    console_log("INFO", "意图识别结果: " + JSON.stringify(result));
    
    return result;
}

/**
 * 计算意图匹配分数
 * @param {string} input - 用户输入
 * @param {Object} rule - 意图规则
 * @returns {number} 匹配分数
 */
function calculateIntentScore(input, rule) {
    var score = 0;
    var keywordMatches = 0;
    var patternMatches = 0;
    
    // 关键词匹配
    if (rule.keywords) {
        for (var i = 0; i < rule.keywords.length; i++) {
            if (input.indexOf(rule.keywords[i]) !== -1) {
                keywordMatches++;
            }
        }
        
        if (keywordMatches > 0) {
            score += (keywordMatches / rule.keywords.length) * 0.6;
        }
    }
    
    // 正则模式匹配
    if (rule.patterns) {
        for (var j = 0; j < rule.patterns.length; j++) {
            if (rule.patterns[j].test(input)) {
                patternMatches++;
            }
        }
        
        if (patternMatches > 0) {
            score += 0.4;
        }
    }
    
    // 应用基础置信度
    if (score > 0 && rule.confidence) {
        score *= rule.confidence;
    }
    
    return Math.min(score, 1.0);
}

/**
 * 识别情感
 * @param {string} input - 用户输入
 * @returns {string} 情感类型
 */
function recognizeEmotion(input) {
    var maxScore = 0;
    var detectedEmotion = EmotionTypes.NEUTRAL;
    
    for (var emotionType in emotionRules) {
        var rule = emotionRules[emotionType];
        var score = 0;
        
        // 关键词匹配
        if (rule.keywords) {
            for (var i = 0; i < rule.keywords.length; i++) {
                if (input.indexOf(rule.keywords[i]) !== -1) {
                    score += 0.3;
                }
            }
        }
        
        // 模式匹配
        if (rule.patterns) {
            for (var j = 0; j < rule.patterns.length; j++) {
                if (rule.patterns[j].test(input)) {
                    score += 0.5;
                }
            }
        }
        
        if (score > maxScore) {
            maxScore = score;
            detectedEmotion = emotionType;
        }
    }
    
    return detectedEmotion;
}

/**
 * 查找匹配的关键词
 * @param {string} input - 用户输入
 * @param {Array} keywords - 关键词列表
 * @returns {Array} 匹配的关键词
 */
function findMatchedKeywords(input, keywords) {
    var matched = [];
    
    if (keywords) {
        for (var i = 0; i < keywords.length; i++) {
            if (input.indexOf(keywords[i]) !== -1) {
                matched.push(keywords[i]);
            }
        }
    }
    
    return matched;
}

/**
 * 检查是否为强制转人工场景
 * @param {Object} intentResult - 意图识别结果
 * @returns {boolean} 是否需要强制转人工
 */
function shouldForceTransfer(intentResult) {
    // 明确的转人工意图
    if (intentResult.type === IntentTypes.TRANSFER_TO_AGENT && intentResult.confidence > 0.7) {
        return true;
    }
    
    // 投诉且情感为愤怒
    if (intentResult.type === IntentTypes.COMPLAINT && 
        intentResult.emotion === EmotionTypes.ANGRY) {
        return true;
    }
    
    // 置信度过低，可能无法理解
    if (intentResult.confidence < 0.2) {
        return false; // 给机器人再次尝试的机会
    }
    
    return false;
}