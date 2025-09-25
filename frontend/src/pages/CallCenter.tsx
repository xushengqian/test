import React, { useState } from 'react';
import {
  Box,
  Grid,
  Paper,
  Typography,
  Button,
  List,
  ListItem,
  ListItemText,
  ListItemSecondaryAction,
  IconButton,
  Chip,
  Card,
  CardContent,
  TextField,
  Divider,
  Alert,
  Badge,
  Tooltip,
  Tab,
  Tabs
} from '@mui/material';
import {
  Phone as PhoneIcon,
  PhoneDisabled as PhoneDisabledIcon,
  PersonAdd as PersonAddIcon,
  SwapHoriz as SwapHorizIcon,
  Send as SendIcon,
  Mic as MicIcon,
  MicOff as MicOffIcon,
  VolumeUp as VolumeUpIcon,
  VolumeOff as VolumeOffIcon,
  CallEnd as CallEndIcon,
  PlayArrow as PlayArrowIcon,
  Pause as PauseIcon
} from '@mui/icons-material';
import { useCall } from '../contexts/CallContext';
import { useAuth } from '../contexts/AuthContext';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;
  return (
    <div hidden={value !== index} {...other}>
      {value === index && <Box sx={{ p: 2 }}>{children}</Box>}
    </div>
  );
}

