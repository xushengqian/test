import React from 'react';
import { Box, Typography, Paper } from '@mui/material';

const Campaigns: React.FC = () => {
  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        活动管理
      </Typography>
      <Paper sx={{ p: 3 }}>
        <Typography>活动管理功能开发中...</Typography>
      </Paper>
    </Box>
  );
};

export default Campaigns;