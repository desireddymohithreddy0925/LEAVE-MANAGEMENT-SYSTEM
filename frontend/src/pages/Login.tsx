import React, { useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  TextField,
  Button,
  Alert,
  InputAdornment,
  IconButton,
  CircularProgress,
  Divider,
} from '@mui/material';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import EmailOutlinedIcon from '@mui/icons-material/EmailOutlined';
import DateRangeOutlinedIcon from '@mui/icons-material/DateRangeOutlined';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useNotification } from '../context/NotificationContext';

export const Login: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();
  const { showSuccess } = useNotification();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const from = (location.state as { from?: { pathname?: string } })?.from?.pathname || '/dashboard';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) {
      setErrorMsg('Please enter both your email and password.');
      return;
    }

    setLoading(true);
    setErrorMsg(null);

    try {
      await login(email, password);
      showSuccess('Signed in successfully');
      navigate(from, { replace: true });
    } catch (err: unknown) {
      const axiosError = err as { response?: { data?: { message?: string } } };
      setErrorMsg(
        axiosError.response?.data?.message ||
          'Invalid credentials. Please verify your email and password.'
      );
    } finally {
      setLoading(false);
    }
  };

  const fillDemoAdmin = () => {
    setEmail('admin@leavemanagement.com');
    setPassword('Admin@123');
    setErrorMsg(null);
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: '#FAF9F6', // Porcelain alabaster
        px: 2,
        py: 4,
      }}
    >
      <Card
        sx={{
          width: '100%',
          maxWidth: 440,
          borderRadius: 2,
          backgroundColor: '#FFFFFF',
          border: '1px solid #EAE6DF',
          boxShadow: '0 12px 32px rgba(17, 19, 21, 0.08)',
          overflow: 'hidden',
        }}
      >
        {/* Executive Header Banner */}
        <Box
          sx={{
            backgroundColor: '#0D0F12',
            py: 3.5,
            px: 3,
            textAlign: 'center',
            borderBottom: '1px solid rgba(197, 168, 128, 0.25)',
          }}
        >
          {/* Calendar Portal Icon */}
          <Box
            sx={{
              width: 46,
              height: 46,
              borderRadius: '10px',
              border: '1px solid rgba(197, 168, 128, 0.35)',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              backgroundColor: 'rgba(197, 168, 128, 0.08)',
              color: '#C5A880',
              mb: 1.5,
            }}
          >
            <DateRangeOutlinedIcon sx={{ fontSize: 24, color: '#C5A880' }} />
          </Box>
          <Typography
            variant="h5"
            sx={{
              fontFamily: '"Playfair Display", Georgia, serif',
              fontWeight: 600,
              color: '#FAF9F6',
              letterSpacing: '0.01em',
              lineHeight: 1.2,
            }}
          >
            Leave Management System
          </Typography>
          <Typography
            variant="caption"
            sx={{
              color: '#C5A880',
              fontSize: '0.725rem',
              letterSpacing: '0.14em',
              textTransform: 'uppercase',
              fontWeight: 500,
              display: 'block',
              mt: 0.5,
            }}
          >
            Enterprise Portal
          </Typography>
        </Box>

        <CardContent sx={{ p: { xs: 3, sm: 4 } }}>
          <Box sx={{ mb: 3, textAlign: 'center' }}>
            <Typography
              variant="subtitle2"
              sx={{ color: '#B8976C', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
            >
              AUTHENTICATION
            </Typography>
            <Typography
              variant="h6"
              sx={{
                fontFamily: '"Playfair Display", Georgia, serif',
                fontWeight: 600,
                color: '#16181B',
              }}
            >
              Sign In to Your Account
            </Typography>
          </Box>

          {errorMsg && (
            <Alert
              severity="error"
              sx={{
                mb: 3,
                backgroundColor: '#FAF0F0',
                color: '#8C2D2D',
                border: '1px solid #ECCACA',
                fontSize: '0.85rem',
              }}
            >
              {errorMsg}
            </Alert>
          )}

          <Box component="form" onSubmit={handleSubmit} noValidate>
            <TextField
              margin="normal"
              required
              fullWidth
              id="email"
              label="Corporate Email Address"
              name="email"
              autoComplete="email"
              autoFocus
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              size="small"
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <EmailOutlinedIcon sx={{ color: '#6B7280', fontSize: 20 }} />
                  </InputAdornment>
                ),
              }}
              sx={{ mb: 2 }}
            />

            <TextField
              margin="normal"
              required
              fullWidth
              name="password"
              label="Password"
              type={showPassword ? 'text' : 'password'}
              id="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              size="small"
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <LockOutlinedIcon sx={{ color: '#6B7280', fontSize: 20 }} />
                  </InputAdornment>
                ),
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton
                      aria-label="toggle password visibility"
                      onClick={() => setShowPassword(!showPassword)}
                      edge="end"
                      size="small"
                    >
                      {showPassword ? <VisibilityOff fontSize="small" /> : <Visibility fontSize="small" />}
                    </IconButton>
                  </InputAdornment>
                ),
              }}
              sx={{ mb: 3 }}
            />

            <Button
              type="submit"
              fullWidth
              variant="contained"
              disabled={loading}
              sx={{
                py: 1.25,
                backgroundColor: '#111315',
                color: '#FAF9F6',
                fontWeight: 600,
                fontSize: '0.9rem',
                letterSpacing: '0.04em',
                border: '1px solid #111315',
                '&:hover': {
                  backgroundColor: '#23272C',
                },
              }}
            >
              {loading ? <CircularProgress size={22} color="inherit" /> : 'Sign In'}
            </Button>

            <Divider sx={{ my: 3, borderColor: '#EAE6DF' }}>
              <Typography variant="caption" sx={{ color: '#9CA3AF', px: 1 }}>
                DEVELOPER DEMO CREDENTIALS
              </Typography>
            </Divider>

            <Button
              fullWidth
              variant="outlined"
              size="small"
              onClick={fillDemoAdmin}
              sx={{
                borderColor: '#D4CEBE',
                color: '#16181B',
                fontSize: '0.8rem',
                py: 0.75,
                '&:hover': {
                  borderColor: '#B8976C',
                  backgroundColor: 'rgba(184, 151, 108, 0.08)',
                },
              }}
            >
              Fill Administrator Credentials
            </Button>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
};

export default Login;
