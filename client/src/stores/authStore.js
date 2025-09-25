import { create } from 'zustand'
import axios from 'axios'

export const useAuthStore = create((set, get) => ({
  agent: null,
  token: null,
  isAuthenticated: false,

  login: async (agentId, password) => {
    try {
      const response = await axios.post('/api/calls/agent/login', {
        agentId,
        password
      })

      const { agent, token } = response.data
      
      // Store token in localStorage
      localStorage.setItem('authToken', token)
      localStorage.setItem('agent', JSON.stringify(agent))
      
      // Set axios default header
      axios.defaults.headers.common['Authorization'] = `Bearer ${token}`
      
      set({
        agent,
        token,
        isAuthenticated: true
      })

      return { success: true }
    } catch (error) {
      console.error('Login failed:', error)
      return { 
        success: false, 
        error: error.response?.data?.error || '登录失败'
      }
    }
  },

  logout: async () => {
    try {
      await axios.post('/api/calls/agent/logout')
    } catch (error) {
      console.error('Logout error:', error)
    }

    // Clear storage
    localStorage.removeItem('authToken')
    localStorage.removeItem('agent')
    
    // Clear axios header
    delete axios.defaults.headers.common['Authorization']
    
    set({
      agent: null,
      token: null,
      isAuthenticated: false
    })
  },

  checkAuth: () => {
    const token = localStorage.getItem('authToken')
    const agentStr = localStorage.getItem('agent')
    
    if (token && agentStr) {
      try {
        const agent = JSON.parse(agentStr)
        
        // Set axios default header
        axios.defaults.headers.common['Authorization'] = `Bearer ${token}`
        
        set({
          agent,
          token,
          isAuthenticated: true
        })
        
        return true
      } catch (error) {
        console.error('Failed to parse agent data:', error)
      }
    }
    
    return false
  },

  updateAgentStatus: async (status) => {
    try {
      const response = await axios.patch('/api/calls/agent/status', { status })
      const updatedAgent = response.data
      
      set(state => ({
        agent: { ...state.agent, ...updatedAgent }
      }))
      
      localStorage.setItem('agent', JSON.stringify(updatedAgent))
      
      return { success: true }
    } catch (error) {
      console.error('Failed to update status:', error)
      return { 
        success: false, 
        error: error.response?.data?.error || '状态更新失败'
      }
    }
  }
}))