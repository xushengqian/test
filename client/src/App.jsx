import React, { useEffect, useState } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { Box } from '@mui/material'
import Layout from './components/Layout'
import Dashboard from './pages/Dashboard'
import CallMonitor from './pages/CallMonitor'
import CallHistory from './pages/CallHistory'
import AgentLogin from './pages/AgentLogin'
import NewCall from './pages/NewCall'
import { useAuthStore } from './stores/authStore'
import { SocketProvider } from './contexts/SocketContext'

function App() {
  const { isAuthenticated, checkAuth } = useAuthStore()
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    checkAuth()
    setLoading(false)
  }, [])

  if (loading) {
    return (
      <Box sx={{ 
        display: 'flex', 
        justifyContent: 'center', 
        alignItems: 'center', 
        height: '100vh' 
      }}>
        加载中...
      </Box>
    )
  }

  if (!isAuthenticated) {
    return <AgentLogin />
  }

  return (
    <SocketProvider>
      <Layout>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/call/:callId" element={<CallMonitor />} />
          <Route path="/history" element={<CallHistory />} />
          <Route path="/new-call" element={<NewCall />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Layout>
    </SocketProvider>
  )
}

export default App