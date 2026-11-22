import React, { ReactNode } from 'react';
import { Box, Typography } from '@mui/material';

interface PageHeaderProps {
  title: string;
  subtitle?: string;
  category?: string;
  action?: ReactNode;
}

export const PageHeader: React.FC<PageHeaderProps> = ({
  title,
  subtitle,
  category = 'CAPABILITIES',
  action,
}) => {
  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: { xs: 'column', sm: 'row' },
        justifyContent: 'space-between',
        alignItems: { xs: 'flex-start', sm: 'flex-end' },
        mb: 3.5,
        gap: 2,
        pb: 1.5,
        borderBottom: '1px solid #EAE6DF',
      }}
    >
      <Box>
        {/* Category Overline */}
        <Typography
          variant="subtitle2"
          sx={{
            color: '#B8976C',
            fontWeight: 600,
            letterSpacing: '0.14em',
            textTransform: 'uppercase',
            fontSize: '0.725rem',
            mb: 0.5,
          }}
        >
          {category}
        </Typography>

        {/* Editorial Serif Heading */}
        <Typography
          variant="h4"
          sx={{
            fontFamily: '"Playfair Display", "Cinzel", "Georgia", serif',
            fontWeight: 600,
            color: '#16181B',
            letterSpacing: '-0.01em',
          }}
        >
          {title}
        </Typography>

        {/* Decorative Champagne Gold Line */}
        <Box
          sx={{
            width: 32,
            height: 2,
            backgroundColor: '#B8976C',
            mt: 1,
            mb: 0.75,
            borderRadius: 1,
          }}
        />

        {subtitle && (
          <Typography variant="body2" sx={{ color: '#6B7280', maxWidth: 640 }}>
            {subtitle}
          </Typography>
        )}
      </Box>

      {action && <Box sx={{ flexShrink: 0 }}>{action}</Box>}
    </Box>
  );
};

export default PageHeader;
