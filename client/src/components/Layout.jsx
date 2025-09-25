import React, { useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import {
  AppBar,
  Box,
  Drawer,
  IconButton,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Toolbar,
  Typography,
  Avatar,
  Menu,
  MenuItem,
  Chip,
  Divider,
  Badge
} from '@mui/material'
import {
  Menu as MenuIcon,
  Dashboard as DashboardIcon,
  Phone as PhoneIcon,
  History as HistoryIcon,
  AddIcCall as AddCallIcon,
  Person as PersonIcon,
  Logout as LogoutIcon,
  Circle as CircleIcon
} from '@mui/icons-material'
import { useAuthStore } from '../stores/authStore'
import { useSocket } from '../contexts/SocketContext'

const drawerWidth = 240

const menuItems = [
  { text: '监控面板', icon: <DashboardIcon />, path: '/' },
  { text: '发起呼叫', icon: <AddCallIcon />, path: '/new-call' },
  { text: '通话记录', icon: <HistoryIcon />, path: '/history' },
]

function Layout({ children }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { agent, logout } = useAuthStore()
  const { connected, activeCalls } = useSocket()
  const [mobileOpen, setMobileOpen] = useState(false)
  const [anchorEl, setAnchorEl] = useState(null)

  const handleDrawerToggle = () => {
    setMobileOpen(!mobileOpen)
  }

  const handleProfileMenuOpen = (event) => {
    setAnchorEl(event.currentTarget)
  }

  const handleProfileMenuClose = () => {
    setAnchorEl(null)
  }

  const handleLogout = async () => {
    await logout()
    navigate('/login')
  }

  const getStatusColor = (status) => {
    switch (status) {
      case 'available':
        return 'success'
      case 'busy':
        return 'warning'
      case 'break':
        return 'info'
      case 'offline':
      default:
        return 'default'
    }
  }

  const getStatusText = (status) => {
    switch (status) {
      case 'available':
        return '在线'
      case 'busy':
        return '忙碌'
      case 'break':
        return '小休'
      case 'offline':
      default:
        return '离线'
    }
  }

  const drawer = (
    <div>
      <Toolbar>
        <PhoneIcon sx={{ mr: 1 }} />
        <Typography variant="h6" noWrap>
          智能外呼系统
        </Typography>
      </Toolbar>
      <Divider />
      <List>
        {menuItems.map((item) => (
          <ListItem key={item.text} disablePadding>
            <ListItemButton
              selected={location.pathname === item.path}
              onClick={() => {
                navigate(item.path)
                setMobileOpen(false)
              }}
            >
              <ListItemIcon>{item.icon}</ListItemIcon>
              <ListItemText primary={item.text} />
            </ListItemButton>
          </ListItem>
        ))}
      </List>
      <Divider />
      <Box sx={{ p: 2 }}>
        <Typography variant="body2" color="text.secondary" gutterBottom>
          系统状态
        </Typography>
        <Box sx={{ display: 'flex', alignItems: 'center', mt: 1 }}>
          <CircleIcon 
            sx={{ 
              fontSize: 12, 
              mr: 1,
              color: connected ? '#4caf50' : '#f44336'
            }} 
          />
          <Typography variant="body2">
            {connected ? '已连接' : '未连接'}
          </Typography>
        </Box>
        <Box sx={{ mt: 1 }}>
          <Typography variant="body2">
            活跃通话: {activeCalls.length}
          </Typography>
        </Box>
      </Box>
    </div>
  )

  return (
    <Box sx={{ display: 'flex' }}>
      <AppBar
        position="fixed"
        sx={{
          width: { sm: `calc(100% - ${drawerWidth}px)` },
          ml: { sm: `${drawerWidth}px` },
        }}
      >
        <Toolbar>
          <IconButton
            color="inherit"
            edge="start"
            onClick={handleDrawerToggle}
            sx={{ mr: 2, display: { sm: 'none' } }}
          >
            <MenuIcon />
          </IconButton>
          
          <Typography variant="h6" noWrap component="div" sx={{ flexGrow: 1 }}>
            {menuItems.find(item => item.path === location.pathname)?.text || '监控面板'}
          </Typography>

          <Badge badgeContent={activeCalls.length} color="error" sx={{ mr: 2 }}>
            <PhoneIcon />
          </Badge>

          {agent && (
            <>
              <Chip
                label={getStatusText(agent.status)}
                color={getStatusColor(agent.status)}
                size="small"
                sx={{ mr: 2 }}
              />
              
              <IconButton
                onClick={handleProfileMenuOpen}
                color="inherit"
              >
                <Avatar sx={{ width: 32, height: 32 }}>
                  {agent.name?.[0] || 'A'}
                </Avatar>
              </IconButton>
              
              <Menu
                anchorEl={anchorEl}
                open={Boolean(anchorEl)}
                onClose={handleProfileMenuClose}
              >
                <MenuItem disabled>
                  <ListItemIcon>
                    <PersonIcon fontSize="small" />
                  </ListItemIcon>
                  <ListItemText>
                    {agent.name} ({agent.id})
                  </ListItemText>
                </MenuItem>
                <Divider />
                <MenuItem onClick={handleLogout}>
                  <ListItemIcon>
                    <LogoutIcon fontSize="small" />
                  </ListItemIcon>
                  <ListItemText>退出登录</ListItemText>
                </MenuItem>
              </Menu>
            </>
          )}
        </Toolbar>
      </AppBar>

      <Box
        component="nav"
        sx={{ width: { sm: drawerWidth }, flexShrink: { sm: 0 } }}
      >
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={handleDrawerToggle}
          ModalProps={{ keepMounted: true }}
          sx={{
            display: { xs: 'block', sm: 'none' },
            '& .MuiDrawer-paper': { 
              boxSizing: 'border-box', 
              width: drawerWidth 
            },
          }}
        >
          {drawer}
        </Drawer>
        <Drawer
          variant="permanent"
          sx={{
            display: { xs: 'none', sm: 'block' },
            '& .MuiDrawer-paper': { 
              boxSizing: 'border-box', 
              width: drawerWidth 
            },
          }}
          open
        >
          {drawer}
        </Drawer>
      </Box>

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: 3,
          width: { sm: `calc(100% - ${drawerWidth}px)` },
          mt: 8
        }}
      >
        {children}
      </Box>
    </Box>
  )
}

export default Layout