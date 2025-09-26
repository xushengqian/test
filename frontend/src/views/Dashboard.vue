<template>
  <div class="dashboard">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon">
              <el-icon size="40" color="#409EFF"><Phone /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ dashboardData.today_stats.total_calls }}</div>
              <div class="stat-label">今日外呼</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon">
              <el-icon size="40" color="#67C23A"><Check /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ dashboardData.today_stats.answered_calls }}</div>
              <div class="stat-label">接通数量</div>
              <div class="stat-rate">{{ dashboardData.today_stats.answer_rate.toFixed(1) }}%</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon">
              <el-icon size="40" color="#E6A23C"><User /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ dashboardData.today_stats.transferred_calls }}</div>
              <div class="stat-label">转人工</div>
              <div class="stat-rate">{{ dashboardData.today_stats.transfer_rate.toFixed(1) }}%</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon">
              <el-icon size="40" color="#F56C6C"><Timer /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ formatDuration(dashboardData.today_stats.avg_duration) }}</div>
              <div class="stat-label">平均时长</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 实时状态 -->
    <el-row :gutter="20" class="realtime-row">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>实时状态</span>
              <el-tag :type="systemLoadType" size="small">
                系统负载: {{ dashboardData.realtime.system_load.load_percentage.toFixed(1) }}%
              </el-tag>
            </div>
          </template>
          
          <div class="realtime-stats">
            <div class="realtime-item">
              <div class="realtime-label">活跃通话</div>
              <div class="realtime-value active-calls">{{ dashboardData.realtime.active_calls }}</div>
            </div>
            <div class="realtime-item">
              <div class="realtime-label">可用坐席</div>
              <div class="realtime-value available-agents">{{ dashboardData.realtime.available_agents }}</div>
            </div>
            <div class="realtime-item">
              <div class="realtime-label">排队数量</div>
              <div class="realtime-value queue-length">{{ dashboardData.realtime.queue_length }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>系统消息</span>
              <el-button type="text" size="small" @click="clearMessages">清空</el-button>
            </div>
          </template>
          
          <div class="message-list">
            <div 
              v-for="message in recentMessages" 
              :key="message.timestamp" 
              class="message-item"
            >
              <div class="message-time">{{ formatTime(message.timestamp) }}</div>
              <div class="message-content">{{ formatMessage(message) }}</div>
            </div>
            <div v-if="recentMessages.length === 0" class="no-messages">
              暂无消息
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20" class="charts-row">
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>今日通话趋势</span>
          </template>
          <div class="chart-container">
            <v-chart :option="callTrendOption" style="height: 300px;" />
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>转人工原因分析</span>
          </template>
          <div class="chart-container">
            <v-chart :option="transferReasonOption" style="height: 300px;" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart } from 'echarts/charts'
import { 
  TitleComponent, 
  TooltipComponent, 
  LegendComponent,
  GridComponent 
} from 'echarts/components'
import VChart from 'vue-echarts'
import { useSystemStore } from '../stores/system'
import { useWebSocketStore } from '../stores/websocket'
import api from '../utils/api'

use([
  CanvasRenderer,
  LineChart,
  PieChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent
])

const systemStore = useSystemStore()
const websocketStore = useWebSocketStore()

const dashboardData = computed(() => systemStore.dashboardData)
const recentMessages = computed(() => websocketStore.messages.slice(-10).reverse())

const hourlyStats = ref([])
const transferReasons = ref([])

const systemLoadType = computed(() => {
  const load = dashboardData.value.realtime.system_load.load_percentage
  if (load < 50) return 'success'
  if (load < 80) return 'warning'
  return 'danger'
})

