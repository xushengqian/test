import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box,
  Card,
  CardContent,
  TextField,
  Button,
  Typography,
  Grid,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Alert,
  Divider,
  Chip,
  Paper
} from '@mui/material'
import {
  Phone as PhoneIcon,
  SmartToy as AIIcon,
  Description as ScriptIcon,
  Send as SendIcon
} from '@mui/icons-material'
import axios from 'axios'
import toast from 'react-hot-toast'

const predefinedScripts = [
  {
    id: 'sales',
    name: '销售推广',
    description: '产品销售和推广脚本',
    script: {
      greeting: '您好，我是XX公司的智能客服，今天给您介绍一下我们的最新产品。',
      default: '让我为您详细介绍一下这个产品的特点...',
      farewell: '感谢您的时间，如有需要可以随时联系我们。'
    }
  },
  {
    id: 'survey',
    name: '满意度调查',
    description: '客户满意度调查脚本',
    script: {
      greeting: '您好，我是XX公司的客服，想了解一下您对我们服务的满意度。',
      default: '请问您对我们的服务还满意吗？',
      farewell: '感谢您的宝贵意见，祝您生活愉快！'
    }
  },
  {
    id: 'reminder',
    name: '事务提醒',
    description: '预约提醒、缴费提醒等',
    script: {
      greeting: '您好，这里是XX服务中心，有一个重要提醒需要告知您。',
      default: '您的预约时间是明天上午10点，请准时到达。',
      farewell: '感谢您的配合，再见！'
    }
  },
  {
    id: 'custom',
    name: '自定义脚本',
    description: '创建自定义对话脚本',
    script: {
      greeting: '',
      default: '',
      farewell: ''
    }
  }
]

