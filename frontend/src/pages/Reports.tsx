import React from 'react';
import { Box, Typography, Paper } from '@mui/material';

const Reports: React.FC = () => {
  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        报表分析
      </Typography>
      <Paper sx={{ p: 3 }}>
        <Typography>报表分析功能开发中...</Typography>
      </Paper>
    </Box>
  );
};

export default Reports;