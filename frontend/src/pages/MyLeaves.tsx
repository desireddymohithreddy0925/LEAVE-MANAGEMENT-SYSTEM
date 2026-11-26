import React, { useEffect, useState, useCallback, useMemo } from 'react';
import {
  Card,
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
  TablePagination,
  Tabs,
  Tab,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  DialogContentText,
  Paper,
  TextField,
  InputAdornment,
  MenuItem,
  IconButton,
  Alert,
  Stack,
  Divider,
  Tooltip,
  CircularProgress,
  Grid,
} from '@mui/material';
import EventAvailableOutlinedIcon from '@mui/icons-material/EventAvailableOutlined';
import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined';
import AddIcon from '@mui/icons-material/Add';
import SearchIcon from '@mui/icons-material/Search';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import RefreshOutlinedIcon from '@mui/icons-material/RefreshOutlined';
import CloseIcon from '@mui/icons-material/Close';
import ErrorOutlineOutlinedIcon from '@mui/icons-material/ErrorOutlineOutlined';
import DateRangeOutlinedIcon from '@mui/icons-material/DateRangeOutlined';
import ClearIcon from '@mui/icons-material/Clear';
import { useNavigate } from 'react-router-dom';
import apiClient from '../api/client';
import { LeaveRequest, LeaveStatus, Page } from '../types';
import LoadingSpinner from '../components/common/LoadingSpinner';
import PageHeader from '../components/common/PageHeader';
import { useNotification } from '../context/NotificationContext';

type FilterStatus = 'ALL' | LeaveStatus;

