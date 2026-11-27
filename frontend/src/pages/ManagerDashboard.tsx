import React, { useEffect, useState, useMemo, useCallback } from 'react';
import {
  Box,
  Grid,
  Card,
  CardContent,
  Typography,
  Button,
  Chip,
  Tabs,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  Paper,
  TextField,
  InputAdornment,
  IconButton,
  Alert,
  Stack,
  Tooltip,
  MenuItem,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Avatar,
} from '@mui/material';
import PendingActionsOutlinedIcon from '@mui/icons-material/PendingActionsOutlined';
import GroupsOutlinedIcon from '@mui/icons-material/GroupsOutlined';
import EventBusyOutlinedIcon from '@mui/icons-material/EventBusyOutlined';
import AssessmentOutlinedIcon from '@mui/icons-material/AssessmentOutlined';
import RefreshOutlinedIcon from '@mui/icons-material/RefreshOutlined';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined';
import PersonSearchOutlinedIcon from '@mui/icons-material/PersonSearchOutlined';
import AccountTreeOutlinedIcon from '@mui/icons-material/AccountTreeOutlined';
import ErrorOutlineOutlinedIcon from '@mui/icons-material/ErrorOutlineOutlined';
import EventAvailableOutlinedIcon from '@mui/icons-material/EventAvailableOutlined';
import CalendarMonthOutlinedIcon from '@mui/icons-material/CalendarMonthOutlined';
import apiClient from '../api/client';
import { useAuth } from '../context/AuthContext';
import { useNotification } from '../context/NotificationContext';
import { Employee, LeaveRequest, TeamHierarchy } from '../types';
import LoadingSpinner from '../components/common/LoadingSpinner';
import PageHeader from '../components/common/PageHeader';
import LeaveStatusChip from '../components/common/LeaveStatusChip';
import TeamLeaveCalendar from '../components/manager/TeamLeaveCalendar';
import EmployeeLeaveDrawer from '../components/manager/EmployeeLeaveDrawer';

type ManagerWorkspaceTab = 'OVERVIEW' | 'CALENDAR' | 'TEAM';