const CallCenter: React.FC = () => {
  const { activeCalls, currentCall, needsAgentCalls, joinCall, interveneCall, takeoverCall, releaseCall, sendMessage } = useCall();
  const { agent } = useAuth();
  const [tabValue, setTabValue] = useState(0);
  const [messageInput, setMessageInput] = useState('');
  const [isMuted, setIsMuted] = useState(false);
  const [isSpeakerOn, setIsSpeakerOn] = useState(true);

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setTabValue(newValue);
  };

  const handleSendMessage = () => {
    if (messageInput.trim()) {
      sendMessage(messageInput);
      setMessageInput('');
    }
  };

  const getCallStatusColor = (status: string) => {
    switch (status) {
      case 'connected': return 'success';
      case 'on_hold': return 'warning';
      case 'ended': return 'default';
      case 'failed': return 'error';
      default: return 'info';
    }
  };

  const getCallStatusText = (status: string) => {
    switch (status) {
      case 'initiating': return '呼叫中';
      case 'ringing': return '振铃中';
      case 'connected': return '通话中';
      case 'on_hold': return '保持中';
      case 'ended': return '已结束';
      case 'failed': return '失败';
      case 'no_answer': return '无应答';
      default: return status;
    }
  };

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        呼叫中心
      </Typography>

      <Grid container spacing={3}>
        {/* 左侧：呼叫队列 */}
        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 2, height: '80vh' }}>
            <Tabs value={tabValue} onChange={handleTabChange} variant="fullWidth">
              <Tab label={`活动通话 (${activeCalls.length})`} />
              <Tab label={
                <Badge badgeContent={needsAgentCalls.length} color="error">
                  需要介入
                </Badge>
              } />
            </Tabs>

            <TabPanel value={tabValue} index={0}>
              <List sx={{ maxHeight: '65vh', overflow: 'auto' }}>
                {activeCalls.map((call) => (
                  <ListItem key={call.id} sx={{ mb: 1, bgcolor: 'background.paper', borderRadius: 1, border: 1, borderColor: 'divider' }}>
                    <ListItemText
                      primary={
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <Typography variant="subtitle1">{call.phoneNumber}</Typography>
                          <Chip 
                            size="small" 
                            label={getCallStatusText(call.status)} 
                            color={getCallStatusColor(call.status) as any}
                          />
                        </Box>
                      }
                      secondary={
                        <Box>
                          <Typography variant="body2" color="text.secondary">
                            {call.isRobotHandling ? '🤖 机器人处理中' : `👤 ${call.agentId || '等待坐席'}`}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            时长: {call.duration}秒
                          </Typography>
                        </Box>
                      }
                    />
                    <ListItemSecondaryAction>
                      {currentCall?.id === call.id ? (
                        <Chip label="当前通话" color="primary" size="small" />
                      ) : (
                        <Box sx={{ display: 'flex', gap: 1 }}>
                          {call.isRobotHandling && (
                            <>
                              <Tooltip title="监听">
                                <IconButton size="small" onClick={() => joinCall(call.id)}>
                                  <VolumeUpIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                              <Tooltip title="介入">
                                <IconButton size="small" color="warning" onClick={() => interveneCall(call.id)}>
                                  <PersonAddIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                              <Tooltip title="接管">
                                <IconButton size="small" color="primary" onClick={() => takeoverCall(call.id)}>
                                  <SwapHorizIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                            </>
                          )}
                        </Box>
                      )}
                    </ListItemSecondaryAction>
                  </ListItem>
                ))}
                {activeCalls.length === 0 && (
                  <Box sx={{ textAlign: 'center', py: 4 }}>
                    <Typography color="text.secondary">暂无活动通话</Typography>
                  </Box>
                )}
              </List>
            </TabPanel>

            <TabPanel value={tabValue} index={1}>
              <List sx={{ maxHeight: '65vh', overflow: 'auto' }}>
                {needsAgentCalls.map((call) => (
                  <ListItem key={call.id} sx={{ mb: 1, bgcolor: 'error.light', borderRadius: 1 }}>
                    <ListItemText
                      primary={call.phoneNumber}
                      secondary="客户请求人工服务"
                    />
                    <ListItemSecondaryAction>
                      <Button
                        variant="contained"
                        color="primary"
                        size="small"
                        startIcon={<PhoneIcon />}
                        onClick={() => takeoverCall(call.id)}
                      >
                        接管
                      </Button>
                    </ListItemSecondaryAction>
                  </ListItem>
                ))}
                {needsAgentCalls.length === 0 && (
                  <Box sx={{ textAlign: 'center', py: 4 }}>
                    <Typography color="text.secondary">暂无需要介入的通话</Typography>
                  </Box>
                )}
              </List>
            </TabPanel>
          </Paper>
        </Grid>

        {/* 右侧：当前通话详情 */}
        <Grid item xs={12} md={8}>
          {currentCall ? (
            <Paper sx={{ p: 2, height: '80vh' }}>
              {/* 通话信息头部 */}
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                <Box>
                  <Typography variant="h6">
                    {currentCall.phoneNumber}
                  </Typography>
                  <Chip 
                    label={getCallStatusText(currentCall.status)} 
                    color={getCallStatusColor(currentCall.status) as any}
                    size="small"
                  />
                </Box>
                <Box sx={{ display: 'flex', gap: 1 }}>
                  <IconButton 
                    color={isMuted ? 'error' : 'default'}
                    onClick={() => setIsMuted(!isMuted)}
                  >
                    {isMuted ? <MicOffIcon /> : <MicIcon />}
                  </IconButton>
                  <IconButton 
                    color={isSpeakerOn ? 'primary' : 'default'}
                    onClick={() => setIsSpeakerOn(!isSpeakerOn)}
                  >
                    {isSpeakerOn ? <VolumeUpIcon /> : <VolumeOffIcon />}
                  </IconButton>
                  {currentCall.isRobotHandling ? (
                    <Button
                      variant="contained"
                      color="primary"
                      startIcon={<SwapHorizIcon />}
                      onClick={() => takeoverCall(currentCall.id)}
                    >
                      接管通话
                    </Button>
                  ) : (
                    <Button
                      variant="outlined"
                      color="warning"
                      startIcon={<SwapHorizIcon />}
                      onClick={() => releaseCall(currentCall.id)}
                    >
                      转回机器人
                    </Button>
                  )}
                  <Button
                    variant="contained"
                    color="error"
                    startIcon={<CallEndIcon />}
                  >
                    结束通话
                  </Button>
                </Box>
              </Box>

              <Divider sx={{ mb: 2 }} />

              {/* 对话记录 */}
              <Box sx={{ height: 'calc(100% - 200px)', overflow: 'auto', mb: 2, p: 2, bgcolor: 'background.default', borderRadius: 1 }}>
                <List>
                  {currentCall.transcript.map((entry, index) => (
                    <ListItem key={index} sx={{ 
                      justifyContent: entry.speaker === 'agent' ? 'flex-end' : 'flex-start',
                      mb: 1
                    }}>
                      <Paper sx={{ 
                        p: 1.5, 
                        maxWidth: '70%',
                        bgcolor: entry.speaker === 'agent' ? 'primary.light' : 
                                entry.speaker === 'robot' ? 'info.light' : 
                                entry.speaker === 'system' ? 'warning.light' : 'background.paper'
                      }}>
                        <Typography variant="caption" color="text.secondary">
                          {entry.speaker === 'agent' ? '坐席' : 
                           entry.speaker === 'robot' ? '机器人' : 
                           entry.speaker === 'customer' ? '客户' : '系统'}
                        </Typography>
                        <Typography variant="body1">{entry.text}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          {new Date(entry.timestamp).toLocaleTimeString()}
                        </Typography>
                      </Paper>
                    </ListItem>
                  ))}
                </List>
              </Box>

              {/* 消息输入框 */}
              {!currentCall.isRobotHandling && (
                <Box sx={{ display: 'flex', gap: 1 }}>
                  <TextField
                    fullWidth
                    variant="outlined"
                    placeholder="输入消息（将转换为语音）..."
                    value={messageInput}
                    onChange={(e) => setMessageInput(e.target.value)}
                    onKeyPress={(e) => e.key === 'Enter' && handleSendMessage()}
                  />
                  <Button
                    variant="contained"
                    endIcon={<SendIcon />}
                    onClick={handleSendMessage}
                    disabled={!messageInput.trim()}
                  >
                    发送
                  </Button>
                </Box>
              )}
            </Paper>
          ) : (
            <Paper sx={{ p: 4, height: '80vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Box sx={{ textAlign: 'center' }}>
                <PhoneDisabledIcon sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
                <Typography variant="h6" color="text.secondary">
                  未选择通话
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                  从左侧列表选择一个通话进行监听或介入
                </Typography>
              </Box>
            </Paper>
          )}
        </Grid>
      </Grid>
    </Box>
  );
};

export default CallCenter;