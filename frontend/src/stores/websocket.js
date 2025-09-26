import { defineStore } from 'pinia'
import { ref } from 'vue'
import { io } from 'socket.io-client'
import { ElMessage } from 'element-plus'

export const useWebSocketStore = defineStore('websocket', () => {
  const socket = ref(null)
  const connectionStatus = ref('disconnected')
  const messages = ref([])
  
  const connect = () => {
    try {
      socket.value = io('ws://localhost:8000/ws/admin/admin_001', {
        transports: ['websocket']
      })
      
      socket.value.on('connect', () => {
        connectionStatus.value = 'connected'
        console.log('WebSocket connected')
      })
      
      socket.value.on('disconnect', () => {
        connectionStatus.value = 'disconnected'
        console.log('WebSocket disconnected')
      })
      
      socket.value.on('message', (data) => {
        handleMessage(data)
      })
      
      socket.value.on('error', (error) => {
        console.error('WebSocket error:', error)
        ElMessage.error('WebSocket连接错误')
      })
      
    } catch (error) {
      console.error('Failed to connect WebSocket:', error)
      connectionStatus.value = 'error'
    }
  }
  
  const disconnect = () => {
    if (socket.value) {
      socket.value.disconnect()
      socket.value = null
      connectionStatus.value = 'disconnected'
    }
  }
  
  const handleMessage = (data) => {
    try {
      const message = typeof data === 'string' ? JSON.parse(data) : data
      messages.value.push({
        ...message,
        timestamp: new Date()
      })
      
      // 根据消息类型处理
      switch (message.type) {
        case 'call_initiated':
          ElMessage.success(`外呼发起: ${message.data.phone}`)
          break
        case 'call_answered':
          ElMessage.info(`通话接通: ${message.data.call_uuid}`)
          break
        case 'call_ended':
          ElMessage.info(`通话结束: ${message.data.call_uuid}`)
          break
        case 'call_transferred':
          ElMessage.warning(`转接人工: ${message.data.call_uuid}`)
          break
        case 'agent_online':
          ElMessage.success(`坐席上线: ${message.data.name}`)
          break
        case 'agent_offline':
          ElMessage.warning(`坐席下线: ${message.data.name}`)
          break
        default:
          console.log('Received message:', message)
      }
      
      // 保持消息列表长度
      if (messages.value.length > 100) {
        messages.value = messages.value.slice(-50)
      }
      
    } catch (error) {
      console.error('Failed to handle message:', error)
    }
  }
  
  const sendMessage = (message) => {
    if (socket.value && connectionStatus.value === 'connected') {
      socket.value.emit('message', JSON.stringify(message))
    } else {
      console.error('WebSocket not connected')
    }
  }
  
  return {
    socket,
    connectionStatus,
    messages,
    connect,
    disconnect,
    sendMessage
  }
})