// 通话趋势图表配置
const callTrendOption = computed(() => ({
  tooltip: {
    trigger: 'axis'
  },
  legend: {
    data: ['总通话', '接通数', '转人工']
  },
  xAxis: {
    type: 'category',
    data: hourlyStats.value.map(item => `${item.hour}:00`)
  },
  yAxis: {
    type: 'value'
  },
  series: [
    {
      name: '总通话',
      type: 'line',
      data: hourlyStats.value.map(item => item.total_calls),
      smooth: true
    },
    {
      name: '接通数',
      type: 'line',
      data: hourlyStats.value.map(item => item.answered_calls),
      smooth: true
    },
    {
      name: '转人工',
      type: 'line',
      data: hourlyStats.value.map(item => item.transferred_calls),
      smooth: true
    }
  ]
}))

// 转人工原因饼图配置
const transferReasonOption = computed(() => ({
  tooltip: {
    trigger: 'item'
  },
  legend: {
    orient: 'vertical',
    left: 'left'
  },
  series: [
    {
      type: 'pie',
      radius: '50%',
      data: transferReasons.value,
      emphasis: {
        itemStyle: {
          shadowBlur: 10,
          shadowOffsetX: 0,
          shadowColor: 'rgba(0, 0, 0, 0.5)'
        }
      }
    }
  ]
}))

const formatDuration = (seconds) => {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

const formatTime = (timestamp) => {
  return new Date(timestamp).toLocaleTimeString()
}

const formatMessage = (message) => {
  switch (message.type) {
    case 'call_initiated':
      return `外呼发起: ${message.data.phone}`
    case 'call_answered':
      return `通话接通: ${message.data.call_uuid}`
    case 'call_ended':
      return `通话结束: ${message.data.call_uuid}`
    case 'call_transferred':
      return `转接人工: ${message.data.call_uuid}`
    case 'agent_online':
      return `坐席上线: ${message.data.name}`
    case 'agent_offline':
      return `坐席下线: ${message.data.name}`
    default:
      return JSON.stringify(message.data)
  }
}

const clearMessages = () => {
  websocketStore.messages.splice(0)
}

const fetchHourlyStats = async () => {
  try {
    const response = await api.get('/admin/statistics/hourly')
    if (response.data.success) {
      hourlyStats.value = response.data.statistics
    }
  } catch (error) {
    console.error('Failed to fetch hourly stats:', error)
  }
}

const fetchTransferReasons = async () => {
  try {
    // 模拟数据，实际应该从API获取
    transferReasons.value = [
      { value: 35, name: '用户要求' },
      { value: 25, name: '价格咨询' },
      { value: 20, name: '投诉问题' },
      { value: 15, name: '复杂问题' },
      { value: 5, name: '其他' }
    ]
  } catch (error) {
    console.error('Failed to fetch transfer reasons:', error)
  }
}

onMounted(() => {
  fetchHourlyStats()
  fetchTransferReasons()
  
  // 定期刷新图表数据
  setInterval(() => {
    fetchHourlyStats()
  }, 60000) // 每分钟刷新一次
})
</script>

<style scoped>
.dashboard {
  padding: 0;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  height: 120px;
}

.stat-content {
  display: flex;
  align-items: center;
  height: 100%;
}

.stat-icon {
  margin-right: 20px;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #303133;
  line-height: 1;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 5px;
}

.stat-rate {
  font-size: 12px;
  color: #67C23A;
  margin-top: 2px;
}

.realtime-row {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.realtime-stats {
  display: flex;
  justify-content: space-around;
  text-align: center;
}

.realtime-item {
  flex: 1;
}

.realtime-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 10px;
}

.realtime-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.active-calls {
  color: #409EFF;
}

.available-agents {
  color: #67C23A;
}

.queue-length {
  color: #E6A23C;
}

.message-list {
  height: 200px;
  overflow-y: auto;
}

.message-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.message-time {
  font-size: 12px;
  color: #909399;
  min-width: 80px;
}

.message-content {
  flex: 1;
  font-size: 14px;
  color: #303133;
  margin-left: 10px;
}

.no-messages {
  text-align: center;
  color: #909399;
  padding: 50px 0;
}

.charts-row {
  margin-bottom: 20px;
}

.chart-container {
  width: 100%;
}
</style>