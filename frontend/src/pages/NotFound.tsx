import React from 'react';
import { Typography, Button, Container, Card, CardContent } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import HomeOutlinedIcon from '@mui/icons-material/HomeOutlined';
import SearchOffIcon from '@mui/icons-material/SearchOff';

export const NotFound: React.FC = () => {
  const navigate = useNavigate();

  return (
    <Container maxWidth="sm" sx={{ py: 12 }}>
      <Card
        sx={{
          textAlign: 'center',
          p: 4,
          border: '1px solid #EAE6DF',
          borderTop: '4px solid #B8976C',
        }}
      >
        <CardContent>
          <SearchOffIcon sx={{ fontSize: 56, color: '#B8976C', mb: 2 }} />
          <Typography
            variant="h3"
            sx={{
              fontFamily: '"Playfair Display", Georgia, serif',
              fontWeight: 700,
              color: '#16181B',
              mb: 1,
            }}
          >
            404
          </Typography>
          <Typography
            variant="h6"
            sx={{
              fontFamily: '"Playfair Display", Georgia, serif',
              fontWeight: 600,
              color: '#16181B',
              mb: 1,
            }}
          >
            Page Not Found
          </Typography>
          <Typography variant="body2" sx={{ color: '#6B7280', mb: 4 }}>
            The requested destination is either unavailable or requires elevated authorization.
          </Typography>
          <Button
            variant="contained"
            startIcon={<HomeOutlinedIcon />}
            onClick={() => navigate('/dashboard')}
            sx={{
              backgroundColor: '#111315',
              '&:hover': { backgroundColor: '#23272C' },
            }}
          >
            Return to Dashboard
          </Button>
        </CardContent>
      </Card>
    </Container>
  );
};

export default NotFound;
