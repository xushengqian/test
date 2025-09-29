// 机器人外呼坐席管理系统 JavaScript

// 全局变量
let currentAgent = {
    id: 'agent001',
    name: '坐席001',
    number: '1001',
    status: 'available'
};

let activeCalls = [];
let selectedCall = null;
let refreshInterval = null;

// API基础URL
const API_BASE_URL = 'http://localhost:8080/api';

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    initializeApp();
    startAutoRefresh();
});

// 初始化应用
function initializeApp() {
    updateAgentInfo();
    loadActiveCalls();
    loadStatistics();
    
    // 设置事件监听器
    setupEventListeners();
}

// 设置事件监听器
function setupEventListeners() {
    // 模态框事件
    const takeoverModal = document.getElementById('takeoverModal');
    takeoverModal.addEventListener('show.bs.modal', function(event) {
        const button = event.relatedTarget;
        const sessionId = button.getAttribute('data-session-id');
        loadCallDetails(sessionId);
    });
}

// 更新坐席信息显示
function updateAgentInfo() {
    document.getElementById('currentAgent').textContent = currentAgent.name;
    
    const statusElement = document.getElementById('agentStatus');
    const statusMap = {
        'available': { text: '空闲', class: 'bg-success' },
        'busy': { text: '忙碌', class: 'bg-warning' },
        'offline': { text: '离线', class: 'bg-danger' }
    };
    
    const status = statusMap[currentAgent.status] || statusMap['offline'];
    statusElement.textContent = status.text;
    statusElement.className = `badge ${status.class}`;
}

// 加载活跃通话列表
async function loadActiveCalls() {
    try {
        const response = await fetch(`${API_BASE_URL}/active_calls`);
        const data = await response.json();
        
        if (data.success) {
            activeCalls = data.calls;
            renderActiveCalls();
            updateCallsCount();
        } else {
            console.error('Failed to load active calls:', data.error);
            showError('加载通话列表失败');
        }
    } catch (error) {
        console.error('Error loading active calls:', error);
        showError('网络连接错误');
    }
}

// 渲染活跃通话列表
function renderActiveCalls() {
    const container = document.getElementById('activeCallsList');
    const noCallsMessage = document.getElementById('noCallsMessage');
    
    if (activeCalls.length === 0) {
        container.innerHTML = '';
        noCallsMessage.style.display = 'block';
        return;
    }
    
    noCallsMessage.style.display = 'none';
    
    const callsHtml = activeCalls.map(call => {
        const stateInfo = getCallStateInfo(call.call_state);
        const conversationPreview = call.conversation_preview || '暂无对话记录';
        
        return `
            <div class="col-md-6 col-lg-4 mb-3">
                <div class="card call-card h-100">
                    <div class="card-header d-flex justify-content-between align-items-center">
                        <h6 class="mb-0">
                            <i class="bi bi-telephone"></i> ${call.customer_number}
                        </h6>
                        <span class="badge status-badge ${stateInfo.class}">${stateInfo.text}</span>
                    </div>
                    <div class="card-body">
                        <div class="conversation-preview mb-3">
                            <small class="text-muted">对话记录:</small><br>
                            <span>${conversationPreview}</span>
                        </div>
                        <div class="d-flex justify-content-between align-items-center mb-2">
                            <small class="text-muted">通话时长:</small>
                            <span class="call-duration">${call.duration}</span>
                        </div>
                        <div class="d-flex justify-content-between align-items-center mb-3">
                            <small class="text-muted">开始时间:</small>
                            <span>${formatTime(call.start_time)}</span>
                        </div>
                    </div>
                    <div class="card-footer">
                        ${renderCallActions(call)}
                    </div>
                </div>
            </div>
        `;
    }).join('');
    
    container.innerHTML = callsHtml;
}

// 获取通话状态信息
function getCallStateInfo(state) {
    const stateMap = {
        'robot_active': { text: '机器人通话中', class: 'bg-primary' },
        'robot_paused': { text: '机器人暂停', class: 'bg-warning' },
        'transferring': { text: '转接中', class: 'bg-info' },
        'in_conference': { text: '三方通话', class: 'bg-success' },
        'agent_only': { text: '人工通话', class: 'bg-success' }
    };
    
    return stateMap[state] || { text: '未知状态', class: 'bg-secondary' };
}

