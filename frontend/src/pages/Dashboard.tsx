import React, { useState, useEffect } from 'react';
import {
  Box,
  Grid,
  Paper,
  Typography,
  Card,
  CardContent,
  LinearProgress,
  List,
  ListItem,
  ListItemText,
  Chip,
  IconButton,
  Tooltip
} from '@mui/material';
import {
  Phone as PhoneIcon,
  TrendingUp as TrendingUpIcon,
  People as PeopleIcon,
  AccessTime as AccessTimeIcon,
  CheckCircle as CheckCircleIcon,
  Cancel as CancelIcon,
  Refresh as RefreshIcon,
  SmartToy as SmartToyIcon,
  SupervisorAccount as SupervisorAccountIcon
} from '@mui/icons-material';
import { Line, Doughnut, Bar } from 'react-chartjs-2';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  ArcElement,
  Title,
  Tooltip as ChartTooltip,
  Legend
} from 'chart.js';
import axios from 'axios';
import { useCall } from '../contexts/CallContext';

// Register ChartJS components
ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  ArcElement,
  Title,
  ChartTooltip,
  Legend
);

interface Statistics {
  totalCalls: number;
  avgDuration: number;
  successfulCalls: number;
  robotHandledCalls: number;
  agentInterventions: number;
  totalDuration: number;
}

interface CallsByHour {
  _id: number;
  count: number;
}

const Dashboard: React.FC = () => {
  const { activeCalls } = useCall();
  const [statistics, setStatistics] = useState<Statistics>({
    totalCalls: 0,
    avgDuration: 0,
    successfulCalls: 0,
    robotHandledCalls: 0,
    agentInterventions: 0,
    totalDuration: 0
  });
  const [callsByHour, setCallsByHour] = useState<CallsByHour[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    fetchStatistics();
    const interval = setInterval(fetchStatistics, 30000); // Refresh every 30 seconds
    return () => clearInterval(interval);
  }, []);

  const fetchStatistics = async () => {
    try {
      const response = await axios.get('/api/calls/stats/overview');
      setStatistics(response.data.overview);
      setCallsByHour(response.data.byHour);
    } catch (error) {
      console.error('Failed to fetch statistics:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const formatDuration = (seconds: number) => {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;
    return `${hours}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  // Chart data
  const hourlyCallsData = {
    labels: Array.from({ length: 24 }, (_, i) => `${i}:00`),
    datasets: [
      {
        label: '呼叫数量',
        data: Array.from({ length: 24 }, (_, hour) => {
          const found = callsByHour.find(c => c._id === hour);
          return found ? found.count : 0;
        }),
        borderColor: 'rgb(75, 192, 192)',
        backgroundColor: 'rgba(75, 192, 192, 0.2)',
        tension: 0.1
      }
    ]
  };

  const callOutcomeData = {
    labels: ['成功', '机器人处理', '人工介入', '其他'],
    datasets: [
      {
        data: [
          statistics.successfulCalls,
          statistics.robotHandledCalls,
          statistics.agentInterventions,
          Math.max(0, statistics.totalCalls - statistics.successfulCalls - statistics.agentInterventions)
        ],
        backgroundColor: [
          'rgba(75, 192, 192, 0.6)',
          'rgba(54, 162, 235, 0.6)',
          'rgba(255, 206, 86, 0.6)',
          'rgba(255, 99, 132, 0.6)'
        ],
        borderWidth: 1
      }
    ]
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4">仪表盘</Typography>
        <Tooltip title="刷新数据">
          <IconButton onClick={fetchStatistics}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
      </Box>

      {isLoading && <LinearProgress sx={{ mb: 2 }} />}

      {/* 统计卡片 */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <Box>
                  <Typography color="textSecondary" gutterBottom variant="body2">
                    今日呼叫总数
                  </Typography>
                  <Typography variant="h4">
                    {statistics.totalCalls}
                  </Typography>
                  <Typography variant="body2" color="success.main">
                    <TrendingUpIcon fontSize="small" sx={{ verticalAlign: 'middle' }} />
                    {` 活跃通话: ${activeCalls.length}`}
                  </Typography>
                </Box>
                <PhoneIcon sx={{ fontSize: 40, color: 'primary.main' }} />
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <Box>
                  <Typography color="textSecondary" gutterBottom variant="body2">
                    平均通话时长
                  </Typography>
                  <Typography variant="h4">
                    {Math.round(statistics.avgDuration)}s
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    总时长: {formatDuration(statistics.totalDuration)}
                  </Typography>
                </Box>
                <AccessTimeIcon sx={{ fontSize: 40, color: 'info.main' }} />
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <Box>
                  <Typography color="textSecondary" gutterBottom variant="body2">
                    成功率
                  </Typography>
                  <Typography variant="h4">
                    {statistics.totalCalls > 0 
                      ? `${Math.round((statistics.successfulCalls / statistics.totalCalls) * 100)}%`
                      : '0%'}
                  </Typography>
                  <Typography variant="body2" color="success.main">
                    成功: {statistics.successfulCalls}
                  </Typography>
                </Box>
                <CheckCircleIcon sx={{ fontSize: 40, color: 'success.main' }} />
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <Box>
                  <Typography color="textSecondary" gutterBottom variant="body2">
                    人工介入率
                  </Typography>
                  <Typography variant="h4">
                    {statistics.totalCalls > 0 
                      ? `${Math.round((statistics.agentInterventions / statistics.totalCalls) * 100)}%`
                      : '0%'}
                  </Typography>
                  <Typography variant="body2" color="warning.main">
                    介入: {statistics.agentInterventions}
                  </Typography>
                </Box>
                <SupervisorAccountIcon sx={{ fontSize: 40, color: 'warning.main' }} />
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* 图表 */}
      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h6" gutterBottom>
              24小时呼叫趋势
            </Typography>
            <Box sx={{ height: 300 }}>
              <Line 
                data={hourlyCallsData} 
                options={{
                  responsive: true,
                  maintainAspectRatio: false,
                  plugins: {
                    legend: {
                      display: false
                    }
                  },
                  scales: {
                    y: {
                      beginAtZero: true,
                      ticks: {
                        stepSize: 1
                      }
                    }
                  }
                }}
              />
            </Box>
          </Paper>
        </Grid>

        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h6" gutterBottom>
              通话结果分布
            </Typography>
            <Box sx={{ height: 300, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Doughnut 
                data={callOutcomeData}
                options={{
                  responsive: true,
                  maintainAspectRatio: false,
                  plugins: {
                    legend: {
                      position: 'bottom'
                    }
                  }
                }}
              />
            </Box>
          </Paper>
        </Grid>

        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h6" gutterBottom>
              实时通话队列
            </Typography>
            <List sx={{ maxHeight: 300, overflow: 'auto' }}>
              {activeCalls.slice(0, 5).map((call) => (
                <ListItem key={call.id}>
                  <ListItemText
                    primary={call.phoneNumber}
                    secondary={
                      <Box sx={{ display: 'flex', gap: 1, mt: 0.5 }}>
                        <Chip 
                          size="small" 
                          icon={call.isRobotHandling ? <SmartToyIcon /> : <SupervisorAccountIcon />}
                          label={call.isRobotHandling ? '机器人' : '人工'}
                          color={call.isRobotHandling ? 'info' : 'warning'}
                        />
                        <Typography variant="caption" color="text.secondary">
                          {`${call.duration}秒`}
                        </Typography>
                      </Box>
                    }
                  />
                </ListItem>
              ))}
              {activeCalls.length === 0 && (
                <ListItem>
                  <ListItemText 
                    primary="暂无活动通话"
                    secondary="等待新的呼叫..."
                  />
                </ListItem>
              )}
            </List>
          </Paper>
        </Grid>

        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h6" gutterBottom>
              系统状态
            </Typography>
            <List>
              <ListItem>
                <ListItemText
                  primary="机器人服务"
                  secondary="正常运行"
                />
                <Chip label="在线" color="success" size="small" />
              </ListItem>
              <ListItem>
                <ListItemText
                  primary="语音识别服务"
                  secondary="响应时间: 120ms"
                />
                <Chip label="正常" color="success" size="small" />
              </ListItem>
              <ListItem>
                <ListItemText
                  primary="数据库连接"
                  secondary="MongoDB"
                />
                <Chip label="已连接" color="success" size="small" />
              </ListItem>
              <ListItem>
                <ListItemText
                  primary="WebRTC服务"
                  secondary="STUN/TURN服务器"
                />
                <Chip label="可用" color="success" size="small" />
              </ListItem>
            </List>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

export default Dashboard;