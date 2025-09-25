import React from 'react';
import { Box, Typography, Paper } from '@mui/material';

const Customers: React.FC = () => {
  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        客户管理
      </Typography>
      <Paper sx={{ p: 3 }}>
        <Typography>客户管理功能开发中...</Typography>
      </Paper>
    </Box>
  );
};

export default Customers;