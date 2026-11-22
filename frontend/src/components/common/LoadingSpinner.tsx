import React from 'react';
import { Box, CircularProgress, Typography } from '@mui/material';

interface LoadingSpinnerProps {
  message?: string;
  minHeight?: number | string;
}

export const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({
  message = 'Loading...',
  minHeight = '200px',
}) => {
  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight,
        gap: 2,
        width: '100%',
      }}
    >
      <CircularProgress
        size={36}
        thickness={4}
        sx={{
          color: '#6C7A89', // Muted slate spinner
        }}
      />
      {message && (
        <Typography variant="body2" sx={{ color: '#718096', fontWeight: 500 }}>
          {message}
        </Typography>
      )}
    </Box>
  );
};

export default LoadingSpinner;