// 渲染通话操作按钮
function renderCallActions(call) {
    if (call.call_state === 'robot_active' || call.call_state === 'robot_paused') {
        return `
            <button class="btn btn-success btn-sm btn-takeover w-100" 
                    data-bs-toggle="modal" 
                    data-bs-target="#takeoverModal"
                    data-session-id="${call.session_id}">
                <i class="bi bi-headset"></i> 接入通话
            </button>
        `;
    } else if (call.call_state === 'in_conference') {
        return `
            <button class="btn btn-info btn-sm w-100" disabled>
                <i class="bi bi-people"></i> 通话进行中
            </button>
        `;
    } else {
        return `
            <button class="btn btn-secondary btn-sm w-100" disabled>
                <i class="bi bi-clock"></i> ${getCallStateInfo(call.call_state).text}
            </button>
        `;
    }
}

// 加载通话详情
function loadCallDetails(sessionId) {
    const call = activeCalls.find(c => c.session_id === sessionId);
    if (!call) return;
    
    selectedCall = call;
    
    // 更新模态框内容
    document.getElementById('modalCustomerNumber').textContent = call.customer_number;
    document.getElementById('modalCallDuration').textContent = call.duration;
    
    // 渲染对话记录
    const conversationContainer = document.getElementById('modalConversation');
    if (call.conversation_preview && call.conversation_preview !== '暂无对话记录') {
        conversationContainer.innerHTML = `<p class="mb-0">${call.conversation_preview}</p>`;
    } else {
        conversationContainer.innerHTML = '<p class="text-muted mb-0">暂无对话记录</p>';
    }
    
    // 设置默认分机号
    document.getElementById('agentNumber').value = currentAgent.number;
}

// 确认接入通话
async function confirmTakeover() {
    if (!selectedCall) return;
    
    const agentNumber = document.getElementById('agentNumber').value.trim();
    if (!agentNumber) {
        showError('请输入分机号');
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE_URL}/agent_takeover`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                session_id: selectedCall.session_id,
                agent_id: currentAgent.id,
                agent_number: agentNumber
            })
        });
        
        const data = await response.json();
        
        if (data.success) {
            showSuccess('成功接入通话！');
            
            // 关闭模态框
            const modal = bootstrap.Modal.getInstance(document.getElementById('takeoverModal'));
            modal.hide();
            
            // 更新坐席状态
            currentAgent.status = 'busy';
            updateAgentInfo();
            
            // 刷新通话列表
            setTimeout(() => {
                loadActiveCalls();
            }, 1000);
            
        } else {
            showError('接入通话失败: ' + data.error);
        }
    } catch (error) {
        console.error('Error taking over call:', error);
        showError('网络连接错误');
    }
}

// 发起机器人外呼
function startRobotCall() {
    const modal = new bootstrap.Modal(document.getElementById('startCallModal'));
    modal.show();
}

// 确认发起外呼
async function confirmStartCall() {
    const customerNumber = document.getElementById('customerNumber').value.trim();
    const callScript = document.getElementById('callScript').value;
    
    if (!customerNumber) {
        showError('请输入客户电话号码');
        return;
    }
    
    // 简单的电话号码验证
    if (!/^1[3-9]\d{9}$/.test(customerNumber)) {
        showError('请输入有效的手机号码');
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE_URL}/start_robot_call`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                customer_number: customerNumber,
                call_script: callScript
            })
        });
        
        const data = await response.json();
        
        if (data.success) {
            showSuccess('机器人外呼已启动！');
            
            // 关闭模态框
            const modal = bootstrap.Modal.getInstance(document.getElementById('startCallModal'));
            modal.hide();
            
            // 清空表单
            document.getElementById('customerNumber').value = '';
            document.getElementById('callScript').value = 'default';
            
            // 刷新通话列表
            setTimeout(() => {
                loadActiveCalls();
            }, 2000);
            
        } else {
            showError('启动外呼失败: ' + data.error);
        }
    } catch (error) {
        console.error('Error starting robot call:', error);
        showError('网络连接错误');
    }
}

// 加载统计数据
async function loadStatistics() {
    try {
        // 这里可以调用统计API，暂时使用模拟数据
        updateStatistics({
            activeCallsCount: activeCalls.length,
            waitingCallsCount: activeCalls.filter(c => c.call_state === 'transferring').length,
            availableAgentsCount: 5,
            successRate: 85
        });
    } catch (error) {
        console.error('Error loading statistics:', error);
    }
}

// 更新统计数据显示
function updateStatistics(stats) {
    document.getElementById('activeCallsCount').textContent = stats.activeCallsCount;
    document.getElementById('waitingCallsCount').textContent = stats.waitingCallsCount;
    document.getElementById('availableAgentsCount').textContent = stats.availableAgentsCount;
    document.getElementById('successRate').textContent = stats.successRate + '%';
}

// 更新通话数量
function updateCallsCount() {
    const activeCount = activeCalls.length;
    const waitingCount = activeCalls.filter(c => c.call_state === 'transferring').length;
    
    document.getElementById('activeCallsCount').textContent = activeCount;
    document.getElementById('waitingCallsCount').textContent = waitingCount;
}

// 改变坐席状态
function changeStatus(newStatus) {
    currentAgent.status = newStatus;
    updateAgentInfo();
    showSuccess(`状态已更改为: ${getStatusText(newStatus)}`);
}

// 获取状态文本
function getStatusText(status) {
    const statusMap = {
        'available': '空闲',
        'busy': '忙碌',
        'offline': '离线'
    };
    return statusMap[status] || '未知';
}

// 刷新数据
function refreshData() {
    loadActiveCalls();
    loadStatistics();
    showSuccess('数据已刷新');
}

// 查看历史记录
function viewHistory() {
    // 这里可以跳转到历史记录页面或打开模态框
    showInfo('历史记录功能开发中...');
}

// 开始自动刷新
function startAutoRefresh() {
    // 每30秒自动刷新一次
    refreshInterval = setInterval(() => {
        loadActiveCalls();
        loadStatistics();
    }, 30000);
}

// 停止自动刷新
function stopAutoRefresh() {
    if (refreshInterval) {
        clearInterval(refreshInterval);
        refreshInterval = null;
    }
}

// 格式化时间
function formatTime(timeString) {
    if (!timeString) return '未知';
    
    try {
        const date = new Date(timeString);
        return date.toLocaleTimeString('zh-CN', {
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
    } catch (error) {
        return '未知';
    }
}

// 显示成功消息
function showSuccess(message) {
    showToast(message, 'success');
}

// 显示错误消息
function showError(message) {
    showToast(message, 'danger');
}

// 显示信息消息
function showInfo(message) {
    showToast(message, 'info');
}

// 显示Toast消息
function showToast(message, type = 'info') {
    // 创建toast容器（如果不存在）
    let toastContainer = document.getElementById('toastContainer');
    if (!toastContainer) {
        toastContainer = document.createElement('div');
        toastContainer.id = 'toastContainer';
        toastContainer.className = 'toast-container position-fixed top-0 end-0 p-3';
        toastContainer.style.zIndex = '9999';
        document.body.appendChild(toastContainer);
    }
    
    // 创建toast元素
    const toastId = 'toast_' + Date.now();
    const toastHtml = `
        <div id="${toastId}" class="toast align-items-center text-white bg-${type} border-0" role="alert">
            <div class="d-flex">
                <div class="toast-body">
                    ${message}
                </div>
                <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
            </div>
        </div>
    `;
    
    toastContainer.insertAdjacentHTML('beforeend', toastHtml);
    
    // 显示toast
    const toastElement = document.getElementById(toastId);
    const toast = new bootstrap.Toast(toastElement, {
        autohide: true,
        delay: 3000
    });
    
    toast.show();
    
    // 自动清理
    toastElement.addEventListener('hidden.bs.toast', function() {
        toastElement.remove();
    });
}

// 页面卸载时清理
window.addEventListener('beforeunload', function() {
    stopAutoRefresh();
});