function NewCall() {
  const navigate = useNavigate()
  const [phoneNumber, setPhoneNumber] = useState('')
  const [selectedScript, setSelectedScript] = useState('sales')
  const [customScript, setCustomScript] = useState({
    greeting: '',
    default: '',
    farewell: ''
  })
  const [metadata, setMetadata] = useState({
    customerName: '',
    orderNumber: '',
    notes: ''
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const handleScriptChange = (scriptId) => {
    setSelectedScript(scriptId)
    const script = predefinedScripts.find(s => s.id === scriptId)
    if (script && scriptId === 'custom') {
      setCustomScript(script.script)
    }
  }

  const handleInitiateCall = async () => {
    // Validate phone number
    const cleanedPhone = phoneNumber.replace(/\D/g, '')
    if (!cleanedPhone || cleanedPhone.length < 10) {
      setError('请输入有效的电话号码')
      return
    }

    setLoading(true)
    setError('')

    try {
      const selectedScriptData = predefinedScripts.find(s => s.id === selectedScript)
      const scriptToUse = selectedScript === 'custom' ? customScript : selectedScriptData.script

      const response = await axios.post('/api/calls/initiate', {
        phoneNumber: `+86${cleanedPhone}`,
        script: scriptToUse,
        metadata: {
          ...metadata,
          scriptType: selectedScript,
          initiatedBy: localStorage.getItem('agent')
        }
      })

      if (response.data.success) {
        toast.success('呼叫已发起')
        navigate(`/call/${response.data.callId}`)
      }
    } catch (error) {
      console.error('Failed to initiate call:', error)
      setError(error.response?.data?.error || '发起呼叫失败')
      toast.error('发起呼叫失败')
    } finally {
      setLoading(false)
    }
  }

  const currentScript = selectedScript === 'custom' 
    ? customScript 
    : predefinedScripts.find(s => s.id === selectedScript)?.script

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        发起新呼叫
      </Typography>

      <Grid container spacing={3}>
        {/* Call Configuration */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
                <PhoneIcon color="primary" sx={{ mr: 1 }} />
                <Typography variant="h6">
                  呼叫配置
                </Typography>
              </Box>

              {error && (
                <Alert severity="error" sx={{ mb: 2 }}>
                  {error}
                </Alert>
              )}

              <TextField
                fullWidth
                label="电话号码"
                value={phoneNumber}
                onChange={(e) => setPhoneNumber(e.target.value)}
                placeholder="请输入手机号码"
                margin="normal"
                required
                helperText="请输入11位手机号码"
              />

              <FormControl fullWidth margin="normal">
                <InputLabel>选择脚本模板</InputLabel>
                <Select
                  value={selectedScript}
                  onChange={(e) => handleScriptChange(e.target.value)}
                  label="选择脚本模板"
                >
                  {predefinedScripts.map(script => (
                    <MenuItem key={script.id} value={script.id}>
                      <Box>
                        <Typography>{script.name}</Typography>
                        <Typography variant="caption" color="textSecondary">
                          {script.description}
                        </Typography>
                      </Box>
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>

              <Divider sx={{ my: 3 }} />

              <Typography variant="subtitle1" gutterBottom>
                附加信息（选填）
              </Typography>

              <TextField
                fullWidth
                label="客户姓名"
                value={metadata.customerName}
                onChange={(e) => setMetadata({ ...metadata, customerName: e.target.value })}
                margin="normal"
                size="small"
              />

              <TextField
                fullWidth
                label="订单号"
                value={metadata.orderNumber}
                onChange={(e) => setMetadata({ ...metadata, orderNumber: e.target.value })}
                margin="normal"
                size="small"
              />

              <TextField
                fullWidth
                label="备注"
                value={metadata.notes}
                onChange={(e) => setMetadata({ ...metadata, notes: e.target.value })}
                margin="normal"
                size="small"
                multiline
                rows={2}
              />

              <Box sx={{ mt: 3, display: 'flex', gap: 2 }}>
                <Button
                  fullWidth
                  variant="contained"
                  startIcon={<SendIcon />}
                  onClick={handleInitiateCall}
                  disabled={loading || !phoneNumber}
                >
                  {loading ? '发起中...' : '发起呼叫'}
                </Button>
                <Button
                  fullWidth
                  variant="outlined"
                  onClick={() => navigate('/')}
                >
                  取消
                </Button>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* Script Preview */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
                <ScriptIcon color="primary" sx={{ mr: 1 }} />
                <Typography variant="h6">
                  脚本预览
                </Typography>
                <Box sx={{ ml: 'auto' }}>
                  <Chip
                    icon={<AIIcon />}
                    label="AI 自动应答"
                    color="primary"
                    variant="outlined"
                    size="small"
                  />
                </Box>
              </Box>

              {selectedScript === 'custom' ? (
                <Box>
                  <TextField
                    fullWidth
                    label="开场白"
                    value={customScript.greeting}
                    onChange={(e) => setCustomScript({ ...customScript, greeting: e.target.value })}
                    margin="normal"
                    multiline
                    rows={2}
                    placeholder="输入AI开场白..."
                  />

                  <TextField
                    fullWidth
                    label="默认回复"
                    value={customScript.default}
                    onChange={(e) => setCustomScript({ ...customScript, default: e.target.value })}
                    margin="normal"
                    multiline
                    rows={2}
                    placeholder="输入AI默认回复..."
                  />

                  <TextField
                    fullWidth
                    label="结束语"
                    value={customScript.farewell}
                    onChange={(e) => setCustomScript({ ...customScript, farewell: e.target.value })}
                    margin="normal"
                    multiline
                    rows={2}
                    placeholder="输入AI结束语..."
                  />
                </Box>
              ) : (
                <Box>
                  <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
                    <Typography variant="subtitle2" color="primary" gutterBottom>
                      开场白
                    </Typography>
                    <Typography variant="body2">
                      {currentScript?.greeting || '未设置'}
                    </Typography>
                  </Paper>

                  <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
                    <Typography variant="subtitle2" color="primary" gutterBottom>
                      默认回复
                    </Typography>
                    <Typography variant="body2">
                      {currentScript?.default || '未设置'}
                    </Typography>
                  </Paper>

                  <Paper variant="outlined" sx={{ p: 2 }}>
                    <Typography variant="subtitle2" color="primary" gutterBottom>
                      结束语
                    </Typography>
                    <Typography variant="body2">
                      {currentScript?.farewell || '未设置'}
                    </Typography>
                  </Paper>
                </Box>
              )}

              <Alert severity="info" sx={{ mt: 3 }}>
                AI 将根据设定的脚本自动与客户对话。坐席可以随时接入并接管通话。
              </Alert>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  )
}

export default NewCall