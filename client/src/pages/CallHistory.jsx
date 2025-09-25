import React, { useState, useEffect } from 'react'
import {
  Box,
  Card,
  CardContent,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  Paper,
  Chip,
  IconButton,
  TextField,
  InputAdornment,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Button
} from '@mui/material'
import {
  Search as SearchIcon,
  Download as DownloadIcon,
  PlayArrow as PlayIcon,
  Description as TranscriptIcon,
  FilterList as FilterIcon
} from '@mui/icons-material'
import { format } from 'date-fns'
import { zhCN } from 'date-fns/locale'
import axios from 'axios'
import toast from 'react-hot-toast'

function CallHistory() {
  const [calls, setCalls] = useState([])
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(0)
  const [rowsPerPage, setRowsPerPage] = useState(10)
  const [searchTerm, setSearchTerm] = useState('')
  const [statusFilter, setStatusFilter] = useState('all')
  const [dateFilter, setDateFilter] = useState('all')

  // Mock data for demonstration
  useEffect(() => {
    loadCallHistory()
  }, [])

  const loadCallHistory = async () => {
    setLoading(true)
    
    // Mock data - in production, this would be an API call
    const mockCalls = [
      {
        id: 'call-001',
        phoneNumber: '138****8001',
        status: 'completed',
        startTime: new Date('2024-01-15 10:30:00'),
        endTime: new Date('2024-01-15 10:35:00'),
        duration: 300,
        agentId: 'agent001',
        agentName: '张三',
        hasRecording: true,
        hasTranscript: true
      },
      {
        id: 'call-002',
        phoneNumber: '139****2345',
        status: 'completed',
        startTime: new Date('2024-01-15 11:00:00'),
        endTime: new Date('2024-01-15 11:08:00'),
        duration: 480,
        agentId: null,
        agentName: 'AI',
        hasRecording: true,
        hasTranscript: true
      },
      {
        id: 'call-003',
        phoneNumber: '136****5678',
        status: 'no-answer',
        startTime: new Date('2024-01-15 11:30:00'),
        endTime: new Date('2024-01-15 11:30:30'),
        duration: 30,
        agentId: null,
        agentName: 'AI',
        hasRecording: false,
        hasTranscript: false
      },
      {
        id: 'call-004',
        phoneNumber: '137****9012',
        status: 'completed',
        startTime: new Date('2024-01-15 14:00:00'),
        endTime: new Date('2024-01-15 14:12:00'),
        duration: 720,
        agentId: 'agent002',
        agentName: '李四',
        hasRecording: true,
        hasTranscript: true
      },
      {
        id: 'call-005',
        phoneNumber: '135****3456',
        status: 'failed',
        startTime: new Date('2024-01-15 14:30:00'),
        endTime: new Date('2024-01-15 14:30:05'),
        duration: 5,
        agentId: null,
        agentName: 'AI',
        hasRecording: false,
        hasTranscript: false
      }
    ]
    
    setTimeout(() => {
      setCalls(mockCalls)
      setLoading(false)
    }, 500)
  }

  const handleChangePage = (event, newPage) => {
    setPage(newPage)
  }

  const handleChangeRowsPerPage = (event) => {
    setRowsPerPage(parseInt(event.target.value, 10))
    setPage(0)
  }

  const formatDuration = (seconds) => {
    const mins = Math.floor(seconds / 60)
    const secs = seconds % 60
    return `${mins}:${secs.toString().padStart(2, '0')}`
  }

  const getStatusChip = (status) => {
    const statusConfig = {
      'completed': { label: '已完成', color: 'success' },
      'no-answer': { label: '未接听', color: 'warning' },
      'failed': { label: '失败', color: 'error' },
      'busy': { label: '忙线', color: 'default' }
    }
    
    const config = statusConfig[status] || statusConfig.failed
    return <Chip label={config.label} color={config.color} size="small" />
  }

  const handlePlayRecording = (callId) => {
    toast.success('播放录音功能开发中')
  }

  const handleViewTranscript = (callId) => {
    toast.success('查看记录功能开发中')
  }

  const handleDownloadRecording = (callId) => {
    toast.success('下载录音功能开发中')
  }

  const filteredCalls = calls.filter(call => {
    if (searchTerm && !call.phoneNumber.includes(searchTerm)) {
      return false
    }
    if (statusFilter !== 'all' && call.status !== statusFilter) {
      return false
    }
    return true
  })

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        通话记录
      </Typography>

      <Card>
        <CardContent>
          {/* Filters */}
          <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap' }}>
            <TextField
              placeholder="搜索电话号码..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              size="small"
              sx={{ minWidth: 250 }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon />
                  </InputAdornment>
                ),
              }}
            />

            <FormControl size="small" sx={{ minWidth: 120 }}>
              <InputLabel>状态</InputLabel>
              <Select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                label="状态"
              >
                <MenuItem value="all">全部</MenuItem>
                <MenuItem value="completed">已完成</MenuItem>
                <MenuItem value="no-answer">未接听</MenuItem>
                <MenuItem value="failed">失败</MenuItem>
              </Select>
            </FormControl>

            <FormControl size="small" sx={{ minWidth: 120 }}>
              <InputLabel>时间</InputLabel>
              <Select
                value={dateFilter}
                onChange={(e) => setDateFilter(e.target.value)}
                label="时间"
              >
                <MenuItem value="all">全部</MenuItem>
                <MenuItem value="today">今天</MenuItem>
                <MenuItem value="yesterday">昨天</MenuItem>
                <MenuItem value="week">本周</MenuItem>
                <MenuItem value="month">本月</MenuItem>
              </Select>
            </FormControl>

            <Button
              variant="outlined"
              startIcon={<FilterIcon />}
              onClick={loadCallHistory}
            >
              刷新
            </Button>
          </Box>

          {/* Table */}
          <TableContainer component={Paper} variant="outlined">
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>电话号码</TableCell>
                  <TableCell>状态</TableCell>
                  <TableCell>开始时间</TableCell>
                  <TableCell>时长</TableCell>
                  <TableCell>处理人</TableCell>
                  <TableCell align="center">录音</TableCell>
                  <TableCell align="center">操作</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {loading ? (
                  <TableRow>
                    <TableCell colSpan={7} align="center">
                      <Typography sx={{ py: 3 }}>加载中...</Typography>
                    </TableCell>
                  </TableRow>
                ) : filteredCalls.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} align="center">
                      <Typography color="textSecondary" sx={{ py: 3 }}>
                        暂无通话记录
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  filteredCalls
                    .slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
                    .map((call) => (
                      <TableRow key={call.id}>
                        <TableCell>
                          <Typography variant="body2" fontWeight="medium">
                            {call.phoneNumber}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          {getStatusChip(call.status)}
                        </TableCell>
                        <TableCell>
                          {format(call.startTime, 'MM-dd HH:mm:ss', { locale: zhCN })}
                        </TableCell>
                        <TableCell>
                          {formatDuration(call.duration)}
                        </TableCell>
                        <TableCell>
                          {call.agentName === 'AI' ? (
                            <Chip label="AI" size="small" variant="outlined" />
                          ) : (
                            <Typography variant="body2">
                              {call.agentName}
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell align="center">
                          {call.hasRecording ? (
                            <IconButton
                              size="small"
                              color="primary"
                              onClick={() => handlePlayRecording(call.id)}
                            >
                              <PlayIcon />
                            </IconButton>
                          ) : (
                            <Typography variant="body2" color="textSecondary">
                              -
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell align="center">
                          {call.hasTranscript && (
                            <IconButton
                              size="small"
                              onClick={() => handleViewTranscript(call.id)}
                              title="查看记录"
                            >
                              <TranscriptIcon />
                            </IconButton>
                          )}
                          {call.hasRecording && (
                            <IconButton
                              size="small"
                              onClick={() => handleDownloadRecording(call.id)}
                              title="下载录音"
                            >
                              <DownloadIcon />
                            </IconButton>
                          )}
                        </TableCell>
                      </TableRow>
                    ))
                )}
              </TableBody>
            </Table>
          </TableContainer>

          <TablePagination
            rowsPerPageOptions={[5, 10, 25]}
            component="div"
            count={filteredCalls.length}
            rowsPerPage={rowsPerPage}
            page={page}
            onPageChange={handleChangePage}
            onRowsPerPageChange={handleChangeRowsPerPage}
            labelRowsPerPage="每页显示"
            labelDisplayedRows={({ from, to, count }) => `${from}-${to} 共 ${count} 条`}
          />
        </CardContent>
      </Card>
    </Box>
  )
}

export default CallHistory