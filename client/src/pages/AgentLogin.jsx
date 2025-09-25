import React, { useState } from 'react'
import {
  Box,
  Card,
  CardContent,
  TextField,
  Button,
  Typography,
  Alert,
  Container,
  InputAdornment,
  IconButton
} from '@mui/material'
import {
  Person as PersonIcon,
  Lock as LockIcon,
  Visibility,
  VisibilityOff,
  Phone as PhoneIcon
} from '@mui/icons-material'
import { useAuthStore } from '../stores/authStore'

function AgentLogin() {
  const [agentId, setAgentId] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const { login } = useAuthStore()

  const handleSubmit = async (e) => {
    e.preventDefault()
    
    if (!agentId || !password) {
      setError('请输入坐席ID和密码')
      return
    }

    setLoading(true)
    setError('')

    const result = await login(agentId, password)
    
    if (!result.success) {
      setError(result.error || '登录失败')
      setLoading(false)
    }
  }

  const handleDemoLogin = (demoAgentId) => {
    setAgentId(demoAgentId)
    setPassword('demo123')
  }

  return (
    <Container maxWidth="sm">
      <Box
        sx={{
          minHeight: '100vh',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          alignItems: 'center'
        }}
      >
        <Card sx={{ width: '100%', maxWidth: 450 }}>
          <CardContent sx={{ p: 4 }}>
            <Box sx={{ textAlign: 'center', mb: 4 }}>
              <PhoneIcon sx={{ fontSize: 48, color: 'primary.main', mb: 2 }} />
              <Typography variant="h4" gutterBottom>
                智能外呼系统
              </Typography>
              <Typography variant="body2" color="textSecondary">
                坐席登录
              </Typography>
            </Box>

            {error && (
              <Alert severity="error" sx={{ mb: 3 }}>
                {error}
              </Alert>
            )}

            <form onSubmit={handleSubmit}>
              <TextField
                fullWidth
                label="坐席ID"
                value={agentId}
                onChange={(e) => setAgentId(e.target.value)}
                margin="normal"
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <PersonIcon />
                    </InputAdornment>
                  ),
                }}
                autoComplete="username"
              />

              <TextField
                fullWidth
                label="密码"
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                margin="normal"
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <LockIcon />
                    </InputAdornment>
                  ),
                  endAdornment: (
                    <InputAdornment position="end">
                      <IconButton
                        onClick={() => setShowPassword(!showPassword)}
                        edge="end"
                      >
                        {showPassword ? <VisibilityOff /> : <Visibility />}
                      </IconButton>
                    </InputAdornment>
                  ),
                }}
                autoComplete="current-password"
              />

              <Button
                fullWidth
                variant="contained"
                type="submit"
                size="large"
                disabled={loading}
                sx={{ mt: 3, mb: 2 }}
              >
                {loading ? '登录中...' : '登录'}
              </Button>
            </form>

            <Box sx={{ mt: 3, pt: 3, borderTop: 1, borderColor: 'divider' }}>
              <Typography variant="body2" color="textSecondary" align="center" gutterBottom>
                演示账号（点击快速填充）
              </Typography>
              <Box sx={{ display: 'flex', gap: 1, justifyContent: 'center', mt: 2 }}>
                <Button
                  size="small"
                  variant="outlined"
                  onClick={() => handleDemoLogin('agent001')}
                >
                  张三
                </Button>
                <Button
                  size="small"
                  variant="outlined"
                  onClick={() => handleDemoLogin('agent002')}
                >
                  李四
                </Button>
                <Button
                  size="small"
                  variant="outlined"
                  onClick={() => handleDemoLogin('agent003')}
                >
                  王五
                </Button>
              </Box>
            </Box>
          </CardContent>
        </Card>

        <Typography variant="body2" color="textSecondary" sx={{ mt: 3 }}>
          © 2024 智能外呼系统. All rights reserved.
        </Typography>
      </Box>
    </Container>
  )
}

export default AgentLogin