import React, { useState, useEffect } from 'react';
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
  Chip,
  Stack,
} from '@mui/material';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import EmailOutlinedIcon from '@mui/icons-material/EmailOutlined';
import DateRangeOutlinedIcon from '@mui/icons-material/DateRangeOutlined';
import AdminPanelSettingsOutlinedIcon from '@mui/icons-material/AdminPanelSettingsOutlined';
import BadgeOutlinedIcon from '@mui/icons-material/BadgeOutlined';
import SupervisorAccountOutlinedIcon from '@mui/icons-material/SupervisorAccountOutlined';
import PersonOutlineOutlinedIcon from '@mui/icons-material/PersonOutlineOutlined';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useNotification } from '../context/NotificationContext';

export const Login: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, isAuthenticated } = useAuth();
  const { showSuccess } = useNotification();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);

  // Field validation states
  const [emailError, setEmailError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [touched, setTouched] = useState({ email: false, password: false });
  const [authError, setAuthError] = useState<string | null>(null);
  const [sessionNotice, setSessionNotice] = useState<string | null>(null);

  // Destination after login
  const from = (location.state as { from?: { pathname?: string } })?.from?.pathname || '/dashboard';

  // Read URL query parameters for session expiration or unauthorized redirection
  useEffect(() => {
    const params = new URLSearchParams(location.search);
    if (params.get('expired') === 'true') {
      setSessionNotice('Your session has expired for security reasons. Please sign in again to continue.');
    } else if (params.get('unauthorized') === 'true') {
      setSessionNotice('Access Denied: You do not have permission to view that module. Please sign in with an authorized account.');
    }
  }, [location.search]);

  // If already authenticated and visiting /login directly, redirect to dashboard
  useEffect(() => {
    if (isAuthenticated) {
      navigate('/dashboard', { replace: true });
    }
  }, [isAuthenticated, navigate]);

  // Client-side validation logic
  const validateEmail = (val: string): string | null => {
    if (!val || !val.trim()) {
      return 'Email address is required.';
    }
    const emailRegex = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
    if (!emailRegex.test(val.trim())) {
      return 'Please enter a valid email address (e.g., name@company.com).';
    }
    return null;
  };

  const validatePassword = (val: string): string | null => {
    if (!val) {
      return 'Password is required.';
    }
    if (val.length < 6) {
      return 'Password must be at least 6 characters in length.';
    }
    return null;
  };

  const handleEmailChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value;
    setEmail(val);
    if (touched.email) {
      setEmailError(validateEmail(val));
    }
    if (authError) setAuthError(null);
  };

  const handlePasswordChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value;
    setPassword(val);
    if (touched.password) {
      setPasswordError(validatePassword(val));
    }
    if (authError) setAuthError(null);
  };

  const handleBlur = (field: 'email' | 'password') => {
    setTouched((prev) => ({ ...prev, [field]: true }));
    if (field === 'email') {
      setEmailError(validateEmail(email));
    } else if (field === 'password') {
      setPasswordError(validatePassword(password));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    // Mark fields as touched
    setTouched({ email: true, password: true });
    const eErr = validateEmail(email);
    const pErr = validatePassword(password);
    setEmailError(eErr);
    setPasswordError(pErr);

    if (eErr || pErr) {
      return;
    }

    setLoading(true);
    setAuthError(null);
    setSessionNotice(null);

    try {
      await login(email.trim(), password);
      showSuccess('Authentication successful. Welcome to the portal.');
      navigate(from, { replace: true });
    } catch (err: unknown) {
      const axiosError = err as {
        response?: {
          status?: number;
          data?: { message?: string; error?: string };
        };
      };

      const status = axiosError.response?.status;
      const serverMsg = axiosError.response?.data?.message || axiosError.response?.data?.error;

      if (status === 401) {
        setAuthError(serverMsg || 'Invalid email address or password. Please verify your credentials.');
      } else if (status === 403) {
        setAuthError(
          serverMsg || 'This account is locked or disabled. Please contact your system administrator.'
        );
      } else if (status === 429) {
        setAuthError('Too many login attempts. Please wait a few minutes before trying again.');
      } else {
        setAuthError(
          serverMsg || 'Unable to connect to authentication services. Please check your network and retry.'
        );
      }
    } finally {
      setLoading(false);
    }
  };

  // Demo credential helper
  const handleSelectRoleDemo = (roleEmail: string, rolePass: string) => {
    setEmail(roleEmail);
    setPassword(rolePass);
    setEmailError(null);
    setPasswordError(null);
    setAuthError(null);
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: '#FAF9F6', // Porcelain alabaster canvas
        px: 2,
        py: 5,
      }}
    >
      <Card
        sx={{
          width: '100%',
          maxWidth: 460,
          borderRadius: 2,
          backgroundColor: '#FFFFFF',
          border: '1px solid #EAE6DF',
          boxShadow: '0 12px 32px rgba(17, 19, 21, 0.07)',
          overflow: 'hidden',
        }}
      >
        {/* Executive Header Banner */}
        <Box
          sx={{
            backgroundColor: '#0D0F12', // Obsidian black
            py: 3.5,
            px: 3,
            textAlign: 'center',
            borderBottom: '1px solid rgba(197, 168, 128, 0.25)',
          }}
        >
          {/* Calendar Portal Icon */}
          <Box
            sx={{
              width: 48,
              height: 48,
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
            <DateRangeOutlinedIcon sx={{ fontSize: 26, color: '#C5A880' }} />
          </Box>
          <Typography
            variant="h5"
            sx={{
              fontFamily: '"Playfair Display", Georgia, serif',
              fontWeight: 600,
              color: '#FAF9F6',
              letterSpacing: '0.01em',
              lineHeight: 1.25,
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
            Enterprise Portal · Secure Access
          </Typography>
        </Box>

        <CardContent sx={{ p: { xs: 3, sm: 4 } }}>
          <Box sx={{ mb: 3, textAlign: 'center' }}>
            <Typography
              variant="subtitle2"
              sx={{ color: '#B8976C', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
            >
              AUTHENTICATION & AUTHORIZATION
            </Typography>
            <Typography
              variant="h6"
              sx={{
                fontFamily: '"Playfair Display", Georgia, serif',
                fontWeight: 600,
                color: '#16181B',
                mt: 0.5,
              }}
            >
              Sign In to Your Workspace
            </Typography>
          </Box>

          {/* Session Expiration or Authorization Notice */}
          {sessionNotice && (
            <Alert
              severity="warning"
              icon={<InfoOutlinedIcon fontSize="small" />}
              sx={{
                mb: 2.5,
                backgroundColor: '#FDF8EB',
                color: '#8C6B1C',
                border: '1px solid #F3E4BF',
                fontSize: '0.85rem',
                lineHeight: 1.4,
              }}
            >
              {sessionNotice}
            </Alert>
          )}

          {/* Authentication Error Alert */}
          {authError && (
            <Alert
              severity="error"
              sx={{
                mb: 2.5,
                backgroundColor: '#FAF0F0',
                color: '#8C2D2D',
                border: '1px solid #ECCACA',
                fontSize: '0.85rem',
                lineHeight: 1.4,
              }}
            >
              {authError}
            </Alert>
          )}

          <Box component="form" onSubmit={handleSubmit} noValidate>
            {/* Email Field */}
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
              onChange={handleEmailChange}
              onBlur={() => handleBlur('email')}
              error={!!emailError}
              helperText={emailError}
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

            {/* Password Field */}
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
              onChange={handlePasswordChange}
              onBlur={() => handleBlur('password')}
              error={!!passwordError}
              helperText={passwordError}
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

            {/* Submit Button */}
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
                transition: 'all 0.2s ease',
                '&:hover': {
                  backgroundColor: '#23272C',
                },
              }}
            >
              {loading ? <CircularProgress size={22} color="inherit" /> : 'Sign In Securely'}
            </Button>

            {/* Role Quick Selector */}
            <Divider sx={{ my: 3, borderColor: '#EAE6DF' }}>
              <Typography variant="caption" sx={{ color: '#9CA3AF', px: 1, letterSpacing: '0.08em' }}>
                ROLE DEMO PREVIEWS
              </Typography>
            </Divider>

            <Typography
              variant="caption"
              sx={{
                color: '#6B7280',
                display: 'block',
                mb: 1.5,
                textAlign: 'center',
                fontSize: '0.75rem',
              }}
            >
              Select an account role below to auto-populate credentials:
            </Typography>

            <Stack spacing={1}>
              {/* ADMIN Role */}
              <Chip
                icon={<AdminPanelSettingsOutlinedIcon style={{ fontSize: 18 }} />}
                label="Administrator (ADMIN) · admin@leavemanagement.com"
                onClick={() => handleSelectRoleDemo('admin@leavemanagement.com', 'Admin@123')}
                variant="outlined"
                clickable
                sx={{
                  justifyContent: 'flex-start',
                  px: 1,
                  py: 1.75,
                  fontSize: '0.775rem',
                  borderColor: '#D4CEBE',
                  color: '#16181B',
                  backgroundColor: 'rgba(184, 151, 108, 0.04)',
                  '&:hover': {
                    borderColor: '#B8976C',
                    backgroundColor: 'rgba(184, 151, 108, 0.12)',
                  },
                }}
              />

              {/* HR Role */}
              <Chip
                icon={<BadgeOutlinedIcon style={{ fontSize: 18 }} />}
                label="HR Manager (HR) · hr@leavemanagement.com"
                onClick={() => handleSelectRoleDemo('hr@leavemanagement.com', 'Hr@123')}
                variant="outlined"
                clickable
                sx={{
                  justifyContent: 'flex-start',
                  px: 1,
                  py: 1.75,
                  fontSize: '0.775rem',
                  borderColor: '#D4CEBE',
                  color: '#16181B',
                  backgroundColor: 'rgba(184, 151, 108, 0.04)',
                  '&:hover': {
                    borderColor: '#B8976C',
                    backgroundColor: 'rgba(184, 151, 108, 0.12)',
                  },
                }}
              />

              {/* MANAGER Role */}
              <Chip
                icon={<SupervisorAccountOutlinedIcon style={{ fontSize: 18 }} />}
                label="Team Manager (MANAGER) · manager@leavemanagement.com"
                onClick={() => handleSelectRoleDemo('manager@leavemanagement.com', 'Manager@123')}
                variant="outlined"
                clickable
                sx={{
                  justifyContent: 'flex-start',
                  px: 1,
                  py: 1.75,
                  fontSize: '0.775rem',
                  borderColor: '#D4CEBE',
                  color: '#16181B',
                  backgroundColor: 'rgba(184, 151, 108, 0.04)',
                  '&:hover': {
                    borderColor: '#B8976C',
                    backgroundColor: 'rgba(184, 151, 108, 0.12)',
                  },
                }}
              />

              {/* EMPLOYEE Role */}
              <Chip
                icon={<PersonOutlineOutlinedIcon style={{ fontSize: 18 }} />}
                label="Employee (EMPLOYEE) · employee@leavemanagement.com"
                onClick={() => handleSelectRoleDemo('employee@leavemanagement.com', 'Employee@123')}
                variant="outlined"
                clickable
                sx={{
                  justifyContent: 'flex-start',
                  px: 1,
                  py: 1.75,
                  fontSize: '0.775rem',
                  borderColor: '#D4CEBE',
                  color: '#16181B',
                  backgroundColor: 'rgba(184, 151, 108, 0.04)',
                  '&:hover': {
                    borderColor: '#B8976C',
                    backgroundColor: 'rgba(184, 151, 108, 0.12)',
                  },
                }}
              />
            </Stack>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
};

export default Login;
