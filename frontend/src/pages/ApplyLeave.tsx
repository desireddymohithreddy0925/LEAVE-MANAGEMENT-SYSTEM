import React, { useEffect, useState, useMemo, useRef } from 'react';
import {
  Card,
  CardContent,
  Typography,
  Box,
  TextField,
  Button,
  MenuItem,
  Alert,
  CircularProgress,
  Grid,
  Chip,
  LinearProgress,
  Stack,
  Divider,
} from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import DateRangeOutlinedIcon from '@mui/icons-material/DateRangeOutlined';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';
import WarningAmberOutlinedIcon from '@mui/icons-material/WarningAmberOutlined';
import ErrorOutlineOutlinedIcon from '@mui/icons-material/ErrorOutlineOutlined';
import { useNavigate } from 'react-router-dom';
import apiClient from '../api/client';
import { LeaveType, LeaveBalance, OverlapCheckResponse } from '../types';
import { useNotification } from '../context/NotificationContext';
import PageHeader from '../components/common/PageHeader';

interface FieldErrors {
  leaveTypeId?: string;
  startDate?: string;
  endDate?: string;
  reason?: string;
}

const generateIdempotencyKey = (): string => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `leave-${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;
};

export const ApplyLeave: React.FC = () => {
  const navigate = useNavigate();
  const { showSuccess, showError } = useNotification();

  const [leaveTypes, setLeaveTypes] = useState<LeaveType[]>([]);
  const [balances, setBalances] = useState<LeaveBalance[]>([]);
  const [selectedTypeId, setSelectedTypeId] = useState<number | ''>('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [reason, setReason] = useState('');

  const [loadingData, setLoadingData] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [overlapWarning, setOverlapWarning] = useState<string | null>(null);

  // Validation & backend error states
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [backendError, setBackendError] = useState<string | null>(null);

  // Synchronous ref lock + persistent Idempotency-Key per logical submission to prevent duplicate submissions
  const isSubmittingRef = useRef<boolean>(false);
  const idempotencyKeyRef = useRef<string>(generateIdempotencyKey());

  const todayStr = useMemo(() => new Date().toISOString().split('T')[0], []);

  // Load active leave types and employee's real leave balances
  useEffect(() => {
    const loadFormDependencies = async () => {
      try {
        const currentYear = new Date().getFullYear();
        const [typesRes, balancesRes] = await Promise.all([
          apiClient.get<LeaveType[]>('/api/leave-types?active=true'),
          apiClient
            .get<LeaveBalance[]>('/api/leave-balances/me', { params: { year: currentYear } })
            .catch(() => apiClient.get<LeaveBalance[]>('/api/leave-balances/me').catch(() => ({ data: [] }))),
        ]);

        const activeTypes = typesRes.data || [];
        setLeaveTypes(activeTypes);
        setBalances(balancesRes.data || []);

        if (activeTypes.length > 0) {
          setSelectedTypeId(activeTypes[0].id);
        }
      } catch {
        showError('Failed to load leave categories and balance information.');
      } finally {
        setLoadingData(false);
      }
    };

    loadFormDependencies();
  }, [showError]);

  // Reset Idempotency-Key whenever form inputs change so a modified payload gets a fresh key
  useEffect(() => {
    idempotencyKeyRef.current = generateIdempotencyKey();
  }, [selectedTypeId, startDate, endDate, reason]);

  // Find balance matching selected leave type and target year (falls back to any matching leave type)
  const selectedBalance = useMemo<LeaveBalance | undefined>(() => {
    if (!selectedTypeId) return undefined;
    const targetYear = startDate ? Number(startDate.slice(0, 4)) : new Date().getFullYear();
    return (
      balances.find((b) => b.leaveType?.id === Number(selectedTypeId) && b.year === targetYear) ||
      balances.find((b) => b.leaveType?.id === Number(selectedTypeId))
    );
  }, [balances, selectedTypeId, startDate]);

  const availableQuota = selectedBalance
    ? selectedBalance.available ?? selectedBalance.availableDays ?? 0
    : 0;
  const usedQuota = selectedBalance ? selectedBalance.used ?? selectedBalance.usedDays ?? 0 : 0;
  const totalQuota = selectedBalance
    ? selectedBalance.total ?? selectedBalance.totalDays ?? availableQuota + usedQuota
    : 0;

  // Compute business working days (excluding weekends)
  const calculateWorkingDays = (start: string, end: string): number => {
    if (!start || !end) return 0;
    const d1 = new Date(`${start}T00:00:00`);
    const d2 = new Date(`${end}T00:00:00`);
    if (isNaN(d1.getTime()) || isNaN(d2.getTime()) || d1 > d2) return 0;

    let count = 0;
    const cur = new Date(d1);
    while (cur <= d2) {
      const dayOfWeek = cur.getDay();
      if (dayOfWeek !== 0 && dayOfWeek !== 6) {
        count++;
      }
      cur.setDate(cur.getDate() + 1);
    }
    return count;
  };

  const calculatedDays = useMemo(() => calculateWorkingDays(startDate, endDate), [startDate, endDate]);
  const exceedsBalance = Boolean(selectedBalance && calculatedDays > availableQuota);

  // Check overlap dynamically when both dates are valid and in the same year
  useEffect(() => {
    if (startDate && endDate && startDate <= endDate && startDate.slice(0, 4) === endDate.slice(0, 4)) {
      apiClient
        .get<OverlapCheckResponse>('/api/leave-requests/check-overlap', {
          params: { startDate, endDate },
        })
        .then((res) => {
          if (res.data?.overlapping) {
            setOverlapWarning(
              res.data.message || 'You already have a pending or approved leave overlapping these dates.'
            );
          } else {
            setOverlapWarning(null);
          }
        })
        .catch(() => {
          setOverlapWarning(null);
        });
    } else {
      setOverlapWarning(null);
    }
  }, [startDate, endDate]);

  // Client-side validation mirroring backend constraints
  const validateForm = (): boolean => {
    const errors: FieldErrors = {};

    if (!selectedTypeId) {
      errors.leaveTypeId = 'Please select a leave type category.';
    }

    if (!startDate) {
      errors.startDate = 'Start date is required.';
    } else if (startDate < todayStr) {
      errors.startDate = 'Start date cannot be in the past.';
    }

    if (!endDate) {
      errors.endDate = 'End date is required.';
    } else if (startDate && endDate < startDate) {
      errors.endDate = 'End date must be on or after the start date.';
    } else if (startDate && startDate.slice(0, 4) !== endDate.slice(0, 4)) {
      errors.endDate = 'Leave request cannot span multiple calendar years. Submit separate requests per year.';
    } else if (startDate && endDate && calculatedDays === 0) {
      errors.endDate = 'Selected date range contains no business working days (weekends only).';
    }

    if (!reason.trim()) {
      errors.reason = 'Please provide a reason or handover context for your leave request.';
    } else if (reason.trim().length > 500) {
      errors.reason = 'Reason must not exceed 500 characters.';
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const clearFieldError = (field: keyof FieldErrors) => {
    if (fieldErrors[field]) {
      setFieldErrors((prev) => ({ ...prev, [field]: undefined }));
    }
    if (backendError) {
      setBackendError(null);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    // Prevent duplicate submissions synchronously
    if (isSubmittingRef.current || submitting) {
      return;
    }

    setBackendError(null);

    if (!validateForm()) {
      showError('Please resolve the highlighted validation errors before submitting.');
      return;
    }

    isSubmittingRef.current = true;
    setSubmitting(true);

    try {
      await apiClient.post(
        '/api/leave-requests',
        {
          leaveTypeId: Number(selectedTypeId),
          startDate,
          endDate,
          reason: reason.trim(),
        },
        {
          headers: {
            'Idempotency-Key': idempotencyKeyRef.current,
          },
        }
      );

      showSuccess('Leave application submitted successfully for manager review.');
      navigate('/my-leaves');
    } catch (err: unknown) {
      const axiosErr = err as {
        response?: {
          status?: number;
          data?: {
            message?: string;
            error?: string;
            errors?: Record<string, string>;
          };
        };
      };

      const data = axiosErr.response?.data;
      const serverMessage = data?.message || data?.error || 'Failed to submit leave application.';
      setBackendError(serverMessage);

      // Map backend field-level validation errors onto form inputs
      if (data?.errors && typeof data.errors === 'object') {
        const mapped: FieldErrors = {};
        if (data.errors.leaveTypeId) mapped.leaveTypeId = data.errors.leaveTypeId;
        if (data.errors.startDate) mapped.startDate = data.errors.startDate;
        if (data.errors.endDate) mapped.endDate = data.errors.endDate;
        if (data.errors.reason) mapped.reason = data.errors.reason;
        // Global @ValidDateRange error maps under DTO name
        if (data.errors.leaveRequestDTO && !mapped.endDate) {
          mapped.endDate = data.errors.leaveRequestDTO;
        }
        setFieldErrors((prev) => ({ ...prev, ...mapped }));
      }

      showError(serverMessage);
    } finally {
      isSubmittingRef.current = false;
      setSubmitting(false);
    }
  };

  return (
    <Box sx={{ pb: 4 }}>
      <PageHeader
        category="LEAVE APPLICATION"
        title="Apply for Leave"
        subtitle="Submit a formal leave request with live quota validation, overlap detection, and idempotent duplicate protection."
      />

      <Grid container spacing={3}>
        {/* Main Application Form */}
        <Grid item xs={12} md={8}>
          <Card
            sx={{
              borderLeft: '4px solid #B8976C',
              backgroundColor: '#FFFFFF',
              borderTop: '1px solid #EAE6DF',
              borderRight: '1px solid #EAE6DF',
              borderBottom: '1px solid #EAE6DF',
              boxShadow: '0 2px 8px rgba(17, 19, 21, 0.04)',
            }}
          >
            <CardContent sx={{ p: { xs: 3, sm: 4 } }}>
              <Box sx={{ mb: 3 }}>
                <Typography
                  variant="subtitle2"
                  sx={{ color: '#B8976C', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
                >
                  APPLICATION FORM
                </Typography>
                <Typography
                  variant="h6"
                  sx={{
                    fontFamily: '"Playfair Display", Georgia, serif',
                    fontWeight: 600,
                    color: '#16181B',
                  }}
                >
                  Leave Details & Schedule
                </Typography>
              </Box>

              {/* Backend Validation / Business Error Banner */}
              {backendError && (
                <Alert
                  severity="error"
                  icon={<ErrorOutlineOutlinedIcon fontSize="small" />}
                  sx={{
                    mb: 3,
                    backgroundColor: '#FAF0F0',
                    color: '#8C2D2D',
                    border: '1px solid #ECCACA',
                    fontSize: '0.85rem',
                    lineHeight: 1.5,
                  }}
                >
                  <strong>Submission Rejected:</strong> {backendError}
                </Alert>
              )}

              {/* Overlap Warning Banner */}
              {overlapWarning && (
                <Alert
                  severity="warning"
                  icon={<WarningAmberOutlinedIcon fontSize="small" />}
                  sx={{
                    mb: 3,
                    backgroundColor: '#FDF8EB',
                    color: '#8C6B1C',
                    border: '1px solid #F4E3B8',
                    fontSize: '0.85rem',
                  }}
                >
                  {overlapWarning}
                </Alert>
              )}

              {/* Insufficient Balance Warning Banner */}
              {exceedsBalance && (
                <Alert
                  severity="warning"
                  icon={<WarningAmberOutlinedIcon fontSize="small" />}
                  sx={{
                    mb: 3,
                    backgroundColor: '#FAF0F0',
                    color: '#8C2D2D',
                    border: '1px solid #ECCACA',
                    fontSize: '0.85rem',
                  }}
                >
                  Requested duration (<strong>{calculatedDays} days</strong>) exceeds your available{' '}
                  {selectedBalance?.leaveType?.name || 'leave'} balance (<strong>{availableQuota} days</strong>).
                </Alert>
              )}

              <Box component="form" onSubmit={handleSubmit} noValidate>
                {/* Leave Type Dropdown with Balance Preview */}
                <TextField
                  select
                  required
                  fullWidth
                  label="Leave Type Category"
                  value={selectedTypeId}
                  onChange={(e) => {
                    setSelectedTypeId(Number(e.target.value));
                    clearFieldError('leaveTypeId');
                  }}
                  disabled={loadingData || submitting}
                  error={Boolean(fieldErrors.leaveTypeId)}
                  helperText={fieldErrors.leaveTypeId || 'Select the applicable leave policy category'}
                  size="small"
                  sx={{ mb: 3 }}
                >
                  {leaveTypes.map((type) => {
                    const bal = balances.find((b) => b.leaveType?.id === type.id);
                    const avail = bal ? bal.available ?? bal.availableDays ?? 0 : null;
                    return (
                      <MenuItem key={type.id} value={type.id}>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', width: '100%', alignItems: 'center' }}>
                          <span>{type.name}</span>
                          {avail !== null && (
                            <Chip
                              label={`${avail} d available`}
                              size="small"
                              sx={{
                                ml: 2,
                                height: 20,
                                fontSize: '0.7rem',
                                backgroundColor: avail > 0 ? '#EBF5EE' : '#FAF0F0',
                                color: avail > 0 ? '#235A36' : '#8C2D2D',
                                fontWeight: 600,
                              }}
                            />
                          )}
                        </Box>
                      </MenuItem>
                    );
                  })}
                </TextField>

                {/* Date Selection Grid */}
                <Grid container spacing={2.5} sx={{ mb: 3 }}>
                  <Grid item xs={12} sm={6}>
                    <TextField
                      required
                      fullWidth
                      label="Start Date"
                      type="date"
                      value={startDate}
                      onChange={(e) => {
                        const nextStart = e.target.value;
                        setStartDate(nextStart);
                        if (endDate && nextStart > endDate) {
                          setEndDate(nextStart);
                        }
                        clearFieldError('startDate');
                        clearFieldError('endDate');
                      }}
                      disabled={submitting}
                      error={Boolean(fieldErrors.startDate)}
                      helperText={fieldErrors.startDate}
                      inputProps={{ min: todayStr }}
                      InputLabelProps={{ shrink: true }}
                      size="small"
                    />
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField
                      required
                      fullWidth
                      label="End Date"
                      type="date"
                      value={endDate}
                      onChange={(e) => {
                        setEndDate(e.target.value);
                        clearFieldError('endDate');
                      }}
                      disabled={submitting}
                      error={Boolean(fieldErrors.endDate)}
                      helperText={fieldErrors.endDate}
                      inputProps={{ min: startDate || todayStr }}
                      InputLabelProps={{ shrink: true }}
                      size="small"
                    />
                  </Grid>
                </Grid>

                {/* Working Days Summary Badge */}
                {startDate && endDate && (
                  <Box
                    sx={{
                      p: 2,
                      mb: 3,
                      backgroundColor: '#FAF9F6',
                      borderRadius: 1.5,
                      border: '1px solid #EAE6DF',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      flexWrap: 'wrap',
                      gap: 1,
                    }}
                  >
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <DateRangeOutlinedIcon sx={{ color: '#B8976C' }} />
                      <Box>
                        <Typography variant="body2" sx={{ color: '#16181B', fontWeight: 500 }}>
                          Business Working Days
                        </Typography>
                        <Typography variant="caption" sx={{ color: '#6B7280' }}>
                          Excludes Saturdays & Sundays
                        </Typography>
                      </Box>
                    </Box>
                    <Typography
                      variant="h6"
                      sx={{
                        fontFamily: '"Playfair Display", Georgia, serif',
                        fontWeight: 700,
                        color: calculatedDays === 0 || exceedsBalance ? '#8C2D2D' : '#16181B',
                      }}
                    >
                      {calculatedDays} {calculatedDays === 1 ? 'Day' : 'Days'}
                    </Typography>
                  </Box>
                )}

                {/* Reason Field with Character Counter */}
                <TextField
                  required
                  fullWidth
                  multiline
                  rows={4}
                  label="Business Reason & Context"
                  placeholder="State the purpose of your leave and handover arrangements..."
                  value={reason}
                  onChange={(e) => {
                    setReason(e.target.value);
                    clearFieldError('reason');
                  }}
                  disabled={submitting}
                  error={Boolean(fieldErrors.reason)}
                  helperText={
                    fieldErrors.reason || `${reason.length}/500 characters`
                  }
                  inputProps={{ maxLength: 500 }}
                  sx={{ mb: 3.5 }}
                />

                {/* Action Buttons */}
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                  <Button
                    type="submit"
                    variant="contained"
                    disabled={submitting || loadingData}
                    startIcon={
                      submitting ? <CircularProgress size={18} color="inherit" /> : <SendIcon sx={{ fontSize: 18 }} />
                    }
                    sx={{
                      px: 3,
                      py: 1.1,
                      backgroundColor: '#111315',
                      color: '#FAF9F6',
                      fontWeight: 600,
                      border: '1px solid #111315',
                      '&:hover': {
                        backgroundColor: '#23272C',
                      },
                    }}
                  >
                    {submitting ? 'Submitting Application...' : 'Submit Application'}
                  </Button>
                  <Button
                    variant="outlined"
                    disabled={submitting}
                    onClick={() => navigate('/dashboard')}
                    sx={{
                      borderColor: '#D4CEBE',
                      color: '#4B5563',
                      '&:hover': {
                        borderColor: '#B8976C',
                        backgroundColor: 'rgba(184, 151, 108, 0.08)',
                      },
                    }}
                  >
                    Cancel
                  </Button>
                </Stack>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* Right Column: Live Quota Balance & Policy Guidelines */}
        <Grid item xs={12} md={4}>
          <Stack spacing={3}>
            {/* Selected Category Balance Card */}
            <Card sx={{ border: '1px solid #EAE6DF', backgroundColor: '#FFFFFF', boxShadow: '0 2px 8px rgba(17, 19, 21, 0.04)' }}>
              <CardContent sx={{ p: 3 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
                  <AccountBalanceWalletOutlinedIcon sx={{ color: '#B8976C', fontSize: 20 }} />
                  <Typography
                    variant="subtitle2"
                    sx={{ color: '#B8976C', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
                  >
                    AVAILABLE BALANCE
                  </Typography>
                </Box>

                {loadingData ? (
                  <Box sx={{ py: 2, textAlign: 'center' }}>
                    <CircularProgress size={22} sx={{ color: '#B8976C' }} />
                  </Box>
                ) : selectedBalance ? (
                  <>
                    <Typography
                      variant="h6"
                      sx={{
                        fontFamily: '"Playfair Display", Georgia, serif',
                        fontWeight: 600,
                        color: '#16181B',
                        mb: 0.5,
                      }}
                    >
                      {selectedBalance.leaveType?.name}
                    </Typography>
                    <Typography variant="caption" sx={{ color: '#6B7280', display: 'block', mb: 2 }}>
                      Calendar Year {selectedBalance.year}
                    </Typography>

                    <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1, mb: 1.5 }}>
                      <Typography
                        variant="h3"
                        sx={{
                          fontFamily: '"Playfair Display", Georgia, serif',
                          fontWeight: 700,
                          color: availableQuota > 0 ? '#16181B' : '#8C2D2D',
                        }}
                      >
                        {availableQuota}
                      </Typography>
                      <Typography variant="body2" sx={{ color: '#6B7280' }}>
                        days available of {totalQuota} total
                      </Typography>
                    </Box>

                    <LinearProgress
                      variant="determinate"
                      value={totalQuota > 0 ? Math.min(100, Math.round((usedQuota / totalQuota) * 100)) : 0}
                      sx={{
                        height: 6,
                        borderRadius: 3,
                        backgroundColor: '#F3EFEA',
                        mb: 1.5,
                        '& .MuiLinearProgress-bar': {
                          backgroundColor: '#B8976C',
                          borderRadius: 3,
                        },
                      }}
                    />

                    <Divider sx={{ my: 1.5, borderColor: '#EAE6DF' }} />

                    <Box sx={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', color: '#4B5563' }}>
                      <span>Used to date:</span>
                      <strong>{usedQuota} days</strong>
                    </Box>
                    {calculatedDays > 0 && (
                      <Box
                        sx={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          fontSize: '0.8rem',
                          color: exceedsBalance ? '#8C2D2D' : '#235A36',
                          mt: 0.75,
                        }}
                      >
                        <span>Projected remaining:</span>
                        <strong>{availableQuota - calculatedDays} days</strong>
                      </Box>
                    )}
                  </>
                ) : (
                  <Typography variant="body2" sx={{ color: '#6B7280' }}>
                    No quota balance record found for this category. Contact HR if you expect an allocation.
                  </Typography>
                )}
              </CardContent>
            </Card>

            {/* Policy Guidelines Card */}
            <Card sx={{ border: '1px solid #EAE6DF', backgroundColor: '#FFFFFF' }}>
              <CardContent sx={{ p: 3 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                  <InfoOutlinedIcon sx={{ color: '#B8976C', fontSize: 20 }} />
                  <Typography
                    variant="subtitle2"
                    sx={{ color: '#B8976C', letterSpacing: '0.12em', fontWeight: 600, fontSize: '0.725rem' }}
                  >
                    POLICY GUIDELINES
                  </Typography>
                </Box>

                <Typography
                  variant="h6"
                  sx={{
                    fontFamily: '"Playfair Display", Georgia, serif',
                    fontWeight: 600,
                    color: '#16181B',
                    mb: 1.5,
                  }}
                >
                  Submission Rules
                </Typography>

                <Box sx={{ '& > p': { mb: 1.5, color: '#6B7280', fontSize: '0.85rem' } }}>
                  <Typography variant="body2">
                    • <strong>Single-Year Requests</strong>: Leave spans cannot cross December 31 into the next year.
                  </Typography>
                  <Typography variant="body2">
                    • <strong>Duplicate Protection</strong>: Every submission carries a unique idempotency key to prevent double-booking.
                  </Typography>
                  <Typography variant="body2">
                    • <strong>Weekend Exclusion</strong>: Saturdays and Sundays are excluded from your quota deduction.
                  </Typography>
                  <Typography variant="body2">
                    • <strong>Approval Chain</strong>: Requests are routed directly to your reporting manager for review.
                  </Typography>
                </Box>
              </CardContent>
            </Card>
          </Stack>
        </Grid>
      </Grid>
    </Box>
  );
};

export default ApplyLeave;
