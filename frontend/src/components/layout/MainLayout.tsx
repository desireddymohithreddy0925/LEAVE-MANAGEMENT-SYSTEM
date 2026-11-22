import React, { useState } from 'react';
import { Box, Container } from '@mui/material';
import { Outlet } from 'react-router-dom';
import Header from './Header';
import Sidebar, { SIDEBAR_WIDTH } from './Sidebar';

export const MainLayout: React.FC = () => {
  const [mobileOpen, setMobileOpen] = useState(false);

  const handleToggleSidebar = () => {
    setMobileOpen((prev) => !prev);
  };

  const handleCloseSidebar = () => {
    setMobileOpen(false);
  };

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', backgroundColor: '#F8F9FA' }}>
      {/* Sidebar Navigation Drawer */}
      <Sidebar mobileOpen={mobileOpen} onClose={handleCloseSidebar} />

      {/* Main Content Area */}
      <Box
        sx={{
          flexGrow: 1,
          display: 'flex',
          flexDirection: 'column',
          width: { md: `calc(100% - ${SIDEBAR_WIDTH}px)` },
          minHeight: '100vh',
        }}
      >
        {/* Top Header */}
        <Header onToggleSidebar={handleToggleSidebar} />

        {/* Dynamic Route View Container */}
        <Box
          component="main"
          sx={{
            flexGrow: 1,
            py: { xs: 2.5, sm: 3.5 },
            px: { xs: 2, sm: 3 },
          }}
        >
          <Container maxWidth="lg" disableGutters>
            <Outlet />
          </Container>
        </Box>
      </Box>
    </Box>
  );
};

export default MainLayout;
