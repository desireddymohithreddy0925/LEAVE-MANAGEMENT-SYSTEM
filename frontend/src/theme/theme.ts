import { createTheme } from '@mui/material/styles';

/**
 * Executive Enterprise Palette:
 * Blends obsidian onyx (#0D0F12), warm champagne bronze/gold (#B8976C / #C5A880),
 * and porcelain alabaster surfaces (#FAF9F6, #FFFFFF).
 * Retains intuitive simplicity while elevating the aesthetic to look prestigious,
 * modern, and professional.
 */
export const theme = createTheme({
  palette: {
    mode: 'light',
    background: {
      default: '#FAF9F6', // Porcelain Alabaster background canvas
      paper: '#FFFFFF',   // Pristine white card & modal surface
    },
    primary: {
      main: '#111315',     // Obsidian / Onyx black for authority & contrast
      light: '#23272C',
      dark: '#070809',
      contrastText: '#FAF9F6',
    },
    secondary: {
      main: '#B8976C',     // Warm Champagne Bronze / Gold accent
      light: '#C8A882',
      dark: '#8C6E45',
      contrastText: '#111315',
    },
    text: {
      primary: '#181A1D',  // Deep charcoal (warm luxury legibility)
      secondary: '#6B7280', // Refined neutral gray
    },
    divider: '#EAE6DF',    // Warm linen hairline border
    success: {
      main: '#235A36',     // Deep forest emerald
      light: '#EBF5EE',
      contrastText: '#FFFFFF',
    },
    warning: {
      main: '#8C6B1C',     // Warm champagne amber
      light: '#FDF8EB',
      contrastText: '#FFFFFF',
    },
    error: {
      main: '#8C2D2D',     // Subdued crimson
      light: '#FAF0F0',
      contrastText: '#FFFFFF',
    },
    info: {
      main: '#334155',     // Executive slate
      light: '#F1F5F9',
      contrastText: '#FFFFFF',
    },
  },
  typography: {
    fontFamily: [
      'Inter',
      '-apple-system',
      'BlinkMacSystemFont',
      '"Segoe UI"',
      'Roboto',
      'sans-serif',
    ].join(','),
    h1: {
      fontFamily: '"Playfair Display", "Cinzel", "Georgia", serif',
      fontWeight: 600,
      color: '#181A1D',
      letterSpacing: '-0.02em',
    },
    h2: {
      fontFamily: '"Playfair Display", "Cinzel", "Georgia", serif',
      fontWeight: 600,
      color: '#181A1D',
      letterSpacing: '-0.02em',
    },
    h3: {
      fontFamily: '"Playfair Display", "Cinzel", "Georgia", serif',
      fontWeight: 600,
      color: '#181A1D',
      letterSpacing: '-0.01em',
    },
    h4: {
      fontFamily: '"Playfair Display", "Cinzel", "Georgia", serif',
      fontWeight: 600,
      color: '#181A1D',
      letterSpacing: '-0.01em',
    },
    h5: {
      fontFamily: '"Playfair Display", "Cinzel", "Georgia", serif',
      fontWeight: 600,
      color: '#181A1D',
      letterSpacing: '-0.01em',
    },
    h6: {
      fontFamily: '"Playfair Display", "Cinzel", "Georgia", serif',
      fontWeight: 600,
      color: '#181A1D',
    },
    subtitle1: {
      color: '#6B7280',
      fontSize: '0.95rem',
      fontWeight: 400,
    },
    subtitle2: {
      fontSize: '0.75rem',
      fontWeight: 600,
      letterSpacing: '0.12em',
      textTransform: 'uppercase',
      color: '#A88654', // Bronze category overline
    },
    body1: {
      color: '#181A1D',
      fontSize: '0.925rem',
      lineHeight: 1.6,
    },
    body2: {
      color: '#6B7280',
      fontSize: '0.85rem',
      lineHeight: 1.5,
    },
    button: {
      textTransform: 'none',
      fontWeight: 500,
      letterSpacing: '0.01em',
    },
  },
  shape: {
    borderRadius: 8,
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: {
          backgroundColor: '#FAF9F6',
          color: '#181A1D',
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          backgroundColor: '#FFFFFF',
          borderRadius: 8,
          border: '1px solid #EAE6DF',
          boxShadow: '0 2px 8px rgba(17, 19, 21, 0.03)',
          transition: 'all 0.25s ease',
          '&:hover': {
            boxShadow: '0 8px 24px rgba(17, 19, 21, 0.06)',
          },
        },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
        },
      },
    },
    MuiButton: {
      styleOverrides: {
        root: {
          borderRadius: 6,
          padding: '8px 18px',
          fontWeight: 500,
          boxShadow: 'none',
          transition: 'all 0.2s ease',
          '&:hover': {
            boxShadow: '0 2px 8px rgba(17, 19, 21, 0.1)',
          },
        },
        containedPrimary: {
          backgroundColor: '#111315',
          color: '#FAF9F6',
          '&:hover': {
            backgroundColor: '#23272C',
          },
        },
        outlinedPrimary: {
          borderColor: '#D4CEBE',
          color: '#111315',
          '&:hover': {
            borderColor: '#B8976C',
            backgroundColor: 'rgba(184, 151, 108, 0.06)',
          },
        },
      },
    },
    MuiAppBar: {
      styleOverrides: {
        root: {
          backgroundColor: '#0D0F12', // Obsidian black header
          color: '#FAF9F6',
          boxShadow: 'none',
          borderBottom: '1px solid #1F2228',
        },
      },
    },
    MuiDrawer: {
      styleOverrides: {
        paper: {
          backgroundColor: '#FAF9F6',
          borderRight: '1px solid #EAE6DF',
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: {
          fontWeight: 500,
          borderRadius: 6,
        },
      },
    },
  },
});

export default theme;