const formatTodayIso = (): string => {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const addDaysIso = (baseIso: string, days: number): string => {
  const d = new Date(`${baseIso}T00:00:00`);
  d.setDate(d.getDate() + days);
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

export const ManagerDashboard: React.FC = () => {
  const { employee } = useAuth();
  const { showSuccess, showError } = useNotification();

  // Backend data state
  const [teamMembers, setTeamMembers] = useState<Employee[]>([]);
  const [teamLeaves, setTeamLeaves] = useState<LeaveRequest[]>([]);
  const [teamHierarchy, setTeamHierarchy] = useState<TeamHierarchy | null>(null);

  // Loading & error state
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Navigation & filter state
  const [activeTab, setActiveTab] = useState<ManagerWorkspaceTab>('OVERVIEW');
  const [historyStatusFilter, setHistoryStatusFilter] = useState<string>('ALL');
  const [historySearch, setHistorySearch] = useState<string>('');
  const [historyPage, setHistoryPage] = useState<number>(0);
  const [historyRowsPerPage, setHistoryRowsPerPage] = useState<number>(10);
  const [teamSearch, setTeamSearch] = useState<string>('');

  // Employee drill-down drawer
  const [inspectedEmployee, setInspectedEmployee] = useState<Employee | null>(null);

  // Approval / Rejection action modals
  const [approveTarget, setApproveTarget] = useState<LeaveRequest | null>(null);
  const [rejectTarget, setRejectTarget] = useState<LeaveRequest | null>(null);
  const [rejectReason, setRejectReason] = useState<string>('');
  const [rejectReasonError, setRejectReasonError] = useState<string | null>(null);
  const [processingDecision, setProcessingDecision] = useState<boolean>(false);

  const fetchManagerData = useCallback(
    async (isRefresh = false) => {
      if (isRefresh) setRefreshing(true);
      else setLoading(true);
      setErrorMsg(null);

      try {
        const [teamRes, leavesRes, hierarchyRes] = await Promise.all([
          apiClient.get<Employee[]>('/api/managers/me/team'),
          apiClient.get<LeaveRequest[]>('/api/managers/me/leave-requests'),
          apiClient.get<TeamHierarchy>('/api/managers/me/team-hierarchy').catch(() => ({ data: null })),
        ]);

        setTeamMembers(teamRes.data || []);
        const sortedLeaves = [...(leavesRes.data || [])].sort((a, b) =>
          (b.appliedAt || b.startDate || '').localeCompare(a.appliedAt || a.startDate || '')
        );
        setTeamLeaves(sortedLeaves);
        setTeamHierarchy(hierarchyRes.data || null);

        if (isRefresh) showSuccess('Manager dashboard refreshed with live team data.');
      } catch (err: unknown) {
        const axiosErr = err as { response?: { status?: number; data?: { message?: string } } };
        const serverMsg = axiosErr.response?.data?.message;
        if (axiosErr.response?.status === 403) {
          setErrorMsg(serverMsg || 'You lack LEAVE_VIEW_TEAM authorization to view manager team records.');
        } else {
          setErrorMsg(
            serverMsg ||
              'Unable to load manager team data. Ensure your user account is linked to a manager employee profile.'
          );
        }
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [showSuccess]
  );

  useEffect(() => {
    fetchManagerData(false);
  }, [fetchManagerData]);

  // Derived metrics
  const todayIso = useMemo(() => formatTodayIso(), []);
  const twoWeeksIso = useMemo(() => addDaysIso(todayIso, 14), [todayIso]);

  const pendingApprovals = useMemo(() => {
    return teamLeaves.filter((l) => l.status === 'PENDING' || l.status === 'PARTIALLY_APPROVED');
  }, [teamLeaves]);

  const pendingRequestedDays = useMemo(() => {
    return pendingApprovals.reduce((sum, l) => sum + (l.requestedDays ?? l.days ?? 0), 0);
  }, [pendingApprovals]);

  const onLeaveToday = useMemo(() => {
    return teamLeaves.filter(
      (l) => l.status === 'APPROVED' && l.startDate <= todayIso && l.endDate >= todayIso
    );
  }, [teamLeaves, todayIso]);

  const upcomingApprovedTeamLeaves = useMemo(() => {
    return teamLeaves
      .filter((l) => l.status === 'APPROVED' && l.startDate > todayIso && l.startDate <= twoWeeksIso)
      .sort((a, b) => a.startDate.localeCompare(b.startDate));
  }, [teamLeaves, todayIso, twoWeeksIso]);

  const approvedDaysTotal = useMemo(() => {
    return teamLeaves
      .filter((l) => l.status === 'APPROVED')
      .reduce((sum, l) => sum + (l.requestedDays ?? l.days ?? 0), 0);
  }, [teamLeaves]);

  const approvedCount = useMemo(() => teamLeaves.filter((l) => l.status === 'APPROVED').length, [teamLeaves]);
  const rejectedCount = useMemo(() => teamLeaves.filter((l) => l.status === 'REJECTED').length, [teamLeaves]);

  // Filtered & paginated Team Leave History
  const filteredHistory = useMemo(() => {
    const q = historySearch.trim().toLowerCase();
    return teamLeaves.filter((req) => {
      if (historyStatusFilter !== 'ALL' && req.status !== historyStatusFilter) {
        return false;
      }
      if (!q) return true;
      const empName = `${req.employee?.firstName || ''} ${req.employee?.lastName || ''}`.toLowerCase();
      const empCode = req.employee?.employeeCode?.toLowerCase() || '';
      const leaveType = req.leaveType?.name?.toLowerCase() || '';
      const reason = req.reason?.toLowerCase() || '';
      return (
        empName.includes(q) ||
        empCode.includes(q) ||
        leaveType.includes(q) ||
        reason.includes(q) ||
        req.startDate.includes(q) ||
        req.endDate.includes(q)
      );
    });
  }, [teamLeaves, historyStatusFilter, historySearch]);

  const paginatedHistory = useMemo(() => {
    const startIdx = historyPage * historyRowsPerPage;
    return filteredHistory.slice(startIdx, startIdx + historyRowsPerPage);
  }, [filteredHistory, historyPage, historyRowsPerPage]);

  // Team directory metrics per employee
  const employeeLeaveStats = useMemo(() => {
    const stats = new Map<number, { pending: number; approvedDays: number; upcoming: number }>();
    teamLeaves.forEach((req) => {
      const empId = req.employee?.id;
      if (!empId) return;
      const current = stats.get(empId) || { pending: 0, approvedDays: 0, upcoming: 0 };
      if (req.status === 'PENDING' || req.status === 'PARTIALLY_APPROVED') {
        current.pending += 1;
      } else if (req.status === 'APPROVED') {
        current.approvedDays += req.requestedDays ?? req.days ?? 0;
        if (req.endDate >= todayIso) {
          current.upcoming += 1;
        }
      }
      stats.set(empId, current);
    });
    return stats;
  }, [teamLeaves, todayIso]);

  const filteredTeamMembers = useMemo(() => {
    const q = teamSearch.trim().toLowerCase();
    if (!q) return teamMembers;
    return teamMembers.filter((m) => {
      const fullName = `${m.firstName} ${m.lastName}`.toLowerCase();
      return (
        fullName.includes(q) ||
        m.employeeCode.toLowerCase().includes(q) ||
        (m.email || '').toLowerCase().includes(q) ||
        (m.department?.name || '').toLowerCase().includes(q)
      );
    });
  }, [teamMembers, teamSearch]);

  // Handle approval action
  const handleConfirmApprove = async () => {
    if (!approveTarget) return;
    setProcessingDecision(true);
    try {
      await apiClient.put(`/api/leave-requests/${approveTarget.id}/approve`);
      showSuccess(
        `Approved ${approveTarget.employee?.firstName || 'employee'}'s ${approveTarget.leaveType?.name || 'leave'} request.`
      );
      setApproveTarget(null);
      await fetchManagerData(false);
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      showError(axiosErr.response?.data?.message || 'Unable to approve leave request.');
    } finally {
      setProcessingDecision(false);
    }
  };

  // Handle rejection action
  const handleConfirmReject = async () => {
    if (!rejectTarget) return;
    const trimmed = rejectReason.trim();
    if (!trimmed) {
      setRejectReasonError('Rejection reason is mandatory.');
      return;
    }
    if (trimmed.length > 500) {
      setRejectReasonError('Rejection reason must not exceed 500 characters.');
      return;
    }

    setProcessingDecision(true);
    try {
      await apiClient.put(`/api/leave-requests/${rejectTarget.id}/reject`, { reason: trimmed });
      showSuccess(
        `Rejected ${rejectTarget.employee?.firstName || 'employee'}'s ${rejectTarget.leaveType?.name || 'leave'} request.`
      );
      setRejectTarget(null);
      setRejectReason('');
      setRejectReasonError(null);
      await fetchManagerData(false);
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      showError(axiosErr.response?.data?.message || 'Unable to reject leave request.');
    } finally {
      setProcessingDecision(false);
    }
  };

  if (loading) {
    return <LoadingSpinner message="Loading manager team overview and pending approvals..." minHeight="55vh" />;
  }

  const departmentLabel = teamHierarchy?.departmentName || employee?.department?.name || 'Managed Team';
  const totalOrgSpan = teamHierarchy?.totalTeamSize ?? teamMembers.length;

  return (
    <Box sx={{ pb: 4 }}>
      {/* Executive Page Header */}
      <PageHeader
        category="MANAGER WORKSPACE"
        title="Manager Dashboard"
        subtitle={`Real-time pending approvals, leave calendar, and employee quota visibility for ${departmentLabel}.`}
        action={
          <Stack direction="row" spacing={1.5} alignItems="center">
            {pendingApprovals.length > 0 && (
              <Chip
                icon={<PendingActionsOutlinedIcon style={{ fontSize: 16 }} />}
                label={`${pendingApprovals.length} Pending Approval${pendingApprovals.length === 1 ? '' : 's'}`}
                sx={{
                  backgroundColor: '#FDF8EB',
                  color: '#8C6B1C',
                  border: '1px solid #F4E3B8',
                  fontWeight: 600,
                }}
              />
            )}
            <Tooltip title="Refresh team data">
              <span>
                <IconButton
                  onClick={() => fetchManagerData(true)}
                  disabled={refreshing}
                  size="small"
                  sx={{
                    border: '1px solid #EAE6DF',
                    backgroundColor: '#FFFFFF',
                    color: '#16181B',
                    '&:hover': { backgroundColor: '#FAF9F6', borderColor: '#B8976C' },
                  }}
                >
                  <RefreshOutlinedIcon fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
          </Stack>
        }
      />

      {/* Backend Error Banner */}
      {errorMsg && (
        <Alert
          severity="error"
          icon={<ErrorOutlineOutlinedIcon fontSize="small" />}
          action={
            <Button color="inherit" size="small" onClick={() => fetchManagerData(false)}>
              Retry
            </Button>
          }
          sx={{
            mb: 3,
            backgroundColor: '#FAF0F0',
            color: '#8C2D2D',
            border: '1px solid #ECCACA',
          }}
        >
          {errorMsg}
        </Alert>
      )}

      {/* 4 Summary KPI Cards */}
      <Grid container spacing={2.5} sx={{ mb: 4 }}>
        {/* Card 1: Pending Approval Count */}
        <Grid item xs={12} sm={6} md={3}>
          <Card
            sx={{
              borderLeft: '4px solid #8C6B1C',
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
                  PENDING APPROVALS
                </Typography>
                <PendingActionsOutlinedIcon sx={{ color: '#8C6B1C', fontSize: 22 }} />
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
                {pendingApprovals.length}
                <Typography component="span" variant="body2" sx={{ color: '#6B7280', ml: 1, fontWeight: 500 }}>
                  request{pendingApprovals.length === 1 ? '' : 's'}
                </Typography>
              </Typography>
              <Typography variant="caption" sx={{ color: '#6B7280', display: 'block', mt: 0.75 }}>
                {pendingRequestedDays} working day{pendingRequestedDays === 1 ? '' : 's'} awaiting decision
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        {/* Card 2: Direct Team & Hierarchy Size */}
        <Grid item xs={12} sm={6} md={3}>
          <Card
            sx={{
              borderLeft: '4px solid #B8976C',
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
                  MANAGED TEAM
                </Typography>
                <GroupsOutlinedIcon sx={{ color: '#B8976C', fontSize: 22 }} />
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
                {teamMembers.length}
                <Typography component="span" variant="body2" sx={{ color: '#6B7280', ml: 1, fontWeight: 500 }}>
                  direct
                </Typography>
              </Typography>
              <Typography variant="caption" sx={{ color: '#6B7280', display: 'block', mt: 0.75 }}>
                {totalOrgSpan} total in reporting hierarchy
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        {/* Card 3: Away Today & Next 14 Days */}
        <Grid item xs={12} sm={6} md={3}>
          <Card
            sx={{
              borderLeft: '4px solid #235A36',
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
                  sx={{ color: '#235A36', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
                >
                  ON LEAVE TODAY
                </Typography>
                <EventBusyOutlinedIcon sx={{ color: '#235A36', fontSize: 22 }} />
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
                {onLeaveToday.length}
                <Typography component="span" variant="body2" sx={{ color: '#6B7280', ml: 1, fontWeight: 500 }}>
                  away
                </Typography>
              </Typography>
              <Typography variant="caption" sx={{ color: '#6B7280', display: 'block', mt: 0.75 }}>
                {upcomingApprovedTeamLeaves.length} upcoming in next 14 days
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        {/* Card 4: Team Leave Summary */}
        <Grid item xs={12} sm={6} md={3}>
          <Card
            sx={{
              borderLeft: '4px solid #111315',
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
                  APPROVED TEAM DAYS
                </Typography>
                <AssessmentOutlinedIcon sx={{ color: '#111315', fontSize: 22 }} />
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
                {approvedDaysTotal}
                <Typography component="span" variant="body2" sx={{ color: '#6B7280', ml: 1, fontWeight: 500 }}>
                  days
                </Typography>
              </Typography>
              <Typography variant="caption" sx={{ color: '#6B7280', display: 'block', mt: 0.75 }}>
                {approvedCount} approved · {rejectedCount} rejected
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Workspace Navigation Tabs */}
      <Card
        sx={{
          mb: 3,
          border: '1px solid #EAE6DF',
          backgroundColor: '#FFFFFF',
          boxShadow: '0 2px 6px rgba(17, 19, 21, 0.03)',
        }}
      >
        <Tabs
          value={activeTab}
          onChange={(_, val) => setActiveTab(val)}
          variant="scrollable"
          scrollButtons="auto"
          sx={{
            px: 2,
            '& .MuiTabs-indicator': {
              backgroundColor: '#B8976C',
              height: 2.5,
            },
            '& .MuiTab-root': {
              textTransform: 'none',
              fontWeight: 500,
              fontSize: '0.9rem',
              color: '#6B7280',
              py: 1.75,
              '&.Mui-selected': {
                color: '#16181B',
                fontWeight: 600,
              },
            },
          }}
        >
          <Tab
            icon={<PendingActionsOutlinedIcon fontSize="small" />}
            iconPosition="start"
            label={`Overview & Approvals (${pendingApprovals.length})`}
            value="OVERVIEW"
          />
          <Tab
            icon={<CalendarMonthOutlinedIcon fontSize="small" />}
            iconPosition="start"
            label="Team Leave Calendar"
            value="CALENDAR"
          />
          <Tab
            icon={<GroupsOutlinedIcon fontSize="small" />}
            iconPosition="start"
            label={`Employee Leave Info (${teamMembers.length})`}
            value="TEAM"
          />
        </Tabs>
      </Card>

      {/* TAB 1: Overview, Pending Approvals Queue & Full Team Requests */}
      {activeTab === 'OVERVIEW' && (
        <Stack spacing={4}>
          {/* Actionable Pending Approvals Queue + Team Coverage */}
          <Grid container spacing={3}>
            <Grid item xs={12} lg={8}>
              <Card
                sx={{
                  border: '1px solid #EAE6DF',
                  backgroundColor: '#FFFFFF',
                  height: '100%',
                  boxShadow: '0 2px 8px rgba(17, 19, 21, 0.04)',
                }}
              >
                <Box
                  sx={{
                    p: 2.5,
                    borderBottom: '1px solid #EAE6DF',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    flexWrap: 'wrap',
                    gap: 1,
                  }}
                >
                  <Box>
                    <Typography
                      variant="subtitle2"
                      sx={{ color: '#B8976C', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
                    >
                      DECISION QUEUE
                    </Typography>
                    <Typography
                      variant="h6"
                      sx={{ fontFamily: '"Playfair Display", Georgia, serif', fontWeight: 600, color: '#16181B' }}
                    >
                      Pending Approval Requests ({pendingApprovals.length})
                    </Typography>
                  </Box>
                </Box>

                {pendingApprovals.length === 0 ? (
                  <Box sx={{ p: 5, textAlign: 'center' }}>
                    <CheckCircleOutlineIcon sx={{ fontSize: 40, color: '#235A36', mb: 1 }} />
                    <Typography variant="subtitle1" sx={{ fontWeight: 600, color: '#16181B' }}>
                      No Pending Team Approvals
                    </Typography>
                    <Typography variant="body2" sx={{ color: '#6B7280', mt: 0.5 }}>
                      All leave requests submitted by your direct reports have been processed.
                    </Typography>
                  </Box>
                ) : (
                  <TableContainer component={Paper} elevation={0}>
                    <Table sx={{ minWidth: 620 }}>
                      <TableHead sx={{ backgroundColor: '#FAF9F6' }}>
                        <TableRow>
                          <TableCell sx={{ fontWeight: 600, fontSize: '0.75rem', color: '#16181B' }}>
                            EMPLOYEE
                          </TableCell>
                          <TableCell sx={{ fontWeight: 600, fontSize: '0.75rem', color: '#16181B' }}>
                            LEAVE TYPE & DATES
                          </TableCell>
                          <TableCell sx={{ fontWeight: 600, fontSize: '0.75rem', color: '#16181B' }}>
                            DAYS
                          </TableCell>
                          <TableCell sx={{ fontWeight: 600, fontSize: '0.75rem', color: '#16181B' }}>
                            STATUS
                          </TableCell>
                          <TableCell align="right" sx={{ fontWeight: 600, fontSize: '0.75rem', color: '#16181B' }}>
                            DECISION
                          </TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {pendingApprovals.map((req) => {
                          const days = req.requestedDays ?? req.days ?? 1;
                          return (
                            <TableRow key={req.id} hover sx={{ '&:last-child td': { border: 0 } }}>
                              <TableCell>
                                <Typography variant="body2" sx={{ fontWeight: 600, color: '#16181B' }}>
                                  {req.employee
                                    ? `${req.employee.firstName} ${req.employee.lastName}`
                                    : 'Team Member'}
                                </Typography>
                                <Typography variant="caption" sx={{ color: '#6B7280' }}>
                                  {req.employee?.employeeCode}
                                </Typography>
                              </TableCell>
                              <TableCell>
                                <Typography variant="body2" sx={{ fontWeight: 500, color: '#16181B' }}>
                                  {req.leaveType?.name || 'Leave'}
                                </Typography>
                                <Typography variant="caption" sx={{ color: '#6B7280', display: 'block' }}>
                                  {req.startDate} to {req.endDate}
                                </Typography>
                                {req.reason && (
                                  <Typography
                                    variant="caption"
                                    sx={{
                                      color: '#6B7280',
                                      display: 'block',
                                      maxWidth: 220,
                                      whiteSpace: 'nowrap',
                                      overflow: 'hidden',
                                      textOverflow: 'ellipsis',
                                    }}
                                  >
                                    {req.reason}
                                  </Typography>
                                )}
                              </TableCell>
                              <TableCell sx={{ fontWeight: 600 }}>{days} d</TableCell>
                              <TableCell>
                                <LeaveStatusChip status={req.status} />
                              </TableCell>
                              <TableCell align="right">
                                <Stack direction="row" spacing={0.75} justifyContent="flex-end">
                                  {req.employee && (
                                    <Tooltip title="Inspect employee balance & history">
                                      <IconButton
                                        size="small"
                                        onClick={() => setInspectedEmployee(req.employee!)}
                                        sx={{
                                          border: '1px solid #EAE6DF',
                                          color: '#4B5563',
                                          '&:hover': { borderColor: '#B8976C', color: '#16181B' },
                                        }}
                                      >
                                        <PersonSearchOutlinedIcon fontSize="small" />
                                      </IconButton>
                                    </Tooltip>
                                  )}
                                  <Button
                                    size="small"
                                    variant="contained"
                                    onClick={() => setApproveTarget(req)}
                                    sx={{
                                      fontSize: '0.725rem',
                                      py: 0.4,
                                      px: 1.25,
                                      backgroundColor: '#235A36',
                                      color: '#FFFFFF',
                                      '&:hover': { backgroundColor: '#1B462A' },
                                    }}
                                  >
                                    Approve
                                  </Button>
                                  <Button
                                    size="small"
                                    variant="outlined"
                                    onClick={() => {
                                      setRejectTarget(req);
                                      setRejectReason('');
                                      setRejectReasonError(null);
                                    }}
                                    sx={{
                                      fontSize: '0.725rem',
                                      py: 0.4,
                                      px: 1.25,
                                      borderColor: '#ECCACA',
                                      color: '#8C2D2D',
                                      '&:hover': { backgroundColor: '#FAF0F0', borderColor: '#8C2D2D' },
                                    }}
                                  >
                                    Reject
                                  </Button>
                                </Stack>
                              </TableCell>
                            </TableRow>
                          );
                        })}
                      </TableBody>
                    </Table>
                  </TableContainer>
                )}
              </Card>
            </Grid>

            {/* Right Panel: Who's Away Today & Upcoming Absences */}
            <Grid item xs={12} lg={4}>
              <Card
                sx={{
                  border: '1px solid #EAE6DF',
                  backgroundColor: '#FFFFFF',
                  height: '100%',
                  boxShadow: '0 2px 8px rgba(17, 19, 21, 0.04)',
                }}
              >
                <Box sx={{ p: 2.5, borderBottom: '1px solid #EAE6DF', backgroundColor: '#FAF9F6' }}>
                  <Typography
                    variant="subtitle2"
                    sx={{ color: '#B8976C', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
                  >
                    TEAM AVAILABILITY
                  </Typography>
                  <Typography
                    variant="h6"
                    sx={{ fontFamily: '"Playfair Display", Georgia, serif', fontWeight: 600, color: '#16181B' }}
                  >
                    Away Today & Upcoming
                  </Typography>
                </Box>

                <CardContent sx={{ p: 2.5 }}>
                  <Typography
                    variant="caption"
                    sx={{ color: '#6B7280', fontWeight: 700, display: 'block', mb: 1 }}
                  >
                    AWAY TODAY ({onLeaveToday.length})
                  </Typography>

                  {onLeaveToday.length === 0 ? (
                    <Box
                      sx={{
                        p: 1.5,
                        borderRadius: 1.25,
                        backgroundColor: '#FAF9F6',
                        border: '1px solid #EAE6DF',
                        mb: 2.5,
                      }}
                    >
                      <Typography variant="caption" sx={{ color: '#4B5563' }}>
                        All direct team members are scheduled in today.
                      </Typography>
                    </Box>
                  ) : (
                    <Stack spacing={1} sx={{ mb: 2.5 }}>
                      {onLeaveToday.map((leave) => (
                        <Box
                          key={leave.id}
                          sx={{
                            p: 1.5,
                            borderRadius: 1.25,
                            backgroundColor: '#EBF5EE',
                            border: '1px solid #D1E8D7',
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                          }}
                        >
                          <Box>
                            <Typography variant="body2" sx={{ fontWeight: 600, color: '#16181B' }}>
                              {leave.employee?.firstName} {leave.employee?.lastName}
                            </Typography>
                            <Typography variant="caption" sx={{ color: '#235A36' }}>
                              {leave.leaveType?.name} · Returns after {leave.endDate}
                            </Typography>
                          </Box>
                        </Box>
                      ))}
                    </Stack>
                  )}

                  <Typography
                    variant="caption"
                    sx={{ color: '#6B7280', fontWeight: 700, display: 'block', mb: 1 }}
                  >
                    UPCOMING NEXT 14 DAYS ({upcomingApprovedTeamLeaves.length})
                  </Typography>

                  {upcomingApprovedTeamLeaves.length === 0 ? (
                    <Box
                      sx={{
                        p: 1.5,
                        borderRadius: 1.25,
                        backgroundColor: '#FAF9F6',
                        border: '1px solid #EAE6DF',
                      }}
                    >
                      <Typography variant="caption" sx={{ color: '#6B7280' }}>
                        No approved team leaves scheduled in the next two weeks.
                      </Typography>
                    </Box>
                  ) : (
                    <Stack spacing={1}>
                      {upcomingApprovedTeamLeaves.slice(0, 5).map((leave) => (
                        <Box
                          key={leave.id}
                          sx={{
                            p: 1.5,
                            borderRadius: 1.25,
                            backgroundColor: '#FAF9F6',
                            border: '1px solid #EAE6DF',
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                          }}
                        >
                          <Box>
                            <Typography variant="body2" sx={{ fontWeight: 600, color: '#16181B' }}>
                              {leave.employee?.firstName} {leave.employee?.lastName}
                            </Typography>
                            <Typography variant="caption" sx={{ color: '#6B7280' }}>
                              {leave.startDate} to {leave.endDate} · {leave.leaveType?.name}
                            </Typography>
                          </Box>
                          <Chip
                            size="small"
                            label={`${leave.requestedDays ?? leave.days ?? 1}d`}
                            sx={{ height: 20, fontSize: '0.7rem', fontWeight: 600 }}
                          />
                        </Box>
                      ))}
                    </Stack>
                  )}
                </CardContent>
              </Card>
            </Grid>
          </Grid>

          {/* Complete Team Leave Overview Table */}
          <Card sx={{ border: '1px solid #EAE6DF', backgroundColor: '#FFFFFF' }}>
            <Box
              sx={{
                p: 2.5,
                borderBottom: '1px solid #EAE6DF',
                display: 'flex',
                flexDirection: { xs: 'column', sm: 'row' },
                justifyContent: 'space-between',
                alignItems: { xs: 'stretch', sm: 'center' },
                gap: 2,
              }}
            >
              <Box>
                <Typography
                  variant="subtitle2"
                  sx={{ color: '#B8976C', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
                >
                  TEAM LEAVE OVERVIEW
                </Typography>
                <Typography
                  variant="h6"
                  sx={{ fontFamily: '"Playfair Display", Georgia, serif', fontWeight: 600, color: '#16181B' }}
                >
                  All Team Leave Requests
                </Typography>
              </Box>

              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
                <TextField
                  size="small"
                  placeholder="Search employee, type, reason..."
                  value={historySearch}
                  onChange={(e) => {
                    setHistorySearch(e.target.value);
                    setHistoryPage(0);
                  }}
                  sx={{ minWidth: 230 }}
                  InputProps={{
                    startAdornment: (
                      <InputAdornment position="start">
                        <SearchIcon fontSize="small" sx={{ color: '#6B7280' }} />
                      </InputAdornment>
                    ),
                    endAdornment: historySearch ? (
                      <InputAdornment position="end">
                        <IconButton size="small" onClick={() => setHistorySearch('')}>
                          <ClearIcon fontSize="small" />
                        </IconButton>
                      </InputAdornment>
                    ) : null,
                  }}
                />
                <TextField
                  select
                  size="small"
                  label="Status"
                  value={historyStatusFilter}
                  onChange={(e) => {
                    setHistoryStatusFilter(e.target.value);
                    setHistoryPage(0);
                  }}
                  sx={{ minWidth: 150 }}
                >
                  <MenuItem value="ALL">All Statuses</MenuItem>
                  <MenuItem value="PENDING">Pending</MenuItem>
                  <MenuItem value="PARTIALLY_APPROVED">Partial Approval</MenuItem>
                  <MenuItem value="APPROVED">Approved</MenuItem>
                  <MenuItem value="REJECTED">Rejected</MenuItem>
                  <MenuItem value="CANCELLED">Cancelled</MenuItem>
                </TextField>
              </Stack>
            </Box>

            <TableContainer component={Paper} elevation={0}>
              <Table sx={{ minWidth: 720 }}>
                <TableHead sx={{ backgroundColor: '#FAF9F6' }}>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 600, fontSize: '0.775rem', color: '#16181B' }}>EMPLOYEE</TableCell>
                    <TableCell sx={{ fontWeight: 600, fontSize: '0.775rem', color: '#16181B' }}>LEAVE TYPE</TableCell>
                    <TableCell sx={{ fontWeight: 600, fontSize: '0.775rem', color: '#16181B' }}>DURATION</TableCell>
                    <TableCell sx={{ fontWeight: 600, fontSize: '0.775rem', color: '#16181B' }}>DAYS</TableCell>
                    <TableCell sx={{ fontWeight: 600, fontSize: '0.775rem', color: '#16181B' }}>REASON</TableCell>
                    <TableCell sx={{ fontWeight: 600, fontSize: '0.775rem', color: '#16181B' }}>STATUS</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 600, fontSize: '0.775rem', color: '#16181B' }}>
                      LEAVE INFO
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {paginatedHistory.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={7} align="center" sx={{ py: 5, color: '#6B7280' }}>
                        <EventAvailableOutlinedIcon sx={{ fontSize: 34, color: '#B8976C', mb: 1, display: 'block', mx: 'auto' }} />
                        No team leave requests match the current filter criteria.
                      </TableCell>
                    </TableRow>
                  ) : (
                    paginatedHistory.map((req) => (
                      <TableRow key={req.id} hover sx={{ '&:last-child td': { border: 0 } }}>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontWeight: 600, color: '#16181B' }}>
                            {req.employee ? `${req.employee.firstName} ${req.employee.lastName}` : 'Employee'}
                          </Typography>
                          <Typography variant="caption" sx={{ color: '#6B7280' }}>
                            {req.employee?.employeeCode}
                          </Typography>
                        </TableCell>
                        <TableCell sx={{ fontWeight: 500 }}>{req.leaveType?.name || 'Leave'}</TableCell>
                        <TableCell sx={{ color: '#4B5563', fontSize: '0.85rem' }}>
                          {req.startDate} to {req.endDate}
                        </TableCell>
                        <TableCell sx={{ fontWeight: 600 }}>{req.requestedDays ?? req.days ?? 1}</TableCell>
                        <TableCell
                          sx={{
                            color: '#6B7280',
                            maxWidth: 220,
                            whiteSpace: 'nowrap',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                          }}
                        >
                          {req.reason || '—'}
                        </TableCell>
                        <TableCell>
                          <LeaveStatusChip status={req.status} />
                        </TableCell>
                        <TableCell align="right">
                          {req.employee && (
                            <Button
                              size="small"
                              variant="outlined"
                              startIcon={<PersonSearchOutlinedIcon fontSize="small" />}
                              onClick={() => setInspectedEmployee(req.employee!)}
                              sx={{
                                fontSize: '0.725rem',
                                borderColor: '#D4CEBE',
                                color: '#16181B',
                                '&:hover': { borderColor: '#B8976C', backgroundColor: 'rgba(184, 151, 108, 0.06)' },
                              }}
                            >
                              Balances
                            </Button>
                          )}
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </TableContainer>

            <TablePagination
              component="div"
              count={filteredHistory.length}
              page={historyPage}
              onPageChange={(_, newPage) => setHistoryPage(newPage)}
              rowsPerPage={historyRowsPerPage}
              onRowsPerPageChange={(e) => {
                setHistoryRowsPerPage(parseInt(e.target.value, 10));
                setHistoryPage(0);
              }}
              rowsPerPageOptions={[5, 10, 25]}
              sx={{ borderTop: '1px solid #EAE6DF', backgroundColor: '#FAF9F6' }}
            />
          </Card>
        </Stack>
      )}

      {/* TAB 2: Team Leave Calendar */}
      {activeTab === 'CALENDAR' && (
        <TeamLeaveCalendar
          leaveRequests={teamLeaves}
          teamSize={teamMembers.length}
          onInspectEmployee={setInspectedEmployee}
        />
      )}

      {/* TAB 3: Employee Leave Information & Team Roster */}
      {activeTab === 'TEAM' && (
        <Stack spacing={3}>
          <Card sx={{ border: '1px solid #EAE6DF', backgroundColor: '#FFFFFF' }}>
            <Box
              sx={{
                p: 2.5,
                borderBottom: '1px solid #EAE6DF',
                display: 'flex',
                flexDirection: { xs: 'column', sm: 'row' },
                justifyContent: 'space-between',
                alignItems: { xs: 'stretch', sm: 'center' },
                gap: 2,
              }}
            >
              <Box>
                <Typography
                  variant="subtitle2"
                  sx={{ color: '#B8976C', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
                >
                  TEAM ROSTER & QUOTAS
                </Typography>
                <Typography
                  variant="h6"
                  sx={{ fontFamily: '"Playfair Display", Georgia, serif', fontWeight: 600, color: '#16181B' }}
                >
                  Employee Leave Information
                </Typography>
              </Box>

              <TextField
                size="small"
                placeholder="Filter by employee name, code, or dept..."
                value={teamSearch}
                onChange={(e) => setTeamSearch(e.target.value)}
                sx={{ minWidth: { sm: 300 } }}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchIcon fontSize="small" sx={{ color: '#6B7280' }} />
                    </InputAdornment>
                  ),
                }}
              />
            </Box>

            <TableContainer component={Paper} elevation={0}>
              <Table sx={{ minWidth: 700 }}>
                <TableHead sx={{ backgroundColor: '#FAF9F6' }}>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 600, fontSize: '0.775rem', color: '#16181B' }}>EMPLOYEE</TableCell>
                    <TableCell sx={{ fontWeight: 600, fontSize: '0.775rem', color: '#16181B' }}>DEPARTMENT</TableCell>
                    <TableCell sx={{ fontWeight: 600, fontSize: '0.775rem', color: '#16181B' }}>STATUS</TableCell>
                    <TableCell sx={{ fontWeight: 600, fontSize: '0.775rem', color: '#16181B' }}>PENDING</TableCell>
                    <TableCell sx={{ fontWeight: 600, fontSize: '0.775rem', color: '#16181B' }}>
                      APPROVED DAYS
                    </TableCell>
                    <TableCell align="right" sx={{ fontWeight: 600, fontSize: '0.775rem', color: '#16181B' }}>
                      DETAILS
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filteredTeamMembers.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={6} align="center" sx={{ py: 5, color: '#6B7280' }}>
                        <GroupsOutlinedIcon sx={{ fontSize: 36, color: '#B8976C', mb: 1, display: 'block', mx: 'auto' }} />
                        {teamMembers.length === 0
                          ? 'No direct reports or managed department employees are assigned to your account.'
                          : 'No team members match your search filter.'}
                      </TableCell>
                    </TableRow>
                  ) : (
                    filteredTeamMembers.map((member) => {
                      const stats = employeeLeaveStats.get(member.id) || {
                        pending: 0,
                        approvedDays: 0,
                        upcoming: 0,
                      };
                      return (
                        <TableRow key={member.id} hover sx={{ '&:last-child td': { border: 0 } }}>
                          <TableCell>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                              <Avatar
                                sx={{
                                  width: 34,
                                  height: 34,
                                  bgcolor: 'rgba(184, 151, 108, 0.14)',
                                  color: '#8C6E45',
                                  fontSize: '0.85rem',
                                  fontWeight: 700,
                                }}
                              >
                                {member.firstName?.charAt(0).toUpperCase()}
                              </Avatar>
                              <Box>
                                <Typography variant="body2" sx={{ fontWeight: 600, color: '#16181B' }}>
                                  {member.firstName} {member.lastName}
                                </Typography>
                                <Typography variant="caption" sx={{ color: '#6B7280' }}>
                                  {member.employeeCode} · {member.email}
                                </Typography>
                              </Box>
                            </Box>
                          </TableCell>
                          <TableCell sx={{ color: '#4B5563' }}>{member.department?.name || '—'}</TableCell>
                          <TableCell>
                            <Chip
                              size="small"
                              label={member.status || 'ACTIVE'}
                              sx={{
                                backgroundColor: member.status === 'ACTIVE' ? '#EBF5EE' : '#F3F4F6',
                                color: member.status === 'ACTIVE' ? '#235A36' : '#4B5563',
                                fontWeight: 600,
                                fontSize: '0.7rem',
                                height: 22,
                              }}
                            />
                          </TableCell>
                          <TableCell>
                            {stats.pending > 0 ? (
                              <Chip
                                size="small"
                                label={`${stats.pending} awaiting`}
                                sx={{
                                  backgroundColor: '#FDF8EB',
                                  color: '#8C6B1C',
                                  fontWeight: 600,
                                  fontSize: '0.7rem',
                                  height: 22,
                                }}
                              />
                            ) : (
                              <Typography variant="caption" sx={{ color: '#9CA3AF' }}>
                                None
                              </Typography>
                            )}
                          </TableCell>
                          <TableCell sx={{ fontWeight: 600 }}>{stats.approvedDays} days</TableCell>
                          <TableCell align="right">
                            <Button
                              size="small"
                              variant="outlined"
                              startIcon={<PersonSearchOutlinedIcon fontSize="small" />}
                              onClick={() => setInspectedEmployee(member)}
                              sx={{
                                fontSize: '0.75rem',
                                borderColor: '#D4CEBE',
                                color: '#16181B',
                                '&:hover': { borderColor: '#B8976C', backgroundColor: 'rgba(184, 151, 108, 0.08)' },
                              }}
                            >
                              View Leave Info
                            </Button>
                          </TableCell>
                        </TableRow>
                      );
                    })
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </Card>

          {/* Reporting Hierarchy Sub-Teams Summary (if subordinate managers exist) */}
          {teamHierarchy && teamHierarchy.subordinateTeams && teamHierarchy.subordinateTeams.length > 0 && (
            <Card sx={{ border: '1px solid #EAE6DF', backgroundColor: '#FFFFFF' }}>
              <CardContent sx={{ p: 3 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                  <AccountTreeOutlinedIcon sx={{ color: '#B8976C' }} />
                  <Typography
                    variant="h6"
                    sx={{ fontFamily: '"Playfair Display", Georgia, serif', fontWeight: 600, color: '#16181B' }}
                  >
                    Subordinate Manager Teams ({teamHierarchy.subordinateTeams.length})
                  </Typography>
                </Box>
                <Grid container spacing={2}>
                  {teamHierarchy.subordinateTeams.map((sub) => (
                    <Grid item xs={12} sm={6} md={4} key={sub.managerId}>
                      <Box
                        sx={{
                          p: 2,
                          borderRadius: 1.5,
                          backgroundColor: '#FAF9F6',
                          border: '1px solid #EAE6DF',
                        }}
                      >
                        <Typography variant="body2" sx={{ fontWeight: 600, color: '#16181B' }}>
                          {sub.managerName}
                        </Typography>
                        <Typography variant="caption" sx={{ color: '#6B7280', display: 'block', mb: 1 }}>
                          {sub.departmentName || 'Sub-Team'} · {sub.totalTeamSize} subordinate(s)
                        </Typography>
                        <Typography variant="caption" sx={{ color: '#8C6E45', fontWeight: 600 }}>
                          {sub.directReports.length} direct report(s)
                        </Typography>
                      </Box>
                    </Grid>
                  ))}
                </Grid>
              </CardContent>
            </Card>
          )}
        </Stack>
      )}

      {/* Employee Leave Details Drawer */}
      <EmployeeLeaveDrawer employee={inspectedEmployee} onClose={() => setInspectedEmployee(null)} />

      {/* Approve Confirmation Dialog */}
      <Dialog
        open={Boolean(approveTarget)}
        onClose={() => !processingDecision && setApproveTarget(null)}
        PaperProps={{ sx: { borderRadius: 2, p: 1, border: '1px solid #EAE6DF' } }}
      >
        <DialogTitle sx={{ fontFamily: '"Playfair Display", Georgia, serif', fontWeight: 600 }}>
          Approve Leave Request
        </DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ color: '#4B5563', fontSize: '0.9rem' }}>
            Approve <strong>{approveTarget?.employee?.firstName} {approveTarget?.employee?.lastName}</strong>'s{' '}
            <strong>{approveTarget?.leaveType?.name}</strong> request from{' '}
            <strong>{approveTarget?.startDate}</strong> to <strong>{approveTarget?.endDate}</strong> (
            {approveTarget?.requestedDays ?? approveTarget?.days ?? 1} working day(s))?
          </DialogContentText>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setApproveTarget(null)} disabled={processingDecision} color="inherit">
            Cancel
          </Button>
          <Button
            onClick={handleConfirmApprove}
            variant="contained"
            disabled={processingDecision}
            sx={{ backgroundColor: '#235A36', '&:hover': { backgroundColor: '#1B462A' } }}
          >
            {processingDecision ? 'Approving...' : 'Confirm Approval'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Reject Confirmation Dialog with Mandatory Reason */}
      <Dialog
        open={Boolean(rejectTarget)}
        onClose={() => !processingDecision && setRejectTarget(null)}
        maxWidth="sm"
        fullWidth
        PaperProps={{ sx: { borderRadius: 2, p: 1, border: '1px solid #EAE6DF' } }}
      >
        <DialogTitle sx={{ fontFamily: '"Playfair Display", Georgia, serif', fontWeight: 600 }}>
          Reject Leave Request
        </DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ color: '#4B5563', fontSize: '0.9rem', mb: 2 }}>
            Provide a clear reason for rejecting{' '}
            <strong>{rejectTarget?.employee?.firstName} {rejectTarget?.employee?.lastName}</strong>'s leave request (
            {rejectTarget?.startDate} to {rejectTarget?.endDate}).
          </DialogContentText>
          <TextField
            autoFocus
            required
            fullWidth
            multiline
            rows={3}
            label="Rejection Reason"
            placeholder="e.g., Overlapping team coverage gap during critical sprint milestone..."
            value={rejectReason}
            onChange={(e) => {
              setRejectReason(e.target.value);
              if (rejectReasonError) setRejectReasonError(null);
            }}
            error={Boolean(rejectReasonError)}
            helperText={rejectReasonError || `${rejectReason.length}/500 characters`}
            inputProps={{ maxLength: 500 }}
          />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setRejectTarget(null)} disabled={processingDecision} color="inherit">
            Cancel
          </Button>
          <Button
            onClick={handleConfirmReject}
            variant="contained"
            color="error"
            startIcon={<CancelOutlinedIcon fontSize="small" />}
            disabled={processingDecision}
            sx={{ backgroundColor: '#8C2D2D', '&:hover': { backgroundColor: '#702323' } }}
          >
            {processingDecision ? 'Rejecting...' : 'Confirm Rejection'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ManagerDashboard;
