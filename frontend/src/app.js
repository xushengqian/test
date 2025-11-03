/**
 * Freeswitch????????
 */

class TranscriptionApp {
    constructor() {
        this.ws = null;
        this.currentSessionId = null;
        this.sessions = new Map();
        this.messageCount = 0;
        
        this.init();
    }
    
    init() {
        this.connectWebSocket();
        this.setupEventListeners();
    }
    
    connectWebSocket() {
        const wsUrl = 'ws://localhost:8766'; // Web???WebSocket??
        
        console.log('????WebSocket???...');
        this.updateConnectionStatus(false);
        
        this.ws = new WebSocket(wsUrl);
        
        this.ws.onopen = () => {
            console.log('WebSocket????');
            this.updateConnectionStatus(true);
        };
        
        this.ws.onmessage = (event) => {
            const data = JSON.parse(event.data);
            this.handleMessage(data);
        };
        
        this.ws.onerror = (error) => {
            console.error('WebSocket??:', error);
            this.updateConnectionStatus(false);
        };
        
        this.ws.onclose = () => {
            console.log('WebSocket?????3????...');
            this.updateConnectionStatus(false);
            setTimeout(() => this.connectWebSocket(), 3000);
        };
    }
    
    handleMessage(data) {
        console.log('????:', data);
        
        switch (data.type) {
            case 'active_sessions':
                this.handleActiveSessions(data.sessions);
                break;
                
            case 'session_start':
                this.handleSessionStart(data);
                break;
                
            case 'session_end':
                this.handleSessionEnd(data.session_id);
                break;
                
            case 'transcription':
                this.handleTranscription(data);
                break;
                
            case 'session_history':
                this.handleSessionHistory(data);
                break;
        }
    }
    
    handleActiveSessions(sessionIds) {
        console.log('????:', sessionIds);
        
        sessionIds.forEach(sessionId => {
            if (!this.sessions.has(sessionId)) {
                this.sessions.set(sessionId, {
                    session_id: sessionId,
                    call_info: {},
                    messages: [],
                    start_time: new Date().toISOString()
                });
            }
        });
        
        this.renderSessionsList();
    }
    
    handleSessionStart(data) {
        console.log('?????:', data.session_id);
        
        this.sessions.set(data.session_id, {
            session_id: data.session_id,
            call_info: data.call_info || {},
            messages: [],
            start_time: new Date().toISOString()
        });
        
        this.renderSessionsList();
        
        // ???????
        if (!this.currentSessionId) {
            this.selectSession(data.session_id);
        }
    }
    
    handleSessionEnd(sessionId) {
        console.log('????:', sessionId);
        
        if (this.sessions.has(sessionId)) {
            const session = this.sessions.get(sessionId);
            session.ended = true;
            this.renderSessionsList();
            
            // ????????????????????
        }
    }
    
    handleTranscription(data) {
        console.log('???:', data);
        
        const sessionId = data.session_id;
        
        if (!this.sessions.has(sessionId)) {
            // ????????????
            this.sessions.set(sessionId, {
                session_id: sessionId,
                call_info: {},
                messages: [],
                start_time: new Date().toISOString()
            });
            this.renderSessionsList();
        }
        
        const session = this.sessions.get(sessionId);
        session.messages.push({
            channel: data.channel,
            text: data.text,
            timestamp: data.timestamp
        });
        
        this.messageCount++;
        this.updateMessageCount();
        
        // ????????????
        if (this.currentSessionId === sessionId) {
            this.renderMessages();
        }
    }
    
    handleSessionHistory(data) {
        const sessionId = data.session_id;
        
        if (this.sessions.has(sessionId)) {
            const session = this.sessions.get(sessionId);
            session.messages = data.history.map(item => ({
                channel: item.channel,
                text: item.text,
                timestamp: item.timestamp
            }));
            
            if (this.currentSessionId === sessionId) {
                this.renderMessages();
            }
        }
    }
    
