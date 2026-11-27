import React from 'react';
import {
  Drawer,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Toolbar,
  Box,
  Typography,
  Divider,
  Chip,
} from '@mui/material';
import DashboardOutlinedIcon from '@mui/icons-material/DashboardOutlined';
import EventAvailableOutlinedIcon from '@mui/icons-material/EventAvailableOutlined';
import HistoryOutlinedIcon from '@mui/icons-material/HistoryOutlined';
import PersonOutlineOutlinedIcon from '@mui/icons-material/PersonOutlineOutlined';
import LogoutOutlinedIcon from '@mui/icons-material/LogoutOutlined';
import AdminPanelSettingsOutlinedIcon from '@mui/icons-material/AdminPanelSettingsOutlined';
import SupervisorAccountOutlinedIcon from '@mui/icons-material/SupervisorAccountOutlined';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

export const SIDEBAR_WIDTH = 250;

interface SidebarProps {
  mobileOpen: boolean;
  onClose: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ mobileOpen, onClose }) => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, roles, hasAnyRole, logout } = useAuth();

  const canAccessManager = hasAnyRole(['MANAGER', 'ADMIN']);

  const navItems = [
    { text: 'Overview', path: '/dashboard', icon: <DashboardOutlinedIcon /> },
    { text: 'Apply for Leave', path: '/apply-leave', icon: <EventAvailableOutlinedIcon /> },
    { text: 'My Leave History', path: '/my-leaves', icon: <HistoryOutlinedIcon /> },
    { text: 'My Profile', path: '/profile', icon: <PersonOutlineOutlinedIcon /> },
  ];

  const managementItems = canAccessManager
    ? [{ text: 'Manager Dashboard', path: '/manager', icon: <SupervisorAccountOutlinedIcon /> }]
    : [];

  const handleNavigation = (path: string) => {
    navigate(path);
    onClose();
  };

  const handleLogout = async () => {
    await logout();
    navigate('/login');
    onClose();
  };

  const renderNavItem = (item: { text: string; path: string; icon: React.ReactNode }) => {
    const isSelected = location.pathname === item.path;
    return (
      <ListItemButton
        key={item.text}
        onClick={() => handleNavigation(item.path)}
        sx={{
          mb: 0.75,
          borderRadius: '6px',
          position: 'relative',
          backgroundColor: isSelected ? 'rgba(184, 151, 108, 0.12)' : 'transparent',
          color: isSelected ? '#16181B' : '#4B5563',
          fontWeight: isSelected ? 600 : 400,
          borderLeft: isSelected ? '3px solid #B8976C' : '3px solid transparent',
          transition: 'all 0.2s ease',
          '&:hover': {
            backgroundColor: isSelected ? 'rgba(184, 151, 108, 0.16)' : 'rgba(17, 19, 21, 0.04)',
            color: '#16181B',
          },
        }}
      >
        <ListItemIcon
          sx={{
            color: isSelected ? '#B8976C' : '#6B7280',
            minWidth: 40,
          }}
        >
          {item.icon}
        </ListItemIcon>
        <ListItemText
          primary={item.text}
          primaryTypographyProps={{
            fontSize: '0.885rem',
            fontWeight: isSelected ? 600 : 500,
          }}
        />
      </ListItemButton>
    );
  };

  const primaryRole = roles[0] || 'EMPLOYEE';

  const drawerContent = (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        backgroundColor: '#FAF9F6', // Porcelain alabaster
        borderRight: '1px solid #EAE6DF',
      }}
    >
      {/* Brand Header */}
      <Toolbar sx={{ px: 3, minHeight: '64px !important' }}>
        <Typography
          variant="subtitle2"
          sx={{
            fontWeight: 700,
            color: '#B8976C',
            letterSpacing: '0.14em',
            textTransform: 'uppercase',
            fontSize: '0.725rem',
          }}
        >
          Navigation Menu
        </Typography>
      </Toolbar>

      <Divider sx={{ borderColor: '#EAE6DF' }} />

      {/* Main Navigation List */}
      <List sx={{ px: 1.5, py: 2.5, flexGrow: 1 }}>
        {navItems.map(renderNavItem)}

        {managementItems.length > 0 && (
          <>
            <Divider sx={{ my: 1.5, borderColor: '#EAE6DF' }} />
            <Typography
              variant="caption"
              sx={{
                px: 1.5,
                pb: 0.75,
                display: 'block',
                color: '#B8976C',
                fontWeight: 700,
                fontSize: '0.675rem',
                letterSpacing: '0.12em',
                textTransform: 'uppercase',
              }}
            >
              Management
            </Typography>
            {managementItems.map(renderNavItem)}
          </>
        )}
      </List>

      <Divider sx={{ borderColor: '#EAE6DF' }} />

      {/* Bottom Session Info & Sign Out */}
      <Box sx={{ p: 2 }}>
        <Box
          sx={{
            backgroundColor: '#FFFFFF',
            border: '1px solid #EAE6DF',
            borderRadius: 1.5,
            p: 1.5,
            mb: 1.5,
          }}
        >
          <Typography
            variant="caption"
            sx={{
              color: '#6B7280',
              fontWeight: 600,
              fontSize: '0.675rem',
              letterSpacing: '0.08em',
              display: 'block',
              mb: 0.5,
            }}
          >
            ACTIVE ACCOUNT ROLE
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Chip
              icon={<AdminPanelSettingsOutlinedIcon style={{ fontSize: 14 }} />}
              label={primaryRole}
              size="small"
              sx={{
                backgroundColor: '#111315',
                color: '#FAF9F6',
                fontWeight: 600,
                fontSize: '0.7rem',
                height: 22,
              }}
            />
          </Box>
          <Typography
            variant="caption"
            noWrap
            sx={{
              color: '#4B5563',
              display: 'block',
              mt: 0.75,
              fontSize: '0.725rem',
            }}
          >
            {user?.email || 'Authenticated User'}
          </Typography>
        </Box>

        <ListItemButton
          onClick={handleLogout}
          sx={{
            borderRadius: '6px',
            color: '#8C2D2D',
            '&:hover': {
              backgroundColor: '#FAF0F0',
            },
          }}
        >
          <ListItemIcon sx={{ color: '#8C2D2D', minWidth: 36 }}>
            <LogoutOutlinedIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText
            primary="Sign Out"
            primaryTypographyProps={{
              fontSize: '0.885rem',
              fontWeight: 500,
            }}
          />
        </ListItemButton>
      </Box>
    </Box>
  );

  return (
    <Box
      component="nav"
      sx={{ width: { md: SIDEBAR_WIDTH }, flexShrink: { md: 0 } }}
      aria-label="mailbox folders"
    >
      {/* Mobile Drawer */}
      <Drawer
        variant="temporary"
        open={mobileOpen}
        onClose={onClose}
        ModalProps={{ keepMounted: true }}
        sx={{
          display: { xs: 'block', md: 'none' },
          '& .MuiDrawer-paper': {
            boxSizing: 'border-box',
            width: SIDEBAR_WIDTH,
          },
        }}
      >
        {drawerContent}
      </Drawer>

      {/* Desktop Persistent Drawer */}
      <Drawer
        variant="permanent"
        sx={{
          display: { xs: 'none', md: 'block' },
          '& .MuiDrawer-paper': {
            boxSizing: 'border-box',
            width: SIDEBAR_WIDTH,
          },
        }}
        open
      >
        {drawerContent}
      </Drawer>
    </Box>
  );
};

export default Sidebar;
