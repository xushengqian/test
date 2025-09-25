import React, { createContext, useContext, useEffect, useState } from 'react'
import io from 'socket.io-client'
import { useAuthStore } from '../stores/authStore'
import toast from 'react-hot-toast'

const SocketContext = createContext()

export const useSocket = () => {
  const context = useContext(SocketContext)
  if (!context) {
    throw new Error('useSocket must be used within SocketProvider')
  }
  return context
}

export const SocketProvider = ({ children }) => {
  const [socket, setSocket] = useState(null)
  const [connected, setConnected] = useState(false)
  const [activeCalls, setActiveCalls] = useState([])
  const [callTranscripts, setCallTranscripts] = useState({})
  const { agent, token } = useAuthStore()

  useEffect(() => {
    if (!agent || !token) return

    // Create socket connection
    const newSocket = io(window.location.origin, {
      transports: ['websocket'],
      auth: {
        token
      }
    })

    // Connection events
    newSocket.on('connect', () => {
      console.log('Socket connected')
      setConnected(true)
      
      // Authenticate agent
      newSocket.emit('agent:authenticate', {
        agentId: agent.id,
        token
      })
    })

    newSocket.on('disconnect', () => {
      console.log('Socket disconnected')
      setConnected(false)
    })

    newSocket.on('error', (error) => {
      console.error('Socket error:', error)
      toast.error(error.message || '连接错误')
    })

    // Agent events
    newSocket.on('agent:authenticated', (data) => {
      console.log('Agent authenticated:', data)
      toast.success('已连接到呼叫中心')
    })

    // Call events
    newSocket.on('calls:active', (calls) => {
      setActiveCalls(calls)
    })

    newSocket.on('call:initiated', (data) => {
      toast.success(`新的呼叫: ${data.phoneNumber}`)
      setActiveCalls(prev => [...prev, data])
    })

    newSocket.on('call:ended', (data) => {
      setActiveCalls(prev => prev.filter(call => call.id !== data.callId))
    })

    newSocket.on('call:agent_joined', (data) => {
      toast.info(`坐席 ${data.agentId} 已接入通话`)
    })

    // Transcription events
    newSocket.on('call:transcription', (data) => {
      const { callId, transcription } = data
      setCallTranscripts(prev => ({
        ...prev,
        [callId]: [...(prev[callId] || []), transcription]
      }))
    })

    newSocket.on('transcription:update', (data) => {
      const { callId, transcription } = data
      setCallTranscripts(prev => ({
        ...prev,
        [callId]: [...(prev[callId] || []), transcription]
      }))
    })

    newSocket.on('call:ai_response', (data) => {
      const { callId, response } = data
      setCallTranscripts(prev => ({
        ...prev,
        [callId]: [...(prev[callId] || []), {
          text: response,
          speaker: 'ai',
          timestamp: new Date()
        }]
      }))
    })

    setSocket(newSocket)

    return () => {
      newSocket.close()
    }
  }, [agent, token])

  const monitorCall = (callId) => {
    if (socket) {
      socket.emit('call:monitor', callId)
    }
  }

  const takeoverCall = (callId) => {
    if (socket && agent) {
      socket.emit('call:takeover', {
        callId,
        agentId: agent.id
      })
    }
  }

  const sendMessage = (callId, message) => {
    if (socket) {
      socket.emit('call:send_message', {
        callId,
        message,
        isAgent: true
      })
    }
  }

  const endCall = (callId) => {
    if (socket) {
      socket.emit('call:end', callId)
    }
  }

  const value = {
    socket,
    connected,
    activeCalls,
    callTranscripts,
    monitorCall,
    takeoverCall,
    sendMessage,
    endCall
  }

  return (
    <SocketContext.Provider value={value}>
      {children}
    </SocketContext.Provider>
  )
}