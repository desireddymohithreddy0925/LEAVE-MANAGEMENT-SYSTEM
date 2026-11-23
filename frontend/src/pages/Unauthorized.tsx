import React from 'react';
import { Box, Card, CardContent, Typography, Button, Stack, Chip } from '@mui/material';
import LockPersonOutlinedIcon from '@mui/icons-material/LockPersonOutlined';
import ArrowBackOutlinedIcon from '@mui/icons-material/ArrowBackOutlined';
import LogoutOutlinedIcon from '@mui/icons-material/LogoutOutlined';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

interface UnauthorizedProps {
  allowedRoles?: string[];
}

export const Unauthorized: React.FC<UnauthorizedProps> = ({ allowedRoles = [] }) => {
  const navigate = useNavigate();
  const { roles, logout } = useAuth();

  const handleLogout = async () => {
    await logout();
    navigate('/login?unauthorized=true');
  };

  return (
    <Box
      sx={{
        minHeight: '75vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        p: 3,
      }}
    >
      <Card
        sx={{
          maxWidth: 520,
          width: '100%',
          textAlign: 'center',
          p: { xs: 2, sm: 4 },
          backgroundColor: '#FFFFFF',
          border: '1px solid #EAE6DF',
          boxShadow: '0 8px 24px rgba(17, 19, 21, 0.06)',
          borderRadius: 2,
        }}
      >
        <CardContent>
          <Box
            sx={{
              width: 64,
              height: 64,
              borderRadius: '50%',
              backgroundColor: '#FAF0F0',
              border: '1px solid #ECCACA',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#8C2D2D',
              mb: 2,
            }}
          >
            <LockPersonOutlinedIcon sx={{ fontSize: 32 }} />
          </Box>

          <Typography
            variant="caption"
            sx={{
              display: 'block',
              color: '#8C2D2D',
              fontWeight: 700,
              letterSpacing: '0.12em',
              textTransform: 'uppercase',
              fontSize: '0.75rem',
              mb: 0.5,
            }}
          >
            HTTP 403 Forbidden
          </Typography>

          <Typography
            variant="h5"
            sx={{
              fontFamily: '"Playfair Display", Georgia, serif',
              fontWeight: 600,
              color: '#16181B',
              mb: 1.5,
            }}
          >
            Access Authorization Restricted
          </Typography>

          <Typography variant="body2" sx={{ color: '#4B5563', mb: 3, lineHeight: 1.6 }}>
            You do not have the necessary role or permissions to access this module. If you believe this is in error, please contact your department administrator.
          </Typography>

          {/* Role details */}
          <Box
            sx={{
              backgroundColor: '#FAF9F6',
              border: '1px solid #EAE6DF',
              borderRadius: 1.5,
              p: 2,
              mb: 3,
              textAlign: 'left',
            }}
          >
            <Box sx={{ mb: 1.5 }}>
              <Typography variant="caption" sx={{ color: '#6B7280', display: 'block', fontWeight: 600 }}>
                YOUR CURRENT ACCOUNT ROLES:
              </Typography>
              <Stack direction="row" spacing={0.75} sx={{ mt: 0.5, flexWrap: 'wrap', gap: 0.5 }}>
                {roles.length > 0 ? (
                  roles.map((r) => (
                    <Chip
                      key={r}
                      label={r}
                      size="small"
                      sx={{
                        backgroundColor: '#111315',
                        color: '#FAF9F6',
                        fontSize: '0.725rem',
                        fontWeight: 600,
                      }}
                    />
                  ))
                ) : (
                  <Typography variant="caption" sx={{ color: '#9CA3AF' }}>
                    None assigned
                  </Typography>
                )}
              </Stack>
            </Box>

            {allowedRoles.length > 0 && (
              <Box>
                <Typography variant="caption" sx={{ color: '#6B7280', display: 'block', fontWeight: 600 }}>
                  REQUIRED ROLES FOR THIS MODULE:
                </Typography>
                <Stack direction="row" spacing={0.75} sx={{ mt: 0.5, flexWrap: 'wrap', gap: 0.5 }}>
                  {allowedRoles.map((r) => (
                    <Chip
                      key={r}
                      label={r}
                      size="small"
                      variant="outlined"
                      sx={{
                        borderColor: '#B8976C',
                        color: '#8C6E45',
                        fontSize: '0.725rem',
                        fontWeight: 600,
                        backgroundColor: 'rgba(184, 151, 108, 0.08)',
                      }}
                    />
                  ))}
                </Stack>
              </Box>
            )}
          </Box>

          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} justifyContent="center">
            <Button
              variant="contained"
              startIcon={<ArrowBackOutlinedIcon />}
              onClick={() => navigate('/dashboard')}
              sx={{
                backgroundColor: '#111315',
                color: '#FAF9F6',
                py: 1,
                px: 2.5,
                fontWeight: 600,
                fontSize: '0.85rem',
                '&:hover': { backgroundColor: '#23272C' },
              }}
            >
              Back to Overview
            </Button>
            <Button
              variant="outlined"
              startIcon={<LogoutOutlinedIcon />}
              onClick={handleLogout}
              sx={{
                borderColor: '#D4CEBE',
                color: '#16181B',
                py: 1,
                px: 2.5,
                fontWeight: 600,
                fontSize: '0.85rem',
                '&:hover': {
                  borderColor: '#B8976C',
                  backgroundColor: 'rgba(184, 151, 108, 0.06)',
                },
              }}
            >
              Sign In with Other Role
            </Button>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
};

export default Unauthorized;