export const MyLeaves: React.FC = () => {
  const navigate = useNavigate();
  const { showSuccess, showError } = useNotification();

  // Data & pagination state
  const [requests, setRequests] = useState<LeaveRequest[]>([]);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [page, setPage] = useState<number>(0);
  const [rowsPerPage, setRowsPerPage] = useState<number>(10);

  // Filters & search state
  const [selectedTab, setSelectedTab] = useState<FilterStatus>('ALL');
  const [selectedYear, setSelectedYear] = useState<number | 'ALL'>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');

  // Loading & error state
  const [loading, setLoading] = useState<boolean>(true);
  const [fetchingPage, setFetchingPage] = useState<boolean>(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Details modal state
  const [detailRequest, setDetailRequest] = useState<LeaveRequest | null>(null);
  const [loadingDetail, setLoadingDetail] = useState<boolean>(false);

  // Cancellation dialog state
  const [cancelTarget, setCancelTarget] = useState<LeaveRequest | null>(null);
  const [cancelling, setCancelling] = useState<boolean>(false);

  const currentYear = new Date().getFullYear();
  const yearOptions = useMemo(() => [currentYear + 1, currentYear, currentYear - 1, currentYear - 2], [currentYear]);

  const fetchLeaves = useCallback(
    async (isInitial = false) => {
      if (isInitial) {
        setLoading(true);
      } else {
        setFetchingPage(true);
      }
      setErrorMsg(null);

      try {
        const params: Record<string, string | number> = {
          page,
          size: rowsPerPage,
          sort: 'appliedAt,desc',
        };
        if (selectedTab !== 'ALL') {
          params.status = selectedTab;
        }
        if (selectedYear !== 'ALL') {
          params.year = selectedYear;
        }

        const response = await apiClient.get<Page<LeaveRequest>>('/api/leave-requests/me', { params });
        setRequests(response.data?.content || []);
        setTotalElements(response.data?.totalElements ?? (response.data?.content?.length || 0));
      } catch (err: unknown) {
        const axiosErr = err as { response?: { data?: { message?: string } } };
        const message =
          axiosErr.response?.data?.message || 'Unable to retrieve your leave history. Please try again.';
        setErrorMsg(message);
        setRequests([]);
        setTotalElements(0);
      } finally {
        setLoading(false);
        setFetchingPage(false);
      }
    },
    [page, rowsPerPage, selectedTab, selectedYear]
  );

  useEffect(() => {
    fetchLeaves(loading);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fetchLeaves]);

  // Client-side search across loaded page results
  const visibleRequests = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return requests;
    return requests.filter((req) => {
      const typeName = req.leaveType?.name?.toLowerCase() || '';
      const reason = req.reason?.toLowerCase() || '';
      const start = req.startDate?.toLowerCase() || '';
      const end = req.endDate?.toLowerCase() || '';
      const status = req.status?.toLowerCase() || '';
      return (
        typeName.includes(q) ||
        reason.includes(q) ||
        start.includes(q) ||
        end.includes(q) ||
        status.includes(q)
      );
    });
  }, [requests, searchQuery]);

  // View full leave request details (hydrating fresh from GET /api/leave-requests/{id})
  const handleOpenDetails = async (req: LeaveRequest) => {
    setDetailRequest(req);
    setLoadingDetail(true);
    try {
      const res = await apiClient.get<LeaveRequest>(`/api/leave-requests/${req.id}`);
      if (res.data) {
        setDetailRequest(res.data);
      }
    } catch {
      // Keep the row data already loaded if detail refresh fails
    } finally {
      setLoadingDetail(false);
    }
  };

  const handleConfirmCancel = async () => {
    if (!cancelTarget) return;

    setCancelling(true);
    try {
      await apiClient.put(`/api/leave-requests/${cancelTarget.id}/cancel`);
      showSuccess('Leave request cancelled successfully. Your quota balance has been restored.');
      const cancelledId = cancelTarget.id;
      setCancelTarget(null);
      if (detailRequest?.id === cancelledId) {
        setDetailRequest(null);
      }
      await fetchLeaves(false);
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      showError(axiosErr.response?.data?.message || 'Failed to cancel leave request.');
    } finally {
      setCancelling(false);
    }
  };

  const handleTabChange = (_: React.SyntheticEvent, newValue: FilterStatus) => {
    setSelectedTab(newValue);
    setPage(0);
  };

  const handleYearChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value;
    setSelectedYear(val === 'ALL' ? 'ALL' : Number(val));
    setPage(0);
  };

  const handleResetFilters = () => {
    setSelectedTab('ALL');
    setSelectedYear('ALL');
    setSearchQuery('');
    setPage(0);
  };

  const formatDateTime = (isoStr?: string): string => {
    if (!isoStr) return '—';
    const d = new Date(isoStr);
    if (isNaN(d.getTime())) return isoStr;
    return d.toLocaleString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

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

  if (loading) {
    return <LoadingSpinner message="Loading your leave requests..." minHeight="55vh" />;
  }

  const hasActiveFilters = selectedTab !== 'ALL' || selectedYear !== 'ALL' || searchQuery.trim() !== '';

  return (
    <Box sx={{ pb: 4 }}>
      <PageHeader
        category="EMPLOYEE SELF-SERVICE"
        title="My Leave Requests"
        subtitle="Filter, inspect approval progress, search records, and cancel pending leave applications."
        action={
          <Stack direction="row" spacing={1.5} alignItems="center">
            <Tooltip title="Refresh leave requests">
              <span>
                <IconButton
                  onClick={() => fetchLeaves(false)}
                  disabled={fetchingPage}
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
                border: '1px solid #111315',
                '&:hover': {
                  backgroundColor: '#23272C',
                },
              }}
            >
              New Application
            </Button>
          </Stack>
        }
      />

      {/* Error State Banner */}
      {errorMsg && (
        <Alert
          severity="error"
          icon={<ErrorOutlineOutlinedIcon fontSize="small" />}
          action={
            <Button color="inherit" size="small" onClick={() => fetchLeaves(false)}>
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

      <Card sx={{ border: '1px solid #EAE6DF', backgroundColor: '#FFFFFF', boxShadow: '0 2px 8px rgba(17, 19, 21, 0.04)' }}>
        {/* Filter Tabs: All, Pending, Approved, Rejected, Cancelled */}
        <Box sx={{ borderBottom: '1px solid #EAE6DF', px: { xs: 1, sm: 2 } }}>
          <Tabs
            value={selectedTab}
            onChange={handleTabChange}
            variant="scrollable"
            scrollButtons="auto"
            sx={{
              '& .MuiTabs-indicator': {
                backgroundColor: '#B8976C',
                height: 2.5,
              },
              '& .MuiTab-root': {
                textTransform: 'none',
                fontWeight: 500,
                fontSize: '0.885rem',
                color: '#6B7280',
                '&.Mui-selected': {
                  color: '#16181B',
                  fontWeight: 600,
                },
              },
            }}
          >
            <Tab label="All Requests" value="ALL" />
            <Tab label="Pending" value="PENDING" />
            <Tab label="Approved" value="APPROVED" />
            <Tab label="Rejected" value="REJECTED" />
            <Tab label="Cancelled" value="CANCELLED" />
          </Tabs>
        </Box>

        {/* Search & Year Filter Toolbar */}
        <Box
          sx={{
            p: 2.5,
            backgroundColor: '#FAF9F6',
            borderBottom: '1px solid #EAE6DF',
            display: 'flex',
            flexDirection: { xs: 'column', sm: 'row' },
            gap: 2,
            alignItems: { xs: 'stretch', sm: 'center' },
            justifyContent: 'space-between',
          }}
        >
          <TextField
            size="small"
            placeholder="Search by leave type, reason, or date..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            sx={{
              flexGrow: 1,
              maxWidth: { sm: 380 },
              backgroundColor: '#FFFFFF',
            }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" sx={{ color: '#6B7280' }} />
                </InputAdornment>
              ),
              endAdornment: searchQuery ? (
                <InputAdornment position="end">
                  <IconButton size="small" onClick={() => setSearchQuery('')} aria-label="clear search">
                    <ClearIcon fontSize="small" />
                  </IconButton>
                </InputAdornment>
              ) : null,
            }}
          />

          <Stack direction="row" spacing={1.5} alignItems="center">
            <TextField
              select
              size="small"
              label="Calendar Year"
              value={selectedYear}
              onChange={handleYearChange}
              sx={{ minWidth: 140, backgroundColor: '#FFFFFF' }}
            >
              <MenuItem value="ALL">All Years</MenuItem>
              {yearOptions.map((yr) => (
                <MenuItem key={yr} value={yr}>
                  {yr}
                </MenuItem>
              ))}
            </TextField>

            {hasActiveFilters && (
              <Button
                size="small"
                onClick={handleResetFilters}
                sx={{ color: '#8C6E45', fontWeight: 600, textTransform: 'none' }}
              >
                Reset
              </Button>
            )}
          </Stack>
        </Box>

        {/* Requests Table */}
        <Box sx={{ position: 'relative' }}>
          {fetchingPage && (
            <Box
              sx={{
                position: 'absolute',
                inset: 0,
                backgroundColor: 'rgba(255, 255, 255, 0.65)',
                zIndex: 2,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <CircularProgress size={28} sx={{ color: '#B8976C' }} />
            </Box>
          )}

          <TableContainer component={Paper} elevation={0}>
            <Table sx={{ minWidth: 740 }}>
              <TableHead sx={{ backgroundColor: '#F7F5F0' }}>
                <TableRow>
                  <TableCell sx={{ fontWeight: 600, color: '#16181B', fontSize: '0.775rem', letterSpacing: '0.05em' }}>
                    LEAVE TYPE
                  </TableCell>
                  <TableCell sx={{ fontWeight: 600, color: '#16181B', fontSize: '0.775rem', letterSpacing: '0.05em' }}>
                    DURATION
                  </TableCell>
                  <TableCell sx={{ fontWeight: 600, color: '#16181B', fontSize: '0.775rem', letterSpacing: '0.05em' }}>
                    DAYS
                  </TableCell>
                  <TableCell sx={{ fontWeight: 600, color: '#16181B', fontSize: '0.775rem', letterSpacing: '0.05em' }}>
                    REASON
                  </TableCell>
                  <TableCell sx={{ fontWeight: 600, color: '#16181B', fontSize: '0.775rem', letterSpacing: '0.05em' }}>
                    STATUS
                  </TableCell>
                  <TableCell align="right" sx={{ fontWeight: 600, color: '#16181B', fontSize: '0.775rem', letterSpacing: '0.05em' }}>
                    ACTIONS
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {visibleRequests.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6} align="center" sx={{ py: 7, color: '#6B7280' }}>
                      <EventAvailableOutlinedIcon
                        sx={{ fontSize: 40, color: '#B8976C', mb: 1.5, display: 'block', mx: 'auto' }}
                      />
                      <Typography variant="subtitle1" sx={{ fontWeight: 600, color: '#16181B', mb: 0.5 }}>
                        {hasActiveFilters ? 'No matching leave requests found' : 'No leave requests submitted yet'}
                      </Typography>
                      <Typography variant="body2" sx={{ color: '#6B7280', mb: 2.5 }}>
                        {hasActiveFilters
                          ? 'Try adjusting your status tab, calendar year filter, or search term.'
                          : 'When you apply for time off, your requests and approval status will appear here.'}
                      </Typography>
                      {hasActiveFilters ? (
                        <Button
                          size="small"
                          variant="outlined"
                          onClick={handleResetFilters}
                          sx={{ borderColor: '#D4CEBE', color: '#16181B' }}
                        >
                          Clear Filters
                        </Button>
                      ) : (
                        <Button
                          size="small"
                          variant="contained"
                          startIcon={<AddIcon />}
                          onClick={() => navigate('/apply-leave')}
                          sx={{ backgroundColor: '#111315', color: '#FAF9F6' }}
                        >
                          Apply for Leave
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ) : (
                  visibleRequests.map((req) => {
                    const days = req.requestedDays ?? req.days ?? 1;
                    return (
                      <TableRow
                        key={req.id}
                        hover
                        onClick={() => handleOpenDetails(req)}
                        sx={{
                          cursor: 'pointer',
                          '&:last-child td, &:last-child th': { border: 0 },
                        }}
                      >
                        <TableCell sx={{ fontWeight: 600, color: '#16181B' }}>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <span>{req.leaveType?.name || 'Leave'}</span>
                            {req.requiresDualApproval && (
                              <Chip
                                label="Dual"
                                size="small"
                                sx={{
                                  height: 18,
                                  fontSize: '0.65rem',
                                  backgroundColor: 'rgba(184, 151, 108, 0.14)',
                                  color: '#8C6E45',
                                  fontWeight: 600,
                                }}
                              />
                            )}
                          </Box>
                        </TableCell>
                        <TableCell sx={{ color: '#4B5563', fontSize: '0.875rem' }}>
                          {req.startDate} to {req.endDate}
                        </TableCell>
                        <TableCell sx={{ fontWeight: 600 }}>
                          {days} {days === 1 ? 'day' : 'days'}
                        </TableCell>
                        <TableCell
                          sx={{
                            color: '#6B7280',
                            maxWidth: 240,
                            whiteSpace: 'nowrap',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                          }}
                        >
                          {req.reason || '—'}
                        </TableCell>
                        <TableCell>{getStatusChip(req.status)}</TableCell>
                        <TableCell align="right" onClick={(e) => e.stopPropagation()}>
                          <Stack direction="row" spacing={1} justifyContent="flex-end">
                            <Tooltip title="View request details">
                              <IconButton
                                size="small"
                                onClick={() => handleOpenDetails(req)}
                                sx={{
                                  color: '#4B5563',
                                  border: '1px solid #EAE6DF',
                                  '&:hover': { borderColor: '#B8976C', color: '#16181B' },
                                }}
                              >
                                <VisibilityOutlinedIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                            {req.status === 'PENDING' && (
                              <Button
                                size="small"
                                color="error"
                                variant="outlined"
                                startIcon={<CancelOutlinedIcon sx={{ fontSize: 15 }} />}
                                onClick={() => setCancelTarget(req)}
                                sx={{
                                  fontSize: '0.75rem',
                                  py: 0.25,
                                  px: 1.25,
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
                            )}
                          </Stack>
                        </TableCell>
                      </TableRow>
                    );
                  })
                )}
              </TableBody>
            </Table>
          </TableContainer>

          {/* Server-Side Pagination */}
          <TablePagination
            component="div"
            count={totalElements}
            page={page}
            onPageChange={(_, newPage) => setPage(newPage)}
            rowsPerPage={rowsPerPage}
            onRowsPerPageChange={(e) => {
              setRowsPerPage(parseInt(e.target.value, 10));
              setPage(0);
            }}
            rowsPerPageOptions={[5, 10, 25]}
            sx={{
              borderTop: '1px solid #EAE6DF',
              backgroundColor: '#FAF9F6',
            }}
          />
        </Box>
      </Card>

      {/* Leave Request Details Modal */}
      <Dialog
        open={Boolean(detailRequest)}
        onClose={() => setDetailRequest(null)}
        maxWidth="sm"
        fullWidth
        PaperProps={{
          sx: {
            borderRadius: 2,
            border: '1px solid #EAE6DF',
          },
        }}
      >
        <DialogTitle
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: '1px solid #EAE6DF',
            pb: 2,
          }}
        >
          <Box>
            <Typography
              variant="caption"
              sx={{ color: '#B8976C', fontWeight: 700, letterSpacing: '0.12em', display: 'block' }}
            >
              LEAVE REQUEST #{detailRequest?.id}
            </Typography>
            <Typography
              variant="h6"
              sx={{ fontFamily: '"Playfair Display", Georgia, serif', fontWeight: 600, color: '#16181B' }}
            >
              {detailRequest?.leaveType?.name || 'Leave Details'}
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            {detailRequest && getStatusChip(detailRequest.status)}
            <IconButton size="small" onClick={() => setDetailRequest(null)} aria-label="close details">
              <CloseIcon fontSize="small" />
            </IconButton>
          </Box>
        </DialogTitle>

        <DialogContent sx={{ pt: 3 }}>
          {loadingDetail && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2, color: '#6B7280' }}>
              <CircularProgress size={16} sx={{ color: '#B8976C' }} />
              <Typography variant="caption">Refreshing latest approval status...</Typography>
            </Box>
          )}

          {detailRequest && (
            <Stack spacing={2.5}>
              {/* Schedule Summary */}
              <Box
                sx={{
                  p: 2,
                  backgroundColor: '#FAF9F6',
                  border: '1px solid #EAE6DF',
                  borderRadius: 1.5,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  flexWrap: 'wrap',
                  gap: 1.5,
                }}
              >
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                  <DateRangeOutlinedIcon sx={{ color: '#B8976C' }} />
                  <Box>
                    <Typography variant="caption" sx={{ color: '#6B7280', display: 'block' }}>
                      SCHEDULED DURATION
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 600, color: '#16181B' }}>
                      {detailRequest.startDate} to {detailRequest.endDate}
                    </Typography>
                  </Box>
                </Box>
                <Chip
                  label={`${detailRequest.requestedDays ?? detailRequest.days ?? 1} Working Day(s)`}
                  size="small"
                  sx={{ backgroundColor: '#111315', color: '#FAF9F6', fontWeight: 600 }}
                />
              </Box>

              {/* Reason */}
              <Box>
                <Typography variant="caption" sx={{ color: '#6B7280', fontWeight: 600, display: 'block', mb: 0.5 }}>
                  BUSINESS REASON & CONTEXT
                </Typography>
                <Typography
                  variant="body2"
                  sx={{
                    color: '#16181B',
                    p: 1.5,
                    backgroundColor: '#FFFFFF',
                    border: '1px solid #EAE6DF',
                    borderRadius: 1,
                    whiteSpace: 'pre-wrap',
                  }}
                >
                  {detailRequest.reason || 'No reason provided.'}
                </Typography>
              </Box>

              {/* Rejection Reason if rejected */}
              {detailRequest.status === 'REJECTED' && detailRequest.rejectionReason && (
                <Alert
                  severity="error"
                  sx={{
                    backgroundColor: '#FAF0F0',
                    color: '#8C2D2D',
                    border: '1px solid #ECCACA',
                  }}
                >
                  <strong>Rejection Reason:</strong> {detailRequest.rejectionReason}
                </Alert>
              )}

              {/* Approval Trail */}
              <Box>
                <Typography variant="caption" sx={{ color: '#6B7280', fontWeight: 600, display: 'block', mb: 1 }}>
                  APPROVAL WORKFLOW & TIMESTAMPS
                </Typography>
                <Grid container spacing={1.5}>
                  <Grid item xs={12} sm={6}>
                    <Box sx={{ p: 1.5, border: '1px solid #EAE6DF', borderRadius: 1 }}>
                      <Typography variant="caption" sx={{ color: '#6B7280', display: 'block' }}>
                        Submitted On
                      </Typography>
                      <Typography variant="body2" sx={{ fontWeight: 500, color: '#16181B' }}>
                        {formatDateTime(detailRequest.appliedAt || detailRequest.createdAt)}
                      </Typography>
                    </Box>
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <Box sx={{ p: 1.5, border: '1px solid #EAE6DF', borderRadius: 1 }}>
                      <Typography variant="caption" sx={{ color: '#6B7280', display: 'block' }}>
                        Manager Approval
                      </Typography>
                      <Typography variant="body2" sx={{ fontWeight: 500, color: '#16181B' }}>
                        {detailRequest.managerApprovalStatus || detailRequest.status}
                        {detailRequest.managerApprovedByEmail ? ` (${detailRequest.managerApprovedByEmail})` : ''}
                      </Typography>
                    </Box>
                  </Grid>
                  {detailRequest.requiresDualApproval && (
                    <Grid item xs={12}>
                      <Box sx={{ p: 1.5, border: '1px solid #EAE6DF', borderRadius: 1 }}>
                        <Typography variant="caption" sx={{ color: '#6B7280', display: 'block' }}>
                          HR Dual Approval
                        </Typography>
                        <Typography variant="body2" sx={{ fontWeight: 500, color: '#16181B' }}>
                          {detailRequest.hrApprovalStatus || 'PENDING'}
                          {detailRequest.hrApprovedByEmail ? ` (${detailRequest.hrApprovedByEmail})` : ''}
                        </Typography>
                      </Box>
                    </Grid>
                  )}
                </Grid>
              </Box>
            </Stack>
          )}
        </DialogContent>

        <Divider sx={{ borderColor: '#EAE6DF' }} />
        <DialogActions sx={{ px: 3, py: 2, justifyContent: 'space-between' }}>
          <Box>
            {detailRequest?.status === 'PENDING' && (
              <Button
                color="error"
                variant="outlined"
                startIcon={<CancelOutlinedIcon />}
                onClick={() => setCancelTarget(detailRequest)}
                sx={{ borderColor: '#ECCACA', color: '#8C2D2D' }}
              >
                Cancel Request
              </Button>
            )}
          </Box>
          <Button onClick={() => setDetailRequest(null)} sx={{ color: '#16181B', fontWeight: 600 }}>
            Close
          </Button>
        </DialogActions>
      </Dialog>

      {/* Confirmation Dialog for Cancellation */}
      <Dialog
        open={Boolean(cancelTarget)}
        onClose={() => !cancelling && setCancelTarget(null)}
        PaperProps={{
          sx: {
            borderRadius: 2,
            p: 1,
            border: '1px solid #EAE6DF',
          },
        }}
      >
        <DialogTitle
          sx={{
            fontFamily: '"Playfair Display", Georgia, serif',
            fontWeight: 600,
            color: '#16181B',
          }}
        >
          Cancel Leave Request
        </DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ color: '#6B7280', fontSize: '0.9rem' }}>
            Are you sure you want to cancel your{' '}
            <strong>{cancelTarget?.leaveType?.name || 'leave'}</strong> request from{' '}
            <strong>{cancelTarget?.startDate}</strong> to <strong>{cancelTarget?.endDate}</strong>? This action will
            immediately restore your allocated quota balance.
          </DialogContentText>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setCancelTarget(null)} disabled={cancelling} color="inherit">
            Keep Request
          </Button>
          <Button
            onClick={handleConfirmCancel}
            variant="contained"
            color="error"
            disabled={cancelling}
            sx={{
              backgroundColor: '#8C2D2D',
              '&:hover': {
                backgroundColor: '#702323',
              },
            }}
          >
            {cancelling ? 'Cancelling...' : 'Confirm Cancellation'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default MyLeaves;
