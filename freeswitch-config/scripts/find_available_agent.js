/**
 * 查找可用人工客服脚本
 * 负责查找在线的人工客服并进行转接
 */

// 客服状态定义
var AgentStatus = {
    ONLINE: "online",
    BUSY: "busy", 
    OFFLINE: "offline",
    BREAK: "break"
};

// 客服队列配置
var queueConfig = {
    name: "agent_queue",
    maxWaitTime: 300, // 最大等待时间（秒）
    ringTimeout: 30,  // 振铃超时（秒）
    priority: {
        VIP: 1,
        NORMAL: 5,
        LOW: 10
    }
};

/**
 * 查找可用客服主函数
 */
function findAvailableAgent() {
    console_log("INFO", "开始查找可用客服...");
    
    var customerPhone = session.getVariable("customer_phone");
    var robotSessionId = session.getVariable("robot_session_id");
    
    // 获取客户优先级
    var customerPriority = getCustomerPriority(customerPhone);
    
    // 查找在线客服
    var availableAgents = getOnlineAgents();
    
    if (availableAgents.length === 0) {
        console_log("WARN", "没有可用的在线客服");
        handleNoAgentAvailable();
        return false;
    }
    
    // 选择最佳客服
    var selectedAgent = selectBestAgent(availableAgents, customerPriority);
    
    if (selectedAgent) {
        console_log("INFO", "选择客服: " + selectedAgent.id + " (" + selectedAgent.name + ")");
        
        // 设置转接信息
        setTransferVariables(selectedAgent, customerPriority);
        
        // 记录转接日志
        logTransferAttempt(customerPhone, selectedAgent, robotSessionId);
        
        return true;
    } else {
        console_log("WARN", "未能选择合适的客服");
        handleNoAgentAvailable();
        return false;
    }
}

/**
 * 获取在线客服列表
 * @returns {Array} 在线客服列表
 */
function getOnlineAgents() {
    var agents = [];
    
    try {
        // 从 FreeSwitch 获取注册的客服分机
        var registeredExtensions = getRegisteredExtensions();
        
        // 预定义的客服信息
        var agentInfo = {
            "1001": { id: "1001", name: "客服小王", skills: ["general", "technical"], maxCalls: 3 },
            "1002": { id: "1002", name: "客服小李", skills: ["general", "complaint"], maxCalls: 2 },
            "1003": { id: "1003", name: "客服小张", skills: ["technical", "vip"], maxCalls: 4 },
            "1004": { id: "1004", name: "客服小陈", skills: ["general"], maxCalls: 2 },
            "1005": { id: "1005", name: "客服小刘", skills: ["vip", "complaint"], maxCalls: 3 }
        };
        
        // 检查每个客服的状态
        for (var ext in agentInfo) {
            if (registeredExtensions.indexOf(ext) !== -1) {
                var agent = agentInfo[ext];
                var status = getAgentStatus(ext);
                var currentCalls = getCurrentCallCount(ext);
                
                if (status === AgentStatus.ONLINE && currentCalls < agent.maxCalls) {
                    agent.extension = ext;
                    agent.currentCalls = currentCalls;
                    agent.status = status;
                    agents.push(agent);
                }
            }
        }
        
        console_log("INFO", "找到 " + agents.length + " 个可用客服");
        
    } catch (e) {
        console_log("ERROR", "获取在线客服失败: " + e.toString());
    }
    
    return agents;
}

/**
 * 获取已注册的分机号
 * @returns {Array} 分机号列表
 */
function getRegisteredExtensions() {
    try {
        // 使用 FreeSwitch API 获取注册用户
        var result = apiExecute("show", "registrations");
        var extensions = [];
        
        if (result) {
            var lines = result.split('\n');
            for (var i = 0; i < lines.length; i++) {
                var line = lines[i];
                // 解析注册信息，提取分机号
                var match = line.match(/(\d{4})@/);
                if (match) {
                    extensions.push(match[1]);
                }
            }
        }
        
        return extensions;
        
    } catch (e) {
        console_log("ERROR", "获取注册分机失败: " + e.toString());
        return ["1001", "1002", "1003"]; // 默认分机
    }
}

/**
 * 获取客服状态
 * @param {string} extension - 分机号
 * @returns {string} 客服状态
 */
function getAgentStatus(extension) {
    try {
        // 检查分机是否在通话中
        var result = apiExecute("show", "channels like " + extension);
        
        if (result && result.indexOf(extension) !== -1) {
            return AgentStatus.BUSY;
        }
        
        // 检查客服是否设置了忙碌状态
        var presenceResult = apiExecute("presence", "get " + extension + "@$${domain}");
        
        if (presenceResult && presenceResult.indexOf("busy") !== -1) {
            return AgentStatus.BUSY;
        }
        
        if (presenceResult && presenceResult.indexOf("break") !== -1) {
            return AgentStatus.BREAK;
        }
        
        return AgentStatus.ONLINE;
        
    } catch (e) {
        console_log("ERROR", "获取客服状态失败: " + e.toString());
        return AgentStatus.OFFLINE;
    }
}

