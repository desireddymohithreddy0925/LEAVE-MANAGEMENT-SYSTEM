import React, { useState } from 'react';
import {
  AppBar,
  Toolbar,
  Typography,
  IconButton,
  Box,
  Menu,
  MenuItem,
  Chip,
  Avatar,
  Divider,
  ListItemIcon,
} from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import PersonOutlineIcon from '@mui/icons-material/PersonOutline';
import LogoutIcon from '@mui/icons-material/Logout';
import DateRangeOutlinedIcon from '@mui/icons-material/DateRangeOutlined';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

interface HeaderProps {
  onToggleSidebar: () => void;
}

export const Header: React.FC<HeaderProps> = ({ onToggleSidebar }) => {
  const { user, employee, roles, logout } = useAuth();
  const navigate = useNavigate();
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

  const handleOpenMenu = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleCloseMenu = () => {
    setAnchorEl(null);
  };

  const handleNavigateProfile = () => {
    handleCloseMenu();
    navigate('/profile');
  };

  const handleLogout = async () => {
    handleCloseMenu();
    await logout();
    navigate('/login');
  };

  // Resolve display name and role
  const displayName = employee
    ? `${employee.firstName} ${employee.lastName}`
    : user?.email?.split('@')[0] || 'User';

  const primaryRole = roles[0] || user?.roles?.[0]?.name?.replace('ROLE_', '') || 'EMPLOYEE';

  return (
    <AppBar
      position="sticky"
      sx={{
        backgroundColor: '#0D0F12', // Obsidian black header
        color: '#FAF9F6',
        boxShadow: 'none',
        borderBottom: '1px solid rgba(197, 168, 128, 0.2)', // Delicate champagne hairline
        zIndex: (theme) => theme.zIndex.drawer + 1,
      }}
    >
      <Toolbar sx={{ justifyContent: 'space-between', px: { xs: 2, sm: 3 }, minHeight: '64px' }}>
        {/* Left: Mobile hamburger & Brand Crest */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <IconButton
            color="inherit"
            edge="start"
            onClick={onToggleSidebar}
            sx={{ display: { md: 'none' }, color: '#FAF9F6' }}
            aria-label="open drawer"
          >
            <MenuIcon />
          </IconButton>

          {/* Brand Header */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <Box
              sx={{
                width: 36,
                height: 36,
                borderRadius: '8px',
                border: '1px solid rgba(197, 168, 128, 0.35)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                backgroundColor: 'rgba(197, 168, 128, 0.08)',
              }}
            >
              <DateRangeOutlinedIcon sx={{ color: '#C5A880', fontSize: 20 }} />
            </Box>
            <Box>
              <Typography
                variant="h6"
                sx={{
                  fontFamily: '"Playfair Display", "Georgia", serif',
                  fontWeight: 600,
                  fontSize: { xs: '0.95rem', sm: '1.1rem' },
                  color: '#FAF9F6',
                  letterSpacing: '0.01em',
                  lineHeight: 1.15,
                }}
              >
                Leave Management System
              </Typography>
              <Typography
                variant="caption"
                sx={{
                  color: '#C5A880',
                  fontSize: '0.675rem',
                  letterSpacing: '0.12em',
                  textTransform: 'uppercase',
                  fontWeight: 500,
                  display: 'block',
                }}
              >
                Enterprise Portal
              </Typography>
            </Box>
          </Box>
        </Box>

        {/* Right: User identification & dropdown menu */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: { xs: 1, sm: 2 } }}>
          {/* Executive Role Badge */}
          <Chip
            size="small"
            label={primaryRole}
            variant="outlined"
            sx={{
              display: { xs: 'none', sm: 'inline-flex' },
              borderColor: 'rgba(197, 168, 128, 0.45)',
              color: '#C5A880',
              fontWeight: 500,
              fontSize: '0.72rem',
              letterSpacing: '0.08em',
              textTransform: 'uppercase',
              backgroundColor: 'rgba(197, 168, 128, 0.05)',
            }}
          />

          {/* User trigger button */}
          <Box
            onClick={handleOpenMenu}
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1.25,
              cursor: 'pointer',
              py: 0.5,
              px: 1,
              borderRadius: 2,
              transition: 'background-color 0.2s',
              '&:hover': {
                backgroundColor: 'rgba(255, 255, 255, 0.06)',
              },
            }}
          >
            <Avatar
              sx={{
                width: 34,
                height: 34,
                bgcolor: 'transparent',
                border: '1.5px solid #C5A880',
                color: '#C5A880',
                fontSize: '0.875rem',
                fontWeight: 600,
                fontFamily: '"Playfair Display", Georgia, serif',
              }}
            >
              {displayName.charAt(0).toUpperCase()}
            </Avatar>
            <Box sx={{ display: { xs: 'none', md: 'block' }, textAlign: 'left' }}>
              <Typography variant="body2" sx={{ fontWeight: 500, color: '#FAF9F6', lineHeight: 1.2 }}>
                {displayName}
              </Typography>
              <Typography variant="caption" sx={{ color: '#9CA3AF', fontSize: '0.725rem' }}>
                {employee?.employeeCode || user?.email}
              </Typography>
            </Box>
          </Box>

          {/* Dropdown Menu */}
          <Menu
            anchorEl={anchorEl}
            open={Boolean(anchorEl)}
            onClose={handleCloseMenu}
            transformOrigin={{ horizontal: 'right', vertical: 'top' }}
            anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
            PaperProps={{
              sx: {
                width: 220,
                mt: 1.5,
                borderRadius: 2,
                border: '1px solid #EAE6DF',
                boxShadow: '0 8px 24px rgba(0, 0, 0, 0.12)',
              },
            }}
          >
            <Box sx={{ px: 2, py: 1.5 }}>
              <Typography variant="subtitle2" sx={{ fontWeight: 600, color: '#16181B' }}>
                {displayName}
              </Typography>
              <Typography variant="caption" sx={{ color: '#6B7280', wordBreak: 'break-all' }}>
                {user?.email}
              </Typography>
            </Box>
            <Divider sx={{ my: 0.5, borderColor: '#EAE6DF' }} />
            <MenuItem onClick={handleNavigateProfile} sx={{ py: 1, fontSize: '0.9rem' }}>
              <ListItemIcon>
                <PersonOutlineIcon fontSize="small" sx={{ color: '#B8976C' }} />
              </ListItemIcon>
              My Profile
            </MenuItem>
            <Divider sx={{ my: 0.5, borderColor: '#EAE6DF' }} />
            <MenuItem onClick={handleLogout} sx={{ py: 1, fontSize: '0.9rem', color: '#8C2D2D' }}>
              <ListItemIcon>
                <LogoutIcon fontSize="small" sx={{ color: '#8C2D2D' }} />
              </ListItemIcon>
              Sign Out
            </MenuItem>
          </Menu>
        </Box>
      </Toolbar>
    </AppBar>
  );
};

export default Header;