    selectSession(sessionId) {
        this.currentSessionId = sessionId;
        this.renderSessionsList();
        this.renderChatPanel();
        
        // ??????
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify({
                type: 'get_session_history',
                session_id: sessionId
            }));
        }
    }
    
    renderSessionsList() {
        const listEl = document.getElementById('sessions-list');
        
        if (this.sessions.size === 0) {
            listEl.innerHTML = '<div class="loading">????...</div>';
            document.getElementById('active-sessions-count').textContent = '0';
            return;
        }
        
        const activeSessions = Array.from(this.sessions.values()).filter(s => !s.ended);
        document.getElementById('active-sessions-count').textContent = activeSessions.length;
        
        listEl.innerHTML = Array.from(this.sessions.values())
            .sort((a, b) => new Date(b.start_time) - new Date(a.start_time))
            .map(session => `
                <div class="session-item ${session.session_id === this.currentSessionId ? 'active' : ''}"
                     onclick="app.selectSession('${session.session_id}')">
                    <div class="session-id">
                        ${session.ended ? '??' : '??'} ${this.formatSessionId(session.session_id)}
                    </div>
                    <div class="session-info">
                        ${session.call_info.caller_number || '????'} ? ${session.call_info.callee_number || '????'}
                    </div>
                    <div class="session-info">
                        ${this.formatTime(session.start_time)} ? ${session.messages.length} ???
                    </div>
                </div>
            `).join('');
    }
    
    renderChatPanel() {
        const chatEl = document.getElementById('chat-content');
        
        if (!this.currentSessionId || !this.sessions.has(this.currentSessionId)) {
            chatEl.innerHTML = `
                <div class="empty-state">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
                    </svg>
                    <p>?????????????</p>
                </div>
            `;
            return;
        }
        
        const session = this.sessions.get(this.currentSessionId);
        
        chatEl.innerHTML = `
            <div class="chat-header">
                <h2>???? - ${this.formatSessionId(session.session_id)}</h2>
                <div class="call-info">
                    <div class="call-info-item">
                        <span>??</span>
                        <span>??: ${session.call_info.caller_number || '??'}</span>
                    </div>
                    <div class="call-info-item">
                        <span>??</span>
                        <span>??: ${session.call_info.callee_number || '??'}</span>
                    </div>
                    <div class="call-info-item">
                        <span>?</span>
                        <span>????: ${this.formatTime(session.start_time)}</span>
                    </div>
                    ${session.ended ? '<div class="call-info-item"><span>?</span><span>???</span></div>' : ''}
                </div>
            </div>
            <div class="messages-container" id="messages-container">
                ${this.renderMessagesHTML(session.messages)}
            </div>
        `;
        
        this.scrollToBottom();
    }
    
    renderMessages() {
        if (!this.currentSessionId || !this.sessions.has(this.currentSessionId)) {
            return;
        }
        
        const session = this.sessions.get(this.currentSessionId);
        const container = document.getElementById('messages-container');
        
        if (container) {
            container.innerHTML = this.renderMessagesHTML(session.messages);
            this.scrollToBottom();
        }
    }
    
    renderMessagesHTML(messages) {
        if (messages.length === 0) {
            return `
                <div class="empty-state">
                    <p>????</p>
                </div>
            `;
        }
        
        return messages.map(msg => `
            <div class="message ${msg.channel}">
                <div class="message-avatar">
                    ${msg.channel === 'customer' ? '?' : '?'}
                </div>
                <div class="message-content">
                    <div class="message-header">
                        <span class="message-role">
                            ${msg.channel === 'customer' ? '??' : '??'}
                        </span>
                        <span class="message-time">${this.formatTime(msg.timestamp)}</span>
                    </div>
                    <div class="message-text">${this.escapeHtml(msg.text)}</div>
                </div>
            </div>
        `).join('');
    }
    
    updateConnectionStatus(connected) {
        const statusDot = document.getElementById('ws-status');
        const statusText = document.getElementById('ws-status-text');
        
        if (connected) {
            statusDot.className = 'status-dot online';
            statusText.textContent = '???';
        } else {
            statusDot.className = 'status-dot offline';
            statusText.textContent = '???';
        }
    }
    
    updateMessageCount() {
        document.getElementById('message-count').textContent = this.messageCount;
    }
    
    scrollToBottom() {
        setTimeout(() => {
            const container = document.getElementById('messages-container');
            if (container) {
                container.scrollTop = container.scrollHeight;
            }
        }, 100);
    }
    
    setupEventListeners() {
        // ???????????
    }
    
    formatSessionId(sessionId) {
        // ??session ID??
        return sessionId.length > 16 ? sessionId.substring(0, 16) + '...' : sessionId;
    }
    
    formatTime(timestamp) {
        const date = new Date(timestamp);
        const now = new Date();
        
        if (date.toDateString() === now.toDateString()) {
            // ????????
            return date.toLocaleTimeString('zh-CN', { 
                hour: '2-digit', 
                minute: '2-digit',
                second: '2-digit'
            });
        } else {
            // ????????????
            return date.toLocaleString('zh-CN', {
                month: '2-digit',
                day: '2-digit',
                hour: '2-digit',
                minute: '2-digit'
            });
        }
    }
    
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

// ?????
const app = new TranscriptionApp();
