import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box,
  Card,
  CardContent,
  Grid,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  IconButton,
  Button,
  Tooltip
} from '@mui/material'
import {
  Visibility as ViewIcon,
  Phone as PhoneIcon,
  PersonAdd as PersonAddIcon,
  PhoneDisabled as EndCallIcon,
  TrendingUp as TrendingUpIcon,
  Groups as GroupsIcon,
  Timer as TimerIcon
} from '@mui/icons-material'
import { format } from 'date-fns'
import { zhCN } from 'date-fns/locale'
import { useSocket } from '../contexts/SocketContext'
import toast from 'react-hot-toast'

function Dashboard() {
  const navigate = useNavigate()
  const { activeCalls, takeoverCall, endCall } = useSocket()
  const [stats] = useState({
    totalCalls: 156,
    avgDuration: '3:45',
    successRate: 78,
    activeAgents: 5
  })

  const formatDuration = (seconds) => {
    const mins = Math.floor(seconds / 60)
    const secs = seconds % 60
    return `${mins}:${secs.toString().padStart(2, '0')}`
  }

  const getStatusChip = (status) => {
    const statusConfig = {
      connecting: { label: '连接中', color: 'warning' },
      active: { label: '通话中', color: 'success' },
      ended: { label: '已结束', color: 'default' },
      failed: { label: '失败', color: 'error' }
    }
    
    const config = statusConfig[status] || statusConfig.ended
    return <Chip label={config.label} color={config.color} size="small" />
  }

  const handleViewCall = (callId) => {
    navigate(`/call/${callId}`)
  }

  const handleTakeoverCall = async (callId) => {
    try {
      takeoverCall(callId)
      toast.success('正在接入通话...')
      navigate(`/call/${callId}`)
    } catch (error) {
      toast.error('接入失败')
    }
  }

  const handleEndCall = async (callId) => {
    try {
      endCall(callId)
      toast.success('通话已结束')
    } catch (error) {
      toast.error('结束通话失败')
    }
  }

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        监控面板
      </Typography>

      {/* Statistics Cards */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <PhoneIcon color="primary" sx={{ mr: 1 }} />
                <Typography color="textSecondary" variant="body2">
                  今日通话
                </Typography>
              </Box>
              <Typography variant="h4">{stats.totalCalls}</Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', mt: 1 }}>
                <TrendingUpIcon sx={{ fontSize: 16, color: 'success.main', mr: 0.5 }} />
                <Typography variant="body2" color="success.main">
                  +12% 较昨日
                </Typography>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <TimerIcon color="primary" sx={{ mr: 1 }} />
                <Typography color="textSecondary" variant="body2">
                  平均时长
                </Typography>
              </Box>
              <Typography variant="h4">{stats.avgDuration}</Typography>
              <Typography variant="body2" color="textSecondary">
                分钟
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <TrendingUpIcon color="primary" sx={{ mr: 1 }} />
                <Typography color="textSecondary" variant="body2">
                  成功率
                </Typography>
              </Box>
              <Typography variant="h4">{stats.successRate}%</Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', mt: 1 }}>
                <TrendingUpIcon sx={{ fontSize: 16, color: 'success.main', mr: 0.5 }} />
                <Typography variant="body2" color="success.main">
                  +5% 较昨日
                </Typography>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <GroupsIcon color="primary" sx={{ mr: 1 }} />
                <Typography color="textSecondary" variant="body2">
                  在线坐席
                </Typography>
              </Box>
              <Typography variant="h4">{stats.activeAgents}</Typography>
              <Typography variant="body2" color="textSecondary">
                活跃通话: {activeCalls.length}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Active Calls Table */}
      <Card>
        <CardContent>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
            <Typography variant="h6">
              活跃通话 ({activeCalls.length})
            </Typography>
            <Button
              variant="contained"
              startIcon={<PhoneIcon />}
              onClick={() => navigate('/new-call')}
            >
              发起呼叫
            </Button>
          </Box>

          <TableContainer component={Paper} variant="outlined">
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>电话号码</TableCell>
                  <TableCell>状态</TableCell>
                  <TableCell>开始时间</TableCell>
                  <TableCell>持续时间</TableCell>
                  <TableCell>坐席</TableCell>
                  <TableCell align="center">操作</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {activeCalls.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6} align="center">
                      <Typography color="textSecondary" sx={{ py: 3 }}>
                        暂无活跃通话
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  activeCalls.map((call) => (
                    <TableRow key={call.id} className="fade-in">
                      <TableCell>
                        <Typography variant="body2" fontWeight="medium">
                          {call.phoneNumber}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        {getStatusChip(call.status)}
                      </TableCell>
                      <TableCell>
                        {format(new Date(call.startTime), 'HH:mm:ss', { locale: zhCN })}
                      </TableCell>
                      <TableCell>
                        {formatDuration(call.duration || 0)}
                      </TableCell>
                      <TableCell>
                        {call.isAgentConnected ? (
                          <Chip
                            label={call.agentId}
                            size="small"
                            color="primary"
                            variant="outlined"
                          />
                        ) : (
                          <Typography variant="body2" color="textSecondary">
                            AI 接听中
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell align="center">
                        <Tooltip title="查看详情">
                          <IconButton
                            size="small"
                            onClick={() => handleViewCall(call.id)}
                          >
                            <ViewIcon />
                          </IconButton>
                        </Tooltip>
                        {!call.isAgentConnected && (
                          <Tooltip title="人工接入">
                            <IconButton
                              size="small"
                              color="primary"
                              onClick={() => handleTakeoverCall(call.id)}
                            >
                              <PersonAddIcon />
                            </IconButton>
                          </Tooltip>
                        )}
                        <Tooltip title="结束通话">
                          <IconButton
                            size="small"
                            color="error"
                            onClick={() => handleEndCall(call.id)}
                          >
                            <EndCallIcon />
                          </IconButton>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </CardContent>
      </Card>
    </Box>
  )
}

export default Dashboard