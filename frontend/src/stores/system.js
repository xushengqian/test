import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '../utils/api'

export const useSystemStore = defineStore('system', () => {
  const status = ref({
    active_calls: 0,
    available_agents: 0,
    queue_length: 0,
    system_load: { load_percentage: 0 }
  })
  
  const dashboardData = ref({
    today_stats: {
      total_calls: 0,
      answered_calls: 0,
      transferred_calls: 0,
      answer_rate: 0,
      transfer_rate: 0,
      avg_duration: 0
    },
    realtime: {
      active_calls: 0,
      available_agents: 0,
      queue_length: 0,
      system_load: { load_percentage: 0 }
    }
  })
  
  let statusInterval = null
  
  const fetchSystemStatus = async () => {
    try {
      const response = await api.get('/status')
      if (response.data) {
        status.value = response.data
      }
    } catch (error) {
      console.error('Failed to fetch system status:', error)
    }
  }
  
  const fetchDashboardData = async () => {
    try {
      const response = await api.get('/admin/dashboard')
      if (response.data.success) {
        dashboardData.value = response.data.data
      }
    } catch (error) {
      console.error('Failed to fetch dashboard data:', error)
    }
  }
  
  const startStatusPolling = () => {
    fetchSystemStatus()
    fetchDashboardData()
    
    statusInterval = setInterval(() => {
      fetchSystemStatus()
      fetchDashboardData()
    }, 5000) // 每5秒更新一次
  }
  
  const stopStatusPolling = () => {
    if (statusInterval) {
      clearInterval(statusInterval)
      statusInterval = null
    }
  }
  
  return {
    status,
    dashboardData,
    fetchSystemStatus,
    fetchDashboardData,
    startStatusPolling,
    stopStatusPolling
  }
})