import React from 'react';
import { Box, Typography, Paper } from '@mui/material';

const Settings: React.FC = () => {
  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        系统设置
      </Typography>
      <Paper sx={{ p: 3 }}>
        <Typography>系统设置功能开发中...</Typography>
      </Paper>
    </Box>
  );
};

export default Settings;