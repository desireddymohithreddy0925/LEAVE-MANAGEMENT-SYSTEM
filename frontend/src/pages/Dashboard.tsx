import React, { useEffect, useState, useMemo, useCallback } from 'react';
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
  LinearProgress,
  IconButton,
  Tooltip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Stack,
} from '@mui/material';
import EventAvailableOutlinedIcon from '@mui/icons-material/EventAvailableOutlined';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import HourglassEmptyOutlinedIcon from '@mui/icons-material/HourglassEmptyOutlined';
import DateRangeOutlinedIcon from '@mui/icons-material/DateRangeOutlined';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import AddIcon from '@mui/icons-material/Add';
import RefreshOutlinedIcon from '@mui/icons-material/RefreshOutlined';
import CelebrationOutlinedIcon from '@mui/icons-material/CelebrationOutlined';
import BeachAccessOutlinedIcon from '@mui/icons-material/BeachAccessOutlined';
import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import { useNavigate } from 'react-router-dom';
import apiClient from '../api/client';
import { useAuth } from '../context/AuthContext';
import { useNotification } from '../context/NotificationContext';
import { LeaveBalance, LeaveRequest, Page, Holiday } from '../types';
import LoadingSpinner from '../components/common/LoadingSpinner';
import PageHeader from '../components/common/PageHeader';

// Standard official corporate and statutory holidays
const CORPORATE_HOLIDAYS: Holiday[] = [
  { id: 1, name: 'New Year’s Day', date: '2027-01-01', dayOfWeek: 'Friday', type: 'STATUTORY', description: 'Federal & Company Holiday' },
  { id: 2, name: 'Martin Luther King Jr. Day', date: '2027-01-18', dayOfWeek: 'Monday', type: 'STATUTORY', description: 'Public Holiday' },
  { id: 3, name: 'Memorial Day', date: '2027-05-31', dayOfWeek: 'Monday', type: 'STATUTORY', description: 'Federal Holiday' },
  { id: 4, name: 'Juneteenth National Independence Day', date: '2027-06-19', dayOfWeek: 'Saturday', type: 'STATUTORY', description: 'Public Holiday' },
  { id: 5, name: 'Independence Day', date: '2027-07-04', dayOfWeek: 'Sunday', type: 'STATUTORY', description: 'National Holiday' },
  { id: 6, name: 'Labor Day', date: '2027-09-06', dayOfWeek: 'Monday', type: 'STATUTORY', description: 'Federal Holiday' },
  { id: 7, name: 'Thanksgiving Day', date: '2026-11-26', dayOfWeek: 'Thursday', type: 'STATUTORY', description: 'National Holiday' },
  { id: 8, name: 'Day After Thanksgiving', date: '2026-11-27', dayOfWeek: 'Friday', type: 'COMPANY', description: 'Corporate Floating Holiday' },
  { id: 9, name: 'Christmas Eve (Half Day)', date: '2026-12-24', dayOfWeek: 'Thursday', type: 'COMPANY', description: 'Company Holiday' },
  { id: 10, name: 'Christmas Day', date: '2026-12-25', dayOfWeek: 'Friday', type: 'STATUTORY', description: 'Federal & Company Holiday' },
];

export const Dashboard: React.FC = () => {
  const navigate = useNavigate();
  const { employee, user } = useAuth();
  const { showSuccess, showError } = useNotification();

  const [balances, setBalances] = useState<LeaveBalance[]>([]);
  const [recentLeaves, setRecentLeaves] = useState<LeaveRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  // Cancellation dialog state
  const [cancelTargetId, setCancelTargetId] = useState<number | null>(null);
  const [cancelling, setCancelling] = useState(false);

  const loadDashboardData = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true);
    try {
      const currentYear = new Date().getFullYear();
      const [balancesRes, leavesRes] = await Promise.all([
        apiClient
          .get<LeaveBalance[]>('/api/leave-balances/me', { params: { year: currentYear } })
          .catch(async () => {
            // Fallback without year parameter if year-specific query has no seeded balance
            return apiClient.get<LeaveBalance[]>('/api/leave-balances/me').catch(() => ({ data: [] }));
          }),
        apiClient
          .get<Page<LeaveRequest>>('/api/leave-requests/me?page=0&size=10&sort=appliedAt,desc')
          .catch(async () => {
            // Fallback without sort if appliedAt is null in some legacy records
            return apiClient.get<Page<LeaveRequest>>('/api/leave-requests/me?page=0&size=10').catch(() => ({
              data: { content: [] as LeaveRequest[], totalElements: 0, totalPages: 0, number: 0, size: 10 },
            }));
          }),
      ]);

      setBalances(balancesRes.data || []);
      setRecentLeaves(leavesRes.data?.content || []);
      if (isRefresh) showSuccess('Dashboard refreshed with latest data');
    } catch {
      showError('Unable to load real-time leave data. Please verify your connection.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [showSuccess, showError]);

  useEffect(() => {
    loadDashboardData();
  }, [loadDashboardData]);

  // Aggregate leave calculations (handling both Spring Boot DTO and legacy property naming)
  const totalAvailable = useMemo(() => {
    return balances.reduce((sum, b) => sum + (b.available ?? b.availableDays ?? 0), 0);
  }, [balances]);

  const totalUsed = useMemo(() => {
    return balances.reduce((sum, b) => sum + (b.used ?? b.usedDays ?? 0), 0);
  }, [balances]);

  const totalAllocated = useMemo(() => {
    return balances.reduce((sum, b) => sum + (b.total ?? b.totalDays ?? 0), 0);
  }, [balances]);

  const pendingLeaves = useMemo(() => {
    return recentLeaves.filter((l) => l.status === 'PENDING');
  }, [recentLeaves]);

  const pendingDaysCount = useMemo(() => {
    return pendingLeaves.reduce((sum, l) => sum + (l.requestedDays ?? l.days ?? 0), 0);
  }, [pendingLeaves]);

  // Upcoming approved leaves for the employee
  const upcomingApprovedLeaves = useMemo(() => {
    const todayStr = new Date().toISOString().split('T')[0];
    return recentLeaves
      .filter((l) => l.status === 'APPROVED' && l.endDate >= todayStr)
      .sort((a, b) => a.startDate.localeCompare(b.startDate));
  }, [recentLeaves]);

  // Upcoming statutory & company holidays sorted by date
  const upcomingHolidays = useMemo(() => {
    const todayStr = new Date().toISOString().split('T')[0];
    return CORPORATE_HOLIDAYS
      .filter((h) => h.date >= todayStr)
      .sort((a, b) => a.date.localeCompare(b.date))
      .slice(0, 4);
  }, []);

  // Handle cancel leave request
  const handleOpenCancelDialog = (id: number) => {
    setCancelTargetId(id);
  };

  const handleCloseCancelDialog = () => {
    setCancelTargetId(null);
  };

  const handleConfirmCancel = async () => {
    if (!cancelTargetId) return;
    setCancelling(true);
    try {
      await apiClient.put(`/api/leave-requests/${cancelTargetId}/cancel`);
      showSuccess('Leave request cancelled successfully. Quota has been restored.');
      handleCloseCancelDialog();
      await loadDashboardData();
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      showError(axiosErr.response?.data?.message || 'Failed to cancel leave request.');
    } finally {
      setCancelling(false);
    }
  };

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
              fontWeight: 600,
              fontSize: '0.725rem',
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
              fontWeight: 600,
              fontSize: '0.725rem',
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
              fontWeight: 600,
              fontSize: '0.725rem',
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
              fontWeight: 600,
              fontSize: '0.725rem',
            }}
          />
        );
      default:
        return <Chip size="small" label={status} />;
    }
  };

  const formatDaysUntil = (dateStr: string): string => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const target = new Date(dateStr);
    target.setHours(0, 0, 0, 0);
    const diffTime = target.getTime() - today.getTime();
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    if (diffDays === 0) return 'Today';
    if (diffDays === 1) return 'Tomorrow';
    return `In ${diffDays} days`;
  };

  if (loading) {
    return <LoadingSpinner message="Retrieving real-time employee leave records..." minHeight="55vh" />;
  }

  return (
    <Box sx={{ pb: 4 }}>
      {/* Page Header with Real-Time Actions */}
      <PageHeader
        category="EMPLOYEE DASHBOARD"
        title={`Welcome back, ${greetingName}`}
        subtitle="Review your annual quotas, track pending approvals, plan upcoming vacations, and check statutory holidays."
        action={
          <Stack direction="row" spacing={1.5} alignItems="center">
            <Tooltip title="Refresh real-time data">
              <span>
                <IconButton
                  onClick={() => loadDashboardData(true)}
                  disabled={refreshing}
                  sx={{
                    border: '1px solid #EAE6DF',
                    backgroundColor: '#FFFFFF',
                    color: '#16181B',
                    '&:hover': { backgroundColor: '#FAF9F6', borderColor: '#B8976C' },
                  }}
                  size="small"
                >
                  <RefreshOutlinedIcon fontSize="small" sx={{ animation: refreshing ? 'spin 1s linear infinite' : 'none' }} />
                </IconButton>
              </span>
            </Tooltip>
            <Button
              variant="contained"
              startIcon={<AddIcon sx={{ color: '#C5A880' }} />}
              onClick={() => navigate('/apply-leave')}
              sx={{
                px: 2.5,
                py: 1,
                backgroundColor: '#111315',
                color: '#FAF9F6',
                fontWeight: 600,
                fontSize: '0.875rem',
                letterSpacing: '0.02em',
                border: '1px solid #111315',
                '&:hover': {
                  backgroundColor: '#23272C',
                },
              }}
            >
              Apply for Leave
            </Button>
          </Stack>
        }
      />

      {/* 4 Summary Highlight Cards (Responsive Grid: xs=12, sm=6, md=3) */}
      <Grid container spacing={2.5} sx={{ mb: 4 }}>
        {/* Card 1: Available Days */}
        <Grid item xs={12} sm={6} md={3}>
          <Card
            sx={{
              borderLeft: '4px solid #B8976C', // Champagne Gold
              backgroundColor: '#FFFFFF',
              borderTop: '1px solid #EAE6DF',
              borderRight: '1px solid #EAE6DF',
              borderBottom: '1px solid #EAE6DF',
              height: '100%',
              boxShadow: '0 2px 8px rgba(17, 19, 21, 0.04)',
            }}
          >
            <CardContent sx={{ p: 2.5 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography
                  variant="subtitle2"
                  sx={{ color: '#B8976C', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
                >
                  AVAILABLE BALANCE
                </Typography>
                <EventAvailableOutlinedIcon sx={{ color: '#B8976C', fontSize: 22 }} />
              </Box>
              <Typography
                variant="h3"
                sx={{
                  fontFamily: '"Playfair Display", Georgia, serif',
                  fontWeight: 600,
                  color: '#16181B',
                  lineHeight: 1.15,
                }}
              >
                {totalAvailable}
                <Typography component="span" variant="body1" sx={{ color: '#6B7280', ml: 0.75, fontWeight: 500 }}>
                  days
                </Typography>
              </Typography>
              <Typography variant="caption" sx={{ color: '#6B7280', display: 'block', mt: 0.75 }}>
                Remaining quota for active year
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        {/* Card 2: Used Days */}
        <Grid item xs={12} sm={6} md={3}>
          <Card
            sx={{
              borderLeft: '4px solid #111315', // Obsidian Slate
              backgroundColor: '#FFFFFF',
              borderTop: '1px solid #EAE6DF',
              borderRight: '1px solid #EAE6DF',
              borderBottom: '1px solid #EAE6DF',
              height: '100%',
              boxShadow: '0 2px 8px rgba(17, 19, 21, 0.04)',
            }}
          >
            <CardContent sx={{ p: 2.5 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography
                  variant="subtitle2"
                  sx={{ color: '#6B7280', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
                >
                  USED DAYS
                </Typography>
                <CheckCircleOutlineIcon sx={{ color: '#111315', fontSize: 22 }} />
              </Box>
              <Typography
                variant="h3"
                sx={{
                  fontFamily: '"Playfair Display", Georgia, serif',
                  fontWeight: 600,
                  color: '#16181B',
                  lineHeight: 1.15,
                }}
              >
                {totalUsed}
                <Typography component="span" variant="body1" sx={{ color: '#6B7280', ml: 0.75, fontWeight: 500 }}>
                  days
                </Typography>
              </Typography>
              <Typography variant="caption" sx={{ color: '#6B7280', display: 'block', mt: 0.75 }}>
                Approved leaves taken this cycle
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        {/* Card 3: Pending Approval */}
        <Grid item xs={12} sm={6} md={3}>
          <Card
            sx={{
              borderLeft: '4px solid #8C6B1C', // Warm Amber
              backgroundColor: '#FFFFFF',
              borderTop: '1px solid #EAE6DF',
              borderRight: '1px solid #EAE6DF',
              borderBottom: '1px solid #EAE6DF',
              height: '100%',
              boxShadow: '0 2px 8px rgba(17, 19, 21, 0.04)',
            }}
          >
            <CardContent sx={{ p: 2.5 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography
                  variant="subtitle2"
                  sx={{ color: '#8C6B1C', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
                >
                  PENDING DECISION
                </Typography>
                <HourglassEmptyOutlinedIcon sx={{ color: '#8C6B1C', fontSize: 22 }} />
              </Box>
              <Typography
                variant="h3"
                sx={{
                  fontFamily: '"Playfair Display", Georgia, serif',
                  fontWeight: 600,
                  color: '#16181B',
                  lineHeight: 1.15,
                }}
              >
                {pendingDaysCount}
                <Typography component="span" variant="body1" sx={{ color: '#6B7280', ml: 0.75, fontWeight: 500 }}>
                  days
                </Typography>
              </Typography>
              <Typography variant="caption" sx={{ color: '#6B7280', display: 'block', mt: 0.75 }}>
                Across {pendingLeaves.length} pending request(s)
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        {/* Card 4: Total Allocation */}
        <Grid item xs={12} sm={6} md={3}>
          <Card
            sx={{
              borderLeft: '4px solid #4B5563', // Slate
              backgroundColor: '#FFFFFF',
              borderTop: '1px solid #EAE6DF',
              borderRight: '1px solid #EAE6DF',
              borderBottom: '1px solid #EAE6DF',
              height: '100%',
              boxShadow: '0 2px 8px rgba(17, 19, 21, 0.04)',
            }}
          >
            <CardContent sx={{ p: 2.5 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography
                  variant="subtitle2"
                  sx={{ color: '#4B5563', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
                >
                  TOTAL ENTITLEMENT
                </Typography>
                <DateRangeOutlinedIcon sx={{ color: '#4B5563', fontSize: 22 }} />
              </Box>
              <Typography
                variant="h3"
                sx={{
                  fontFamily: '"Playfair Display", Georgia, serif',
                  fontWeight: 600,
                  color: '#16181B',
                  lineHeight: 1.15,
                }}
              >
                {totalAllocated}
                <Typography component="span" variant="body1" sx={{ color: '#6B7280', ml: 0.75, fontWeight: 500 }}>
                  days
                </Typography>
              </Typography>
              <Typography variant="caption" sx={{ color: '#6B7280', display: 'block', mt: 0.75 }}>
                {balances.length} leave quota category(s)
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Leave Quotas by Category Section */}
      <Box sx={{ mb: 4.5 }}>
        <Box sx={{ mb: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
          <Box>
            <Typography
              variant="subtitle2"
              sx={{ color: '#B8976C', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
            >
              ENTITLEMENT ALLOCATION
            </Typography>
            <Typography
              variant="h5"
              sx={{
                fontFamily: '"Playfair Display", Georgia, serif',
                fontWeight: 600,
                color: '#16181B',
              }}
            >
              Leave Balances by Category
            </Typography>
          </Box>
          <Typography variant="caption" sx={{ color: '#6B7280', fontSize: '0.75rem' }}>
            Year: {balances[0]?.year || new Date().getFullYear()}
          </Typography>
        </Box>

        <Grid container spacing={2.5}>
          {balances.length === 0 ? (
            <Grid item xs={12}>
              <Card sx={{ p: 4, textAlign: 'center', border: '1px solid #EAE6DF', backgroundColor: '#FFFFFF' }}>
                <InfoOutlinedIcon sx={{ color: '#B8976C', fontSize: 32, mb: 1 }} />
                <Typography variant="body1" sx={{ color: '#16181B', fontWeight: 600, mb: 0.5 }}>
                  No leave quotas configured for current cycle
                </Typography>
                <Typography variant="body2" sx={{ color: '#6B7280', maxWidth: 440, mx: 'auto' }}>
                  Your employee profile does not yet have an allocated leave balance for this calendar year. Please contact HR to initialize your entitlements.
                </Typography>
              </Card>
            </Grid>
          ) : (
            balances.map((balance) => {
              const avail = balance.available ?? balance.availableDays ?? 0;
              const used = balance.used ?? balance.usedDays ?? 0;
              const total = balance.total ?? balance.totalDays ?? (avail + used > 0 ? avail + used : 1);
              const percentUsed = Math.min(100, Math.round((used / total) * 100));

              return (
                <Grid item xs={12} sm={6} md={4} key={balance.id}>
                  <Card
                    sx={{
                      p: 2.5,
                      backgroundColor: '#FFFFFF',
                      border: '1px solid #EAE6DF',
                      borderRadius: 1.5,
                      transition: 'all 0.2s ease',
                      boxShadow: '0 2px 6px rgba(17, 19, 21, 0.03)',
                      '&:hover': {
                        borderColor: '#B8976C',
                        transform: 'translateY(-2px)',
                        boxShadow: '0 6px 16px rgba(17, 19, 21, 0.06)',
                      },
                    }}
                  >
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 1.5 }}>
                      <Box>
                        <Typography
                          variant="subtitle1"
                          sx={{
                            fontWeight: 600,
                            color: '#16181B',
                            fontSize: '0.975rem',
                            lineHeight: 1.2,
                          }}
                        >
                          {balance.leaveType?.name || 'Leave Category'}
                        </Typography>
                        {balance.leaveType?.description && (
                          <Typography
                            variant="caption"
                            sx={{ color: '#6B7280', display: 'block', mt: 0.25, fontSize: '0.725rem' }}
                          >
                            {balance.leaveType.description}
                          </Typography>
                        )}
                      </Box>
                      <Chip
                        label={`${avail} Available`}
                        size="small"
                        sx={{
                          backgroundColor: 'rgba(184, 151, 108, 0.1)',
                          color: '#8C6E45',
                          fontWeight: 600,
                          fontSize: '0.7rem',
                          height: 22,
                        }}
                      />
                    </Box>

                    {/* Numerical breakdown */}
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', mt: 2, mb: 1 }}>
                      <Typography variant="caption" sx={{ color: '#6B7280', fontSize: '0.75rem' }}>
                        Used: <strong>{used} d</strong>
                      </Typography>
                      <Typography variant="caption" sx={{ color: '#6B7280', fontSize: '0.75rem' }}>
                        Total: <strong>{total} d</strong>
                      </Typography>
                    </Box>

                    {/* Progress Bar */}
                    <LinearProgress
                      variant="determinate"
                      value={percentUsed}
                      sx={{
                        height: 7,
                        borderRadius: 4,
                        backgroundColor: '#F3EFEA',
                        '& .MuiLinearProgress-bar': {
                          backgroundColor: percentUsed > 80 ? '#8C2D2D' : '#B8976C',
                          borderRadius: 4,
                        },
                      }}
                    />
                    <Typography
                      variant="caption"
                      sx={{ color: '#9CA3AF', display: 'block', mt: 0.75, textAlign: 'right', fontSize: '0.7rem' }}
                    >
                      {percentUsed}% quota utilized
                    </Typography>
                  </Card>
                </Grid>
              );
            })
          )}
        </Grid>
      </Box>

      {/* Two-Column Responsive Section: Upcoming Holidays & Upcoming Approved Leaves */}
      <Grid container spacing={3} sx={{ mb: 4.5 }}>
        {/* Left Column: Upcoming Corporate & Public Holidays */}
        <Grid item xs={12} md={6}>
          <Card
            sx={{
              backgroundColor: '#FFFFFF',
              border: '1px solid #EAE6DF',
              height: '100%',
              boxShadow: '0 2px 8px rgba(17, 19, 21, 0.04)',
            }}
          >
            <Box sx={{ p: 2.5, pb: 2, borderBottom: '1px solid #EAE6DF', display: 'flex', alignItems: 'center', gap: 1.5 }}>
              <CelebrationOutlinedIcon sx={{ color: '#B8976C', fontSize: 24 }} />
              <Box>
                <Typography
                  variant="subtitle2"
                  sx={{ color: '#B8976C', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
                >
                  CALENDAR SCHEDULE
                </Typography>
                <Typography
                  variant="h6"
                  sx={{
                    fontFamily: '"Playfair Display", Georgia, serif',
                    fontWeight: 600,
                    color: '#16181B',
                  }}
                >
                  Upcoming Public & Company Holidays
                </Typography>
              </Box>
            </Box>

            <Box sx={{ p: 2.5 }}>
              <Stack spacing={1.5}>
                {upcomingHolidays.map((holiday) => (
                  <Box
                    key={holiday.id}
                    sx={{
                      p: 1.5,
                      borderRadius: 1.5,
                      backgroundColor: '#FAF9F6',
                      border: '1px solid #EAE6DF',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      transition: 'background-color 0.2s',
                      '&:hover': { backgroundColor: '#F5F3ED' },
                    }}
                  >
                    <Box>
                      <Typography variant="body2" sx={{ fontWeight: 600, color: '#16181B' }}>
                        {holiday.name}
                      </Typography>
                      <Typography variant="caption" sx={{ color: '#6B7280' }}>
                        {holiday.dayOfWeek} · {holiday.date}
                      </Typography>
                    </Box>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Chip
                        label={holiday.type}
                        size="small"
                        sx={{
                          fontSize: '0.675rem',
                          height: 20,
                          backgroundColor: holiday.type === 'STATUTORY' ? '#EBF5EE' : '#F1F5F9',
                          color: holiday.type === 'STATUTORY' ? '#235A36' : '#334155',
                          fontWeight: 600,
                        }}
                      />
                      <Chip
                        label={formatDaysUntil(holiday.date)}
                        size="small"
                        variant="outlined"
                        sx={{
                          fontSize: '0.675rem',
                          height: 20,
                          borderColor: '#D4CEBE',
                          color: '#8C6E45',
                        }}
                      />
                    </Stack>
                  </Box>
                ))}
              </Stack>

              <Typography variant="caption" sx={{ color: '#9CA3AF', display: 'block', mt: 2, textAlign: 'center' }}>
                Note: Official holidays do not decrement your annual leave balance quota.
              </Typography>
            </Box>
          </Card>
        </Grid>

        {/* Right Column: Upcoming Approved Employee Leaves */}
        <Grid item xs={12} md={6}>
          <Card
            sx={{
              backgroundColor: '#FFFFFF',
              border: '1px solid #EAE6DF',
              height: '100%',
              boxShadow: '0 2px 8px rgba(17, 19, 21, 0.04)',
            }}
          >
            <Box sx={{ p: 2.5, pb: 2, borderBottom: '1px solid #EAE6DF', display: 'flex', alignItems: 'center', gap: 1.5 }}>
              <BeachAccessOutlinedIcon sx={{ color: '#B8976C', fontSize: 24 }} />
              <Box>
                <Typography
                  variant="subtitle2"
                  sx={{ color: '#B8976C', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
                >
                  SCHEDULED TIME OFF
                </Typography>
                <Typography
                  variant="h6"
                  sx={{
                    fontFamily: '"Playfair Display", Georgia, serif',
                    fontWeight: 600,
                    color: '#16181B',
                  }}
                >
                  Your Upcoming Approved Leaves
                </Typography>
              </Box>
            </Box>

            <Box sx={{ p: 2.5 }}>
              {upcomingApprovedLeaves.length === 0 ? (
                <Box sx={{ textAlign: 'center', py: 4 }}>
                  <DateRangeOutlinedIcon sx={{ color: '#D4CEBE', fontSize: 36, mb: 1 }} />
                  <Typography variant="body2" sx={{ fontWeight: 600, color: '#16181B' }}>
                    No upcoming scheduled leaves
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#6B7280', display: 'block', mt: 0.5, mb: 2 }}>
                    You have no approved time-off requests on the calendar.
                  </Typography>
                  <Button
                    size="small"
                    variant="outlined"
                    onClick={() => navigate('/apply-leave')}
                    sx={{
                      borderColor: '#D4CEBE',
                      color: '#16181B',
                      fontSize: '0.775rem',
                      '&:hover': { borderColor: '#B8976C', backgroundColor: 'rgba(184, 151, 108, 0.06)' },
                    }}
                  >
                    Plan Time Off
                  </Button>
                </Box>
              ) : (
                <Stack spacing={1.5}>
                  {upcomingApprovedLeaves.map((leave) => {
                    const days = leave.requestedDays ?? leave.days ?? 1;
                    return (
                      <Box
                        key={leave.id}
                        sx={{
                          p: 1.5,
                          borderRadius: 1.5,
                          backgroundColor: '#FAF9F6',
                          border: '1px solid #EAE6DF',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                        }}
                      >
                        <Box>
                          <Typography variant="body2" sx={{ fontWeight: 600, color: '#16181B' }}>
                            {leave.leaveType?.name || 'Approved Leave'}
                          </Typography>
                          <Typography variant="caption" sx={{ color: '#6B7280' }}>
                            {leave.startDate} to {leave.endDate} · ({days} working {days === 1 ? 'day' : 'days'})
                          </Typography>
                        </Box>
                        <Chip
                          label={formatDaysUntil(leave.startDate)}
                          size="small"
                          sx={{
                            backgroundColor: '#EBF5EE',
                            color: '#235A36',
                            fontWeight: 600,
                            fontSize: '0.7rem',
                            height: 22,
                          }}
                        />
                      </Box>
                    );
                  })}
                </Stack>
              )}
            </Box>
          </Card>
        </Grid>
      </Grid>

      {/* Recent Leave Requests History Table */}
      <Card sx={{ border: '1px solid #EAE6DF', backgroundColor: '#FFFFFF', boxShadow: '0 2px 8px rgba(17, 19, 21, 0.04)' }}>
        <Box
          sx={{
            p: 3,
            pb: 2,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            borderBottom: '1px solid #EAE6DF',
            flexWrap: 'wrap',
            gap: 1.5,
          }}
        >
          <Box>
            <Typography
              variant="subtitle2"
              sx={{ color: '#B8976C', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
            >
              TRANSACTION HISTORY
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
          <Table sx={{ minWidth: 650 }}>
            <TableHead sx={{ backgroundColor: '#FAF9F6' }}>
              <TableRow>
                <TableCell sx={{ fontWeight: 600, color: '#16181B', fontSize: '0.775rem', letterSpacing: '0.05em' }}>
                  LEAVE TYPE
                </TableCell>
                <TableCell sx={{ fontWeight: 600, color: '#16181B', fontSize: '0.775rem', letterSpacing: '0.05em' }}>
                  DURATION
                </TableCell>
                <TableCell sx={{ fontWeight: 600, color: '#16181B', fontSize: '0.775rem', letterSpacing: '0.05em' }}>
                  WORKING DAYS
                </TableCell>
                <TableCell sx={{ fontWeight: 600, color: '#16181B', fontSize: '0.775rem', letterSpacing: '0.05em' }}>
                  REASON
                </TableCell>
                <TableCell sx={{ fontWeight: 600, color: '#16181B', fontSize: '0.775rem', letterSpacing: '0.05em' }}>
                  STATUS
                </TableCell>
                <TableCell align="right" sx={{ fontWeight: 600, color: '#16181B', fontSize: '0.775rem', letterSpacing: '0.05em' }}>
                  ACTION
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {recentLeaves.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 4, color: '#6B7280' }}>
                    <Typography variant="body2" sx={{ color: '#6B7280', mb: 1 }}>
                      No recent leave requests recorded.
                    </Typography>
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={() => navigate('/apply-leave')}
                      sx={{ borderColor: '#D4CEBE', color: '#16181B', fontSize: '0.75rem' }}
                    >
                      Submit First Request
                    </Button>
                  </TableCell>
                </TableRow>
              ) : (
                recentLeaves.slice(0, 6).map((leave) => {
                  const days = leave.requestedDays ?? leave.days ?? 1;
                  const isPending = leave.status === 'PENDING';

                  return (
                    <TableRow key={leave.id} hover sx={{ '&:last-child td, &:last-child th': { border: 0 } }}>
                      <TableCell sx={{ fontWeight: 600, color: '#16181B' }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <span>{leave.leaveType?.name || 'Leave'}</span>
                          {leave.requiresDualApproval && (
                            <Chip
                              label="Dual Approval"
                              size="small"
                              sx={{
                                fontSize: '0.625rem',
                                height: 18,
                                backgroundColor: 'rgba(184, 151, 108, 0.12)',
                                color: '#8C6E45',
                                fontWeight: 600,
                              }}
                            />
                          )}
                        </Box>
                      </TableCell>
                      <TableCell sx={{ color: '#4B5563', fontSize: '0.85rem' }}>
                        {leave.startDate} to {leave.endDate}
                      </TableCell>
                      <TableCell sx={{ fontWeight: 600, fontSize: '0.875rem' }}>
                        {days} {days === 1 ? 'day' : 'days'}
                      </TableCell>
                      <TableCell
                        sx={{
                          color: '#6B7280',
                          maxWidth: 240,
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          fontSize: '0.85rem',
                        }}
                      >
                        {leave.reason || '-'}
                      </TableCell>
                      <TableCell>{getStatusChip(leave.status)}</TableCell>
                      <TableCell align="right">
                        {isPending ? (
                          <Button
                            size="small"
                            variant="outlined"
                            color="error"
                            startIcon={<CancelOutlinedIcon style={{ fontSize: 14 }} />}
                            onClick={() => handleOpenCancelDialog(leave.id)}
                            sx={{
                              fontSize: '0.75rem',
                              py: 0.25,
                              px: 1,
                              borderColor: '#ECCACA',
                              color: '#8C2D2D',
                              '&:hover': {
                                backgroundColor: '#FAF0F0',
                                borderColor: '#8C2D2D',
                              },
                            }}
                          >
                            Cancel
                          </Button>
                        ) : (
                          <Typography variant="caption" sx={{ color: '#9CA3AF' }}>
                            -
                          </Typography>
                        )}
                      </TableCell>
                    </TableRow>
                  );
                })
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      {/* Confirmation Dialog for Request Cancellation */}
      <Dialog
        open={cancelTargetId !== null}
        onClose={handleCloseCancelDialog}
        aria-labelledby="cancel-dialog-title"
        aria-describedby="cancel-dialog-description"
        PaperProps={{
          sx: {
            borderRadius: 2,
            border: '1px solid #EAE6DF',
            p: 1,
          },
        }}
      >
        <DialogTitle id="cancel-dialog-title" sx={{ fontFamily: '"Playfair Display", Georgia, serif', fontWeight: 600 }}>
          Cancel Leave Request?
        </DialogTitle>
        <DialogContent>
          <DialogContentText id="cancel-dialog-description" sx={{ color: '#4B5563', fontSize: '0.9rem' }}>
            Are you sure you want to cancel this pending leave application? Once cancelled, your allocated quota balance will be immediately restored.
          </DialogContentText>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={handleCloseCancelDialog} disabled={cancelling} sx={{ color: '#6B7280' }}>
            Keep Request
          </Button>
          <Button
            onClick={handleConfirmCancel}
            variant="contained"
            color="error"
            disabled={cancelling}
            sx={{
              backgroundColor: '#8C2D2D',
              '&:hover': { backgroundColor: '#702424' },
            }}
          >
            {cancelling ? 'Cancelling...' : 'Confirm Cancellation'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default Dashboard;