/**
 * 获取客服当前通话数
 * @param {string} extension - 分机号
 * @returns {number} 当前通话数
 */
function getCurrentCallCount(extension) {
    try {
        var result = apiExecute("show", "channels like " + extension);
        
        if (!result) return 0;
        
        var lines = result.split('\n');
        var count = 0;
        
        for (var i = 0; i < lines.length; i++) {
            if (lines[i].indexOf(extension) !== -1 && lines[i].indexOf("CS_EXECUTE") !== -1) {
                count++;
            }
        }
        
        return count;
        
    } catch (e) {
        console_log("ERROR", "获取通话数失败: " + e.toString());
        return 0;
    }
}

/**
 * 获取客户优先级
 * @param {string} phone - 客户电话
 * @returns {string} 优先级
 */
function getCustomerPriority(phone) {
    // 这里可以连接数据库或调用API获取客户信息
    // 简化实现：根据号码规则判断
    
    if (phone && phone.length > 0) {
        // VIP 客户（示例：以138开头的号码）
        if (phone.indexOf("138") === 0) {
            return "VIP";
        }
        
        // 普通客户
        return "NORMAL";
    }
    
    return "LOW";
}

/**
 * 选择最佳客服
 * @param {Array} agents - 可用客服列表
 * @param {string} priority - 客户优先级
 * @returns {Object} 选择的客服
 */
function selectBestAgent(agents, priority) {
    if (agents.length === 0) return null;
    
    // 根据优先级和技能匹配
    var scoredAgents = [];
    
    for (var i = 0; i < agents.length; i++) {
        var agent = agents[i];
        var score = calculateAgentScore(agent, priority);
        
        scoredAgents.push({
            agent: agent,
            score: score
        });
    }
    
    // 按分数排序
    scoredAgents.sort(function(a, b) {
        return b.score - a.score;
    });
    
    return scoredAgents[0].agent;
}

/**
 * 计算客服匹配分数
 * @param {Object} agent - 客服信息
 * @param {string} priority - 客户优先级
 * @returns {number} 匹配分数
 */
function calculateAgentScore(agent, priority) {
    var score = 0;
    
    // 基础分数
    score += 10;
    
    // 技能匹配
    if (priority === "VIP" && agent.skills.indexOf("vip") !== -1) {
        score += 20;
    }
    
    if (agent.skills.indexOf("general") !== -1) {
        score += 5;
    }
    
    // 负载均衡（当前通话数越少分数越高）
    score += (agent.maxCalls - agent.currentCalls) * 3;
    
    // 随机因子（避免总是选择同一个客服）
    score += Math.random() * 5;
    
    return score;
}

/**
 * 设置转接变量
 * @param {Object} agent - 选择的客服
 * @param {string} priority - 客户优先级
 */
function setTransferVariables(agent, priority) {
    session.setVariable("selected_agent_id", agent.id);
    session.setVariable("selected_agent_name", agent.name);
    session.setVariable("selected_agent_extension", agent.extension);
    session.setVariable("customer_priority", priority);
    session.setVariable("transfer_timestamp", new Date().toISOString());
    
    // 设置队列优先级
    var queuePriority = queueConfig.priority[priority] || queueConfig.priority.NORMAL;
    session.setVariable("fifo_priority", queuePriority.toString());
}

/**
 * 处理无可用客服情况
 */
function handleNoAgentAvailable() {
    console_log("WARN", "无可用客服，执行备用方案");
    
    // 设置变量供后续处理
    session.setVariable("no_agent_available", "true");
    session.setVariable("queue_callback_requested", "false");
    
    // 可以在这里实现：
    // 1. 提供回呼服务
    // 2. 转到语音信箱
    // 3. 安排稍后重试
}

/**
 * 记录转接尝试日志
 * @param {string} customerPhone - 客户电话
 * @param {Object} agent - 客服信息
 * @param {string} sessionId - 会话ID
 */
function logTransferAttempt(customerPhone, agent, sessionId) {
    var logEntry = {
        timestamp: new Date().toISOString(),
        sessionId: sessionId,
        customerPhone: customerPhone,
        agentId: agent.id,
        agentName: agent.name,
        agentExtension: agent.extension,
        action: "transfer_initiated"
    };
    
    console_log("INFO", "转接日志: " + JSON.stringify(logEntry));
    
    // 这里可以写入数据库或日志文件
    // writeToDatabase(logEntry);
}

/**
 * API 执行辅助函数
 * @param {string} command - API 命令
 * @param {string} args - 参数
 * @returns {string} 执行结果
 */
function apiExecute(command, args) {
    try {
        var api = new API();
        var result = api.execute(command, args);
        return result;
    } catch (e) {
        console_log("ERROR", "API执行失败: " + command + " " + args + " - " + e.toString());
        return null;
    }
}

// 主函数执行
try {
    findAvailableAgent();
} catch (e) {
    console_log("ERROR", "查找客服异常: " + e.toString());
    session.setVariable("no_agent_available", "true");
}