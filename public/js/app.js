// 全局变量
let socket = null;
let authToken = localStorage.getItem('authToken');
let currentUser = null;
let refreshInterval = null;
let callTrendChart = null;

// API基础URL
const API_URL = '/api';

// 初始化应用
document.addEventListener('DOMContentLoaded', function() {
    // 检查登录状态
    if (!authToken) {
        window.location.href = '/login.html';
        return;
    }

    // 初始化WebSocket连接
    initWebSocket();
    
    // 初始化图表
    initCharts();
    
    // 加载初始数据
    loadDashboardData();
    
    // 设置定时刷新
    refreshInterval = setInterval(refreshData, 5000);
    
    // 绑定导航事件
    bindNavigationEvents();
});

// 初始化WebSocket连接
function initWebSocket() {
    socket = io('/', {
        auth: {
            token: authToken
        }
    });

    socket.on('connect', () => {
        console.log('WebSocket connected');
        showNotification('系统已连接', 'success');
    });

    socket.on('disconnect', () => {
        console.log('WebSocket disconnected');
        showNotification('系统连接断开', 'error');
    });

    // 监听实时事件
    socket.on('stats.update', (data) => {
        updateRealtimeStats(data);
    });

    socket.on('call.new', (data) => {
        addRecentCall(data);
        updateCallsTable();
    });

    socket.on('agent.statusChanged', (data) => {
        updateAgentStatus(data);
    });

    socket.on('campaign.update', (data) => {
        updateCampaignStatus(data);
    });
}

// 初始化图表
function initCharts() {
    const ctx = document.getElementById('call-trend-chart');
    if (!ctx) return;

    callTrendChart = new Chart(ctx.getContext('2d'), {
        type: 'line',
        data: {
            labels: [],
            datasets: [{
                label: '呼出量',
                data: [],
                borderColor: 'rgb(75, 192, 192)',
                backgroundColor: 'rgba(75, 192, 192, 0.2)',
                tension: 0.4
            }, {
                label: '接通量',
                data: [],
                borderColor: 'rgb(54, 162, 235)',
                backgroundColor: 'rgba(54, 162, 235, 0.2)',
                tension: 0.4
            }, {
                label: '转人工',
                data: [],
                borderColor: 'rgb(255, 99, 132)',
                backgroundColor: 'rgba(255, 99, 132, 0.2)',
                tension: 0.4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    position: 'top',
                },
                title: {
                    display: false
                }
            },
            scales: {
                y: {
                    beginAtZero: true
                }
            }
        }
    });
}

// 加载仪表盘数据
async function loadDashboardData() {
    try {
        // 获取实时统计
        const stats = await apiRequest('/stats/realtime');
        updateRealtimeStats(stats);

        // 获取最近呼叫
        const calls = await apiRequest('/calls/active');
        updateRecentCalls(calls);

        // 获取外呼任务
        const campaigns = await apiRequest('/campaigns');
        updateCampaignsTable(campaigns);

        // 获取坐席状态
        const agents = await apiRequest('/agents/status');
        updateAgentsGrid(agents);

    } catch (error) {
        console.error('Failed to load dashboard data:', error);
        showNotification('加载数据失败', 'error');
    }
}

// 更新实时统计
function updateRealtimeStats(stats) {
    document.getElementById('active-calls').textContent = stats.activeCalls || 0;
    document.getElementById('robot-calls').textContent = stats.robotCalls || 0;
    document.getElementById('agent-calls').textContent = stats.agentCalls || 0;
    document.getElementById('online-agents').textContent = stats.totalAgents || 0;
    document.getElementById('available-agents').textContent = stats.availableAgents || 0;
    document.getElementById('busy-agents').textContent = stats.busyAgents || 0;
    document.getElementById('queued-calls').textContent = stats.queuedCalls || 0;
    document.getElementById('pending-calls').textContent = stats.pendingCalls || 0;
    
    const activeCampaigns = stats.campaigns ? stats.campaigns.filter(c => c.status === 'running').length : 0;
    document.getElementById('active-campaigns').textContent = activeCampaigns;
}

// 更新最近呼叫列表
function updateRecentCalls(calls) {
    const container = document.getElementById('recent-calls-list');
    if (!container) return;

    container.innerHTML = '';
    
    calls.slice(0, 10).forEach(call => {
        const item = document.createElement('div');
        item.className = `call-item ${call.status}`;
        item.innerHTML = `
            <div class="d-flex justify-content-between">
                <div>
                    <strong>${call.phoneNumber}</strong>
                    <br>
                    <small class="text-muted">${formatTime(call.startTime)}</small>
                </div>
                <div class="text-end">
                    <span class="badge bg-${getStatusColor(call.status)}">${getStatusText(call.status)}</span>
                    <br>
                    <small>${call.duration ? call.duration + 's' : '-'}</small>
                </div>
            </div>
        `;
        container.appendChild(item);
    });
}

// 更新外呼任务表格
function updateCampaignsTable(campaigns) {
    const tbody = document.getElementById('campaigns-table');
    if (!tbody) return;

    tbody.innerHTML = '';
    
    campaigns.forEach(campaign => {
        const tr = document.createElement('tr');
        const answerRate = campaign.stats.total > 0 
            ? Math.round((campaign.stats.answered / campaign.stats.total) * 100) 
            : 0;
        
        tr.innerHTML = `
            <td>${campaign.name}</td>
            <td><span class="badge bg-${getCampaignStatusColor(campaign.status)}">${getCampaignStatusText(campaign.status)}</span></td>
            <td>${campaign.stats.total}</td>
            <td>${campaign.stats.completed}</td>
            <td>${answerRate}%</td>
            <td>${formatTime(campaign.createdAt)}</td>
            <td>
                <div class="btn-group btn-group-sm">
                    ${getCampaignActions(campaign)}
                </div>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

// 更新坐席网格
function updateAgentsGrid(agents) {
    const container = document.getElementById('agents-grid');
    if (!container) return;

    container.innerHTML = '';
    
    agents.forEach(agent => {
        const col = document.createElement('div');
        col.className = 'col-md-4 col-lg-3';
        col.innerHTML = `
            <div class="agent-card ${agent.status}">
                <div class="d-flex align-items-center">
                    <div class="agent-avatar">
                        ${agent.name.charAt(0).toUpperCase()}
                    </div>
                    <div class="ms-3">
                        <h6 class="mb-1">${agent.name}</h6>
                        <p class="mb-0">
                            <small>分机: ${agent.extension || '-'}</small><br>
                            <span class="badge bg-${getAgentStatusColor(agent.status)}">${getAgentStatusText(agent.status)}</span>
                        </p>
                        ${agent.currentCall ? `<small class="text-muted">通话中...</small>` : ''}
                    </div>
                </div>
                <div class="mt-3">
                    <small class="text-muted">
                        今日: ${agent.statistics?.totalCalls || 0} 通 | 
                        平均: ${agent.statistics?.avgTalkTime || 0}s
                    </small>
                </div>
            </div>
        `;
        container.appendChild(col);
    });
}

// 更新通话表格
function updateCallsTable() {
    apiRequest('/calls/active').then(calls => {
        const tbody = document.getElementById('calls-table');
        if (!tbody) return;

        tbody.innerHTML = '';
        
        calls.forEach(call => {
            const tr = document.createElement('tr');
            const duration = call.answerTime 
                ? Math.floor((Date.now() - new Date(call.answerTime).getTime()) / 1000) 
                : 0;
            
            tr.innerHTML = `
                <td><small>${call.uuid.substr(0, 8)}...</small></td>
                <td>${call.phoneNumber}</td>
                <td><span class="badge bg-${getStatusColor(call.status)}">${getStatusText(call.status)}</span></td>
                <td>${call.isRobot ? '机器人' : '人工'}</td>
                <td>${call.agentId || '-'}</td>
                <td>${formatDuration(duration)}</td>
                <td>
                    <div class="btn-group btn-group-sm">
                        <button class="btn btn-outline-primary" onclick="monitorCall('${call.uuid}')" title="监听">
                            <i class="fas fa-headphones"></i>
                        </button>
                        <button class="btn btn-outline-warning" onclick="transferCall('${call.uuid}')" title="转接">
                            <i class="fas fa-exchange-alt"></i>
                        </button>
                        <button class="btn btn-outline-danger" onclick="hangupCall('${call.uuid}')" title="挂断">
                            <i class="fas fa-phone-slash"></i>
                        </button>
                    </div>
                </td>
            `;
            tbody.appendChild(tr);
        });
    });
}

// 创建外呼任务
async function createCampaign() {
    const form = document.getElementById('campaign-form');
    if (!form.checkValidity()) {
        form.reportValidity();
        return;
    }

    const phoneFile = document.getElementById('phone-file').files[0];
    const phoneList = document.getElementById('phone-list').value;
    
    let phones = [];
    if (phoneList) {
        phones = phoneList.split('\n').filter(p => p.trim());
    }

    const campaignData = {
        name: document.getElementById('campaign-name').value,
        description: document.getElementById('campaign-description').value,
        startTime: document.getElementById('campaign-start-time').value,
        endTime: document.getElementById('campaign-end-time').value,
        script: document.getElementById('campaign-script').value,
        phoneList: phones,
        maxRetries: parseInt(document.getElementById('max-retries').value),
        retryInterval: parseInt(document.getElementById('retry-interval').value) * 60000,
        priority: parseInt(document.getElementById('campaign-priority').value)
    };

    try {
        const result = await apiRequest('/campaigns', 'POST', campaignData);
        
        if (phoneFile) {
            // 上传号码文件
            const formData = new FormData();
            formData.append('file', phoneFile);
            await apiRequest(`/campaigns/${result.campaign.id}/import`, 'POST', formData, true);
        }
        
        showNotification('任务创建成功', 'success');
        bootstrap.Modal.getInstance(document.getElementById('createCampaignModal')).hide();
        loadDashboardData();
    } catch (error) {
        console.error('Failed to create campaign:', error);
        showNotification('创建任务失败: ' + error.message, 'error');
    }
}

// 坐席登录
async function agentLogin() {
    const extension = document.getElementById('agent-extension').value;
    const password = document.getElementById('agent-password').value;

    if (!extension || !password) {
        showNotification('请填写完整信息', 'warning');
        return;
    }

    try {
        const result = await apiRequest('/agents/login', 'POST', {
            extension,
            password
        });
        
        showNotification('签入成功', 'success');
        bootstrap.Modal.getInstance(document.getElementById('agentLoginModal')).hide();
        loadDashboardData();
    } catch (error) {
        console.error('Agent login failed:', error);
        showNotification('签入失败: ' + error.message, 'error');
    }
}

// API请求封装
async function apiRequest(endpoint, method = 'GET', data = null, isFormData = false) {
    const options = {
        method,
        headers: {
            'Authorization': `Bearer ${authToken}`
        }
    };

    if (data && !isFormData) {
        options.headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(data);
    } else if (isFormData) {
        options.body = data;
    }

    const response = await fetch(API_URL + endpoint, options);
    
    if (!response.ok) {
        const error = await response.json();
        throw new Error(error.error || 'Request failed');
    }

    const result = await response.json();
    return result.success ? result : Promise.reject(result);
}

// 显示通知
function showNotification(message, type = 'info') {
    const toastHtml = `
        <div class="toast ${type} show" role="alert">
            <div class="toast-header">
                <strong class="me-auto">${getNotificationTitle(type)}</strong>
                <button type="button" class="btn-close" data-bs-dismiss="toast"></button>
            </div>
            <div class="toast-body">
                ${message}
            </div>
        </div>
    `;

    let container = document.querySelector('.toast-container');
    if (!container) {
        container = document.createElement('div');
        container.className = 'toast-container';
        document.body.appendChild(container);
    }

    const toastElement = document.createElement('div');
    toastElement.innerHTML = toastHtml;
    container.appendChild(toastElement.firstElementChild);

    setTimeout(() => {
        toastElement.remove();
    }, 5000);
}

// 辅助函数
function getStatusColor(status) {
    const colors = {
        'initiating': 'secondary',
        'ringing': 'info',
        'answered': 'success',
        'completed': 'primary',
        'failed': 'danger'
    };
    return colors[status] || 'secondary';
}

function getStatusText(status) {
    const texts = {
        'initiating': '发起中',
        'ringing': '振铃中',
        'answered': '已接通',
        'completed': '已完成',
        'failed': '失败'
    };
    return texts[status] || status;
}

function getCampaignStatusColor(status) {
    const colors = {
        'pending': 'secondary',
        'running': 'success',
        'paused': 'warning',
        'completed': 'primary',
        'cancelled': 'danger'
    };
    return colors[status] || 'secondary';
}

function getCampaignStatusText(status) {
    const texts = {
        'pending': '待执行',
        'running': '执行中',
        'paused': '已暂停',
        'completed': '已完成',
        'cancelled': '已取消'
    };
    return texts[status] || status;
}

function getAgentStatusColor(status) {
    const colors = {
        'offline': 'secondary',
        'available': 'success',
        'busy': 'danger',
        'break': 'warning',
        'after_call_work': 'info'
    };
    return colors[status] || 'secondary';
}

function getAgentStatusText(status) {
    const texts = {
        'offline': '离线',
        'available': '空闲',
        'busy': '忙碌',
        'break': '小休',
        'after_call_work': '话后'
    };
    return texts[status] || status;
}

function getCampaignActions(campaign) {
    const actions = [];
    
    if (campaign.status === 'pending' || campaign.status === 'paused') {
        actions.push(`<button class="btn btn-success" onclick="startCampaign('${campaign.id}')">开始</button>`);
    }
    
    if (campaign.status === 'running') {
        actions.push(`<button class="btn btn-warning" onclick="pauseCampaign('${campaign.id}')">暂停</button>`);
    }
    
    if (campaign.status !== 'completed' && campaign.status !== 'cancelled') {
        actions.push(`<button class="btn btn-danger" onclick="cancelCampaign('${campaign.id}')">取消</button>`);
    }
    
    actions.push(`<button class="btn btn-info" onclick="viewCampaignDetails('${campaign.id}')">详情</button>`);
    
    return actions.join('');
}

function getNotificationTitle(type) {
    const titles = {
        'success': '成功',
        'error': '错误',
        'warning': '警告',
        'info': '提示'
    };
    return titles[type] || '通知';
}

function formatTime(dateString) {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return date.toLocaleString('zh-CN');
}

function formatDuration(seconds) {
    if (!seconds) return '0s';
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;
    
    if (hours > 0) {
        return `${hours}h ${minutes}m ${secs}s`;
    } else if (minutes > 0) {
        return `${minutes}m ${secs}s`;
    } else {
        return `${secs}s`;
    }
}

// 导航事件绑定
function bindNavigationEvents() {
    document.querySelectorAll('.nav-link').forEach(link => {
        link.addEventListener('click', function(e) {
            e.preventDefault();
            
            // 更新活动标签
            document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
            this.classList.add('active');
            
            // 切换内容
            const target = this.getAttribute('href').substring(1);
            document.querySelectorAll('.tab-pane').forEach(pane => {
                pane.classList.remove('show', 'active');
            });
            
            const targetPane = document.getElementById(target);
            if (targetPane) {
                targetPane.classList.add('show', 'active');
                
                // 根据不同页面加载数据
                switch(target) {
                    case 'campaigns':
                        loadCampaigns();
                        break;
                    case 'agents':
                        loadAgents();
                        break;
                    case 'calls':
                        updateCallsTable();
                        break;
                    case 'reports':
                        loadReports();
                        break;
                }
            }
        });
    });
}

// 任务操作函数
async function startCampaign(id) {
    try {
        await apiRequest(`/campaigns/${id}/start`, 'POST');
        showNotification('任务已开始', 'success');
        loadCampaigns();
    } catch (error) {
        showNotification('操作失败: ' + error.message, 'error');
    }
}

async function pauseCampaign(id) {
    try {
        await apiRequest(`/campaigns/${id}/pause`, 'POST');
        showNotification('任务已暂停', 'success');
        loadCampaigns();
    } catch (error) {
        showNotification('操作失败: ' + error.message, 'error');
    }
}

async function cancelCampaign(id) {
    if (!confirm('确定要取消此任务吗？')) return;
    
    try {
        await apiRequest(`/campaigns/${id}/cancel`, 'POST');
        showNotification('任务已取消', 'success');
        loadCampaigns();
    } catch (error) {
        showNotification('操作失败: ' + error.message, 'error');
    }
}

// 呼叫操作函数
async function monitorCall(uuid) {
    try {
        await apiRequest(`/monitor/listen/${uuid}`, 'POST');
        showNotification('开始监听', 'success');
    } catch (error) {
        showNotification('监听失败: ' + error.message, 'error');
    }
}

async function transferCall(uuid) {
    try {
        await apiRequest(`/calls/${uuid}/transfer`, 'POST');
        showNotification('正在转接...', 'info');
    } catch (error) {
        showNotification('转接失败: ' + error.message, 'error');
    }
}

async function hangupCall(uuid) {
    if (!confirm('确定要挂断此通话吗？')) return;
    
    try {
        await apiRequest(`/calls/${uuid}/hangup`, 'POST');
        showNotification('通话已挂断', 'success');
        updateCallsTable();
    } catch (error) {
        showNotification('挂断失败: ' + error.message, 'error');
    }
}

// 页面数据加载函数
async function loadCampaigns() {
    const campaigns = await apiRequest('/campaigns');
    updateCampaignsTable(campaigns.campaigns);
}

async function loadAgents() {
    const agents = await apiRequest('/agents/status');
    updateAgentsGrid(agents.agents);
}

async function loadReports() {
    // 实现报表加载逻辑
}

// 定时刷新数据
function refreshData() {
    if (document.querySelector('#dashboard.active')) {
        loadDashboardData();
    } else if (document.querySelector('#calls.active')) {
        updateCallsTable();
    }
}

// 退出登录
function logout() {
    localStorage.removeItem('authToken');
    if (socket) {
        socket.disconnect();
    }
    window.location.href = '/login.html';
}

// 显示创建任务模态框
function showCreateCampaignModal() {
    const modal = new bootstrap.Modal(document.getElementById('createCampaignModal'));
    modal.show();
}

// 显示手动输入号码
function showPhoneInput() {
    document.getElementById('phone-list').classList.toggle('d-none');
}