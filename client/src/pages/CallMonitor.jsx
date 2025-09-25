import React, { useEffect, useState, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Box,
  Card,
  CardContent,
  Grid,
  Typography,
  Paper,
  TextField,
  IconButton,
  Button,
  Chip,
  Avatar,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  Divider,
  Alert
} from '@mui/material'
import {
  Send as SendIcon,
  Phone as PhoneIcon,
  PhoneDisabled as EndCallIcon,
  PersonAdd as PersonAddIcon,
  SmartToy as AIIcon,
  Person as PersonIcon,
  Support as SupportIcon,
  ArrowBack as BackIcon
} from '@mui/icons-material'
import { format } from 'date-fns'
import { zhCN } from 'date-fns/locale'
import { useSocket } from '../contexts/SocketContext'
import { useAuthStore } from '../stores/authStore'
import toast from 'react-hot-toast'

function CallMonitor() {
  const { callId } = useParams()
  const navigate = useNavigate()
  const { agent } = useAuthStore()
  const { 
    callTranscripts, 
    monitorCall, 
    takeoverCall, 
    sendMessage, 
    endCall 
  } = useSocket()
  
  const [message, setMessage] = useState('')
  const [callDetails, setCallDetails] = useState(null)
  const [isAgentConnected, setIsAgentConnected] = useState(false)
  const messagesEndRef = useRef(null)

  const transcripts = callTranscripts[callId] || []

  useEffect(() => {
    // Monitor this specific call
    monitorCall(callId)
    
    // Fetch call details
    fetchCallDetails()
  }, [callId])

  useEffect(() => {
    // Auto-scroll to bottom when new messages arrive
    scrollToBottom()
  }, [transcripts])

  const fetchCallDetails = async () => {
    try {
      const response = await fetch(`/api/calls/${callId}`, {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('authToken')}`
        }
      })
      const data = await response.json()
      setCallDetails(data)
      setIsAgentConnected(data.isAgentConnected)
    } catch (error) {
      console.error('Failed to fetch call details:', error)
      toast.error('获取通话详情失败')
    }
  }

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  const handleSendMessage = () => {
    if (!message.trim()) return
    
    sendMessage(callId, message)
    setMessage('')
  }

  const handleTakeover = () => {
    takeoverCall(callId)
    setIsAgentConnected(true)
    toast.success('已接入通话')
  }

  const handleEndCall = () => {
    endCall(callId)
    toast.success('通话已结束')
    navigate('/')
  }

  const getSpeakerInfo = (speaker) => {
    switch (speaker) {
      case 'customer':
        return {
          icon: <PersonIcon />,
          name: '客户',
          color: 'primary'
        }
      case 'ai':
        return {
          icon: <AIIcon />,
          name: 'AI 助手',
          color: 'secondary'
        }
      case 'agent':
        return {
          icon: <SupportIcon />,
          name: '人工坐席',
          color: 'success'
        }
      default:
        return {
          icon: <PersonIcon />,
          name: '未知',
          color: 'default'
        }
    }
  }

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
        <IconButton onClick={() => navigate('/')} sx={{ mr: 2 }}>
          <BackIcon />
        </IconButton>
        <Typography variant="h5" sx={{ flexGrow: 1 }}>
          通话监控 - {callDetails?.phoneNumber || callId}
        </Typography>
        {!isAgentConnected && (
          <Button
            variant="contained"
            color="primary"
            startIcon={<PersonAddIcon />}
            onClick={handleTakeover}
            sx={{ mr: 2 }}
          >
            人工接入
          </Button>
        )}
        <Button
          variant="contained"
          color="error"
          startIcon={<EndCallIcon />}
          onClick={handleEndCall}
        >
          结束通话
        </Button>
      </Box>

      <Grid container spacing={3}>
        {/* Call Info */}
        <Grid item xs={12} md={4}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                通话信息
              </Typography>
              <Divider sx={{ my: 2 }} />
              
              <Box sx={{ mb: 2 }}>
                <Typography variant="body2" color="textSecondary">
                  电话号码
                </Typography>
                <Typography variant="body1">
                  {callDetails?.phoneNumber || '加载中...'}
                </Typography>
              </Box>

              <Box sx={{ mb: 2 }}>
                <Typography variant="body2" color="textSecondary">
                  通话状态
                </Typography>
                <Chip
                  label={callDetails?.status || '未知'}
                  color={callDetails?.status === 'active' ? 'success' : 'default'}
                  size="small"
                  sx={{ mt: 0.5 }}
                />
              </Box>

              <Box sx={{ mb: 2 }}>
                <Typography variant="body2" color="textSecondary">
                  开始时间
                </Typography>
                <Typography variant="body1">
                  {callDetails?.startTime 
                    ? format(new Date(callDetails.startTime), 'yyyy-MM-dd HH:mm:ss', { locale: zhCN })
                    : '未知'}
                </Typography>
              </Box>

              <Box sx={{ mb: 2 }}>
                <Typography variant="body2" color="textSecondary">
                  持续时间
                </Typography>
                <Typography variant="body1">
                  {callDetails?.duration ? `${callDetails.duration} 秒` : '计算中...'}
                </Typography>
              </Box>

              {isAgentConnected && (
                <Box sx={{ mb: 2 }}>
                  <Typography variant="body2" color="textSecondary">
                    接入坐席
                  </Typography>
                  <Typography variant="body1">
                    {callDetails?.agentId || agent?.id}
                  </Typography>
                </Box>
              )}
            </CardContent>
          </Card>

          {/* AI Script Info */}
          {callDetails?.script && (
            <Card sx={{ mt: 2 }}>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  AI 脚本
                </Typography>
                <Divider sx={{ my: 2 }} />
                <Typography variant="body2" color="textSecondary">
                  {JSON.stringify(callDetails.script, null, 2)}
                </Typography>
              </CardContent>
            </Card>
          )}
        </Grid>

        {/* Transcript */}
        <Grid item xs={12} md={8}>
          <Card sx={{ height: '70vh', display: 'flex', flexDirection: 'column' }}>
            <CardContent sx={{ flexGrow: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
              <Typography variant="h6" gutterBottom>
                实时对话记录
              </Typography>
              <Divider sx={{ mb: 2 }} />
              
              {!isAgentConnected && (
                <Alert severity="info" sx={{ mb: 2 }}>
                  当前为 AI 自动应答模式，您可以点击"人工接入"按钮接管通话
                </Alert>
              )}

              <List sx={{ flexGrow: 1, overflow: 'auto' }}>
                {transcripts.length === 0 ? (
                  <ListItem>
                    <ListItemText
                      primary={
                        <Typography color="textSecondary" align="center">
                          等待对话开始...
                        </Typography>
                      }
                    />
                  </ListItem>
                ) : (
                  transcripts.map((transcript, index) => {
                    const speakerInfo = getSpeakerInfo(transcript.speaker)
                    return (
                      <ListItem key={index} className="fade-in">
                        <ListItemAvatar>
                          <Avatar sx={{ bgcolor: `${speakerInfo.color}.main` }}>
                            {speakerInfo.icon}
                          </Avatar>
                        </ListItemAvatar>
                        <ListItemText
                          primary={
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                              <Typography variant="subtitle2">
                                {speakerInfo.name}
                              </Typography>
                              <Typography variant="caption" color="textSecondary">
                                {format(new Date(transcript.timestamp), 'HH:mm:ss')}
                              </Typography>
                              {transcript.confidence && (
                                <Chip
                                  label={`${Math.round(transcript.confidence * 100)}%`}
                                  size="small"
                                  variant="outlined"
                                />
                              )}
                            </Box>
                          }
                          secondary={transcript.text}
                        />
                      </ListItem>
                    )
                  })
                )}
                <div ref={messagesEndRef} />
              </List>
            </CardContent>

            {/* Message Input */}
            {isAgentConnected && (
              <Box sx={{ p: 2, borderTop: 1, borderColor: 'divider' }}>
                <Box sx={{ display: 'flex', gap: 1 }}>
                  <TextField
                    fullWidth
                    placeholder="输入消息..."
                    value={message}
                    onChange={(e) => setMessage(e.target.value)}
                    onKeyPress={(e) => {
                      if (e.key === 'Enter' && !e.shiftKey) {
                        e.preventDefault()
                        handleSendMessage()
                      }
                    }}
                    size="small"
                    multiline
                    maxRows={3}
                  />
                  <IconButton
                    color="primary"
                    onClick={handleSendMessage}
                    disabled={!message.trim()}
                  >
                    <SendIcon />
                  </IconButton>
                </Box>
              </Box>
            )}
          </Card>
        </Grid>
      </Grid>
    </Box>
  )
}

export default CallMonitor