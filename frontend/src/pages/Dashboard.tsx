import React, { useEffect, useState } from 'react';
import {
  Grid,
  Card,
  CardContent,
  Typography,
  Box,
  Button,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
} from '@mui/material';
import EventAvailableOutlinedIcon from '@mui/icons-material/EventAvailableOutlined';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import HourglassEmptyOutlinedIcon from '@mui/icons-material/HourglassEmptyOutlined';
import DateRangeOutlinedIcon from '@mui/icons-material/DateRangeOutlined';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import AddIcon from '@mui/icons-material/Add';
import { useNavigate } from 'react-router-dom';
import apiClient from '../api/client';
import { useAuth } from '../context/AuthContext';
import { LeaveBalance, LeaveRequest, Page } from '../types';
import LoadingSpinner from '../components/common/LoadingSpinner';
import PageHeader from '../components/common/PageHeader';

export const Dashboard: React.FC = () => {
  const navigate = useNavigate();
  const { employee, user } = useAuth();

  const [balances, setBalances] = useState<LeaveBalance[]>([]);
  const [recentLeaves, setRecentLeaves] = useState<LeaveRequest[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadDashboardData = async () => {
      try {
        const [balancesRes, leavesRes] = await Promise.all([
          apiClient.get<LeaveBalance[]>('/api/leave-balances/me').catch(() => ({ data: [] })),
          apiClient.get<Page<LeaveRequest>>('/api/leave-requests/me?page=0&size=5').catch(() => ({ data: { content: [] } })),
        ]);

        setBalances(balancesRes.data || []);
        setRecentLeaves(leavesRes.data?.content || []);
      } finally {
        setLoading(false);
      }
    };

    loadDashboardData();
  }, []);

  if (loading) {
    return <LoadingSpinner message="Loading dashboard overview..." minHeight="50vh" />;
  }

  // Calculate high-level summary metrics
  const totalAvailable = balances.reduce((sum, b) => sum + (b.availableDays || 0), 0);
  const totalUsed = balances.reduce((sum, b) => sum + (b.usedDays || 0), 0);
  const pendingRequestsCount = recentLeaves.filter((l) => l.status === 'PENDING').length;

  const greetingName = employee
    ? `${employee.firstName}`
    : user?.email?.split('@')[0] || 'Colleague';

  const getStatusChip = (status: string) => {
    switch (status) {
      case 'APPROVED':
        return (
          <Chip
            size="small"
            label="Approved"
            sx={{
              backgroundColor: '#EBF5EE',
              color: '#235A36',
              border: '1px solid #D1E8D7',
              fontWeight: 500,
            }}
          />
        );
      case 'PENDING':
        return (
          <Chip
            size="small"
            label="Pending"
            sx={{
              backgroundColor: '#FDF8EB',
              color: '#8C6B1C',
              border: '1px solid #F4E3B8',
              fontWeight: 500,
            }}
          />
        );
      case 'REJECTED':
        return (
          <Chip
            size="small"
            label="Rejected"
            sx={{
              backgroundColor: '#FAF0F0',
              color: '#8C2D2D',
              border: '1px solid #ECCACA',
              fontWeight: 500,
            }}
          />
        );
      case 'CANCELLED':
        return (
          <Chip
            size="small"
            label="Cancelled"
            sx={{
              backgroundColor: '#F3F4F6',
              color: '#4B5563',
              border: '1px solid #E5E7EB',
              fontWeight: 500,
            }}
          />
        );
      default:
        return <Chip size="small" label={status} />;
    }
  };

  return (
    <Box>
      <PageHeader
        category="EXECUTIVE OVERVIEW"
        title={`Welcome, ${greetingName}`}
        subtitle="Manage your annual entitlements, check remaining quotas, and submit new leave requests."
        action={
          <Button
            variant="contained"
            color="primary"
            startIcon={<AddIcon sx={{ color: '#C5A880' }} />}
            onClick={() => navigate('/apply-leave')}
            sx={{
              px: 2.5,
              py: 1,
              backgroundColor: '#111315',
              border: '1px solid #111315',
              '&:hover': {
                backgroundColor: '#23272C',
              },
            }}
          >
            Apply for Leave
          </Button>
        }
      />

      {/* 3 Main Highlight Metric Cards with Vertical Gold Accent Bar (Image 3 Style) */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        <Grid item xs={12} sm={4}>
          <Card
            sx={{
              borderLeft: '4px solid #B8976C', // Warm Champagne Bronze
              backgroundColor: '#FFFFFF',
              height: '100%',
            }}
          >
            <CardContent sx={{ p: 3 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography
                  variant="subtitle2"
                  sx={{ color: '#B8976C', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
                >
                  AVAILABLE DAYS
                </Typography>
                <EventAvailableOutlinedIcon sx={{ color: '#B8976C', fontSize: 24 }} />
              </Box>
              <Typography
                variant="h3"
                sx={{
                  fontFamily: '"Playfair Display", Georgia, serif',
                  fontWeight: 600,
                  color: '#16181B',
                  lineHeight: 1.1,
                }}
              >
                {totalAvailable}
              </Typography>
              <Typography variant="body2" sx={{ color: '#6B7280', mt: 0.75 }}>
                Remaining annual balance
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={4}>
          <Card
            sx={{
              borderLeft: '4px solid #111315', // Obsidian Slate
              backgroundColor: '#FFFFFF',
              height: '100%',
            }}
          >
            <CardContent sx={{ p: 3 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography
                  variant="subtitle2"
                  sx={{ color: '#6B7280', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
                >
                  USED DAYS
                </Typography>
                <CheckCircleOutlineIcon sx={{ color: '#111315', fontSize: 24 }} />
              </Box>
              <Typography
                variant="h3"
                sx={{
                  fontFamily: '"Playfair Display", Georgia, serif',
                  fontWeight: 600,
                  color: '#16181B',
                  lineHeight: 1.1,
                }}
              >
                {totalUsed}
              </Typography>
              <Typography variant="body2" sx={{ color: '#6B7280', mt: 0.75 }}>
                Utilized leave in current cycle
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={4}>
          <Card
            sx={{
              borderLeft: '4px solid #C5A880', // Champagne Gold
              backgroundColor: '#FFFFFF',
              height: '100%',
            }}
          >
            <CardContent sx={{ p: 3 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography
                  variant="subtitle2"
                  sx={{ color: '#8C6B1C', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
                >
                  PENDING APPROVAL
                </Typography>
                <HourglassEmptyOutlinedIcon sx={{ color: '#8C6B1C', fontSize: 24 }} />
              </Box>
              <Typography
                variant="h3"
                sx={{
                  fontFamily: '"Playfair Display", Georgia, serif',
                  fontWeight: 600,
                  color: '#16181B',
                  lineHeight: 1.1,
                }}
              >
                {pendingRequestsCount}
              </Typography>
              <Typography variant="body2" sx={{ color: '#6B7280', mt: 0.75 }}>
                Awaiting manager decision
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Leave Entitlement Breakdown Grid (Image 2 Tech Stack Style) */}
      <Box sx={{ mb: 4 }}>
        <Box sx={{ mb: 2 }}>
          <Typography
            variant="subtitle2"
            sx={{ color: '#B8976C', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
          >
            ENTITLEMENT
          </Typography>
          <Typography
            variant="h5"
            sx={{
              fontFamily: '"Playfair Display", Georgia, serif',
              fontWeight: 600,
              color: '#16181B',
            }}
          >
            Leave Quotas by Category
          </Typography>
        </Box>

        <Grid container spacing={2.5}>
          {balances.length === 0 ? (
            <Grid item xs={12}>
              <Card sx={{ p: 3, textAlign: 'center' }}>
                <Typography variant="body2" sx={{ color: '#6B7280' }}>
                  No leave quotas configured for current year. Please contact HR.
                </Typography>
              </Card>
            </Grid>
          ) : (
            balances.map((balance) => (
              <Grid item xs={12} sm={6} md={3} key={balance.id}>
                <Card
                  sx={{
                    textAlign: 'center',
                    p: 2.5,
                    border: '1px solid #EAE6DF',
                    transition: 'all 0.2s ease',
                    '&:hover': {
                      borderColor: '#B8976C',
                      transform: 'translateY(-2px)',
                    },
                  }}
                >
                  <DateRangeOutlinedIcon sx={{ color: '#B8976C', fontSize: 32, mb: 1 }} />
                  <Typography
                    variant="body1"
                    sx={{
                      fontWeight: 600,
                      color: '#16181B',
                      fontSize: '0.95rem',
                      mb: 0.5,
                    }}
                  >
                    {balance.leaveType?.name || 'Leave Type'}
                  </Typography>
                  <Typography
                    variant="h4"
                    sx={{
                      fontFamily: '"Playfair Display", Georgia, serif',
                      fontWeight: 600,
                      color: '#16181B',
                      my: 0.5,
                    }}
                  >
                    {balance.availableDays}
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#6B7280', display: 'block' }}>
                    Available of {balance.totalDays} Total
                  </Typography>
                </Card>
              </Grid>
            ))
          )}
        </Grid>
      </Box>

      {/* Recent Submissions Section */}
      <Card sx={{ border: '1px solid #EAE6DF' }}>
        <Box
          sx={{
            p: 3,
            pb: 2,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            borderBottom: '1px solid #EAE6DF',
          }}
        >
          <Box>
            <Typography
              variant="subtitle2"
              sx={{ color: '#B8976C', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
            >
              ACTIVITY
            </Typography>
            <Typography
              variant="h6"
              sx={{
                fontFamily: '"Playfair Display", Georgia, serif',
                fontWeight: 600,
                color: '#16181B',
              }}
            >
              Recent Leave Submissions
            </Typography>
          </Box>
          <Button
            size="small"
            endIcon={<ArrowForwardIcon sx={{ fontSize: 16 }} />}
            onClick={() => navigate('/my-leaves')}
            sx={{
              color: '#B8976C',
              fontWeight: 600,
              fontSize: '0.825rem',
              letterSpacing: '0.04em',
              textTransform: 'uppercase',
              '&:hover': {
                backgroundColor: 'rgba(184, 151, 108, 0.08)',
              },
            }}
          >
            View All Records
          </Button>
        </Box>

        <TableContainer component={Paper} elevation={0}>
          <Table sx={{ minWidth: 600 }}>
            <TableHead sx={{ backgroundColor: '#F7F5F0' }}>
              <TableRow>
                <TableCell sx={{ fontWeight: 600, color: '#16181B', fontSize: '0.8rem', letterSpacing: '0.05em' }}>
                  TYPE
                </TableCell>
                <TableCell sx={{ fontWeight: 600, color: '#16181B', fontSize: '0.8rem', letterSpacing: '0.05em' }}>
                  DURATION
                </TableCell>
                <TableCell sx={{ fontWeight: 600, color: '#16181B', fontSize: '0.8rem', letterSpacing: '0.05em' }}>
                  DAYS
                </TableCell>
                <TableCell sx={{ fontWeight: 600, color: '#16181B', fontSize: '0.8rem', letterSpacing: '0.05em' }}>
                  REASON
                </TableCell>
                <TableCell sx={{ fontWeight: 600, color: '#16181B', fontSize: '0.8rem', letterSpacing: '0.05em' }}>
                  STATUS
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {recentLeaves.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={5} align="center" sx={{ py: 4, color: '#6B7280' }}>
                    No recent leave applications found.
                  </TableCell>
                </TableRow>
              ) : (
                recentLeaves.map((leave) => (
                  <TableRow key={leave.id} hover sx={{ '&:last-child td, &:last-child th': { border: 0 } }}>
                    <TableCell sx={{ fontWeight: 600, color: '#16181B' }}>
                      {leave.leaveType?.name || 'Leave'}
                    </TableCell>
                    <TableCell sx={{ color: '#4B5563' }}>
                      {leave.startDate} to {leave.endDate}
                    </TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>{leave.days}</TableCell>
                    <TableCell sx={{ color: '#6B7280', maxWidth: 220, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {leave.reason || '-'}
                    </TableCell>
                    <TableCell>{getStatusChip(leave.status)}</TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>
    </Box>
  );
};

export default Dashboard;
