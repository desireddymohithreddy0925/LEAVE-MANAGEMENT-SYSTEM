import React, { useEffect, useState } from 'react';
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
} from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import DateRangeOutlinedIcon from '@mui/icons-material/DateRangeOutlined';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import { useNavigate } from 'react-router-dom';
import apiClient from '../api/client';
import { LeaveType, OverlapCheckResponse } from '../types';
import { useNotification } from '../context/NotificationContext';
import PageHeader from '../components/common/PageHeader';

export const ApplyLeave: React.FC = () => {
  const navigate = useNavigate();
  const { showSuccess, showError } = useNotification();

  const [leaveTypes, setLeaveTypes] = useState<LeaveType[]>([]);
  const [selectedTypeId, setSelectedTypeId] = useState<number | ''>('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [reason, setReason] = useState('');
  const [loadingTypes, setLoadingTypes] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [overlapWarning, setOverlapWarning] = useState<string | null>(null);

  useEffect(() => {
    apiClient
      .get<LeaveType[]>('/api/leave-types?active=true')
      .then((res) => {
        setLeaveTypes(res.data || []);
        if (res.data && res.data.length > 0) {
          setSelectedTypeId(res.data[0].id);
        }
      })
      .catch(() => {
        setLeaveTypes([
          { id: 1, name: 'Annual Leave', defaultDays: 20, active: true },
          { id: 2, name: 'Sick Leave', defaultDays: 10, active: true },
        ]);
        setSelectedTypeId(1);
      })
      .finally(() => setLoadingTypes(false));
  }, []);

  // Compute calculated days (excluding weekends)
  const calculateWorkingDays = (start: string, end: string): number => {
    if (!start || !end) return 0;
    const d1 = new Date(start);
    const d2 = new Date(end);
    if (d1 > d2) return 0;

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

  const calculatedDays = calculateWorkingDays(startDate, endDate);

  // Check overlap dynamically when both dates are entered
  useEffect(() => {
    if (startDate && endDate && startDate <= endDate) {
      apiClient
        .get<OverlapCheckResponse>('/api/leave-requests/check-overlap', {
          params: { startDate, endDate },
        })
        .then((res) => {
          if (res.data?.overlapping) {
            setOverlapWarning(res.data.message || 'You have an existing leave request overlapping these dates.');
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

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!selectedTypeId) {
      showError('Please select a leave category.');
      return;
    }

    if (!startDate || !endDate) {
      showError('Please select both start and end dates.');
      return;
    }

    if (startDate > endDate) {
      showError('Start date cannot be after end date.');
      return;
    }

    if (calculatedDays === 0) {
      showError('Selected date range contains no business working days.');
      return;
    }

    if (!reason.trim()) {
      showError('Please provide a business reason for your leave request.');
      return;
    }

    setSubmitting(true);
    try {
      await apiClient.post('/api/leave-requests', {
        leaveTypeId: Number(selectedTypeId),
        startDate,
        endDate,
        reason,
      });

      showSuccess('Leave application submitted successfully for review.');
      navigate('/my-leaves');
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      showError(axiosErr.response?.data?.message || 'Failed to submit leave application.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Box>
      <PageHeader
        category="LEAVE APPLICATION"
        title="Apply for Leave"
        subtitle="Submit a formal leave request to your reporting manager with automated quota and overlap verification."
      />

      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <Card
            sx={{
              borderLeft: '4px solid #B8976C', // Warm Champagne Bronze accent bar (Image 3 Style)
              backgroundColor: '#FFFFFF',
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

              {overlapWarning && (
                <Alert
                  severity="warning"
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

              <Box component="form" onSubmit={handleSubmit} noValidate>
                {/* Leave Type Dropdown */}
                <TextField
                  select
                  fullWidth
                  label="Leave Type Category"
                  value={selectedTypeId}
                  onChange={(e) => setSelectedTypeId(Number(e.target.value))}
                  disabled={loadingTypes}
                  helperText="Select the relevant leave policy entitlement"
                  size="small"
                  sx={{ mb: 3 }}
                >
                  {leaveTypes.map((type) => (
                    <MenuItem key={type.id} value={type.id}>
                      {type.name} {type.defaultDays ? `(${type.defaultDays} days default)` : ''}
                    </MenuItem>
                  ))}
                </TextField>

                {/* Date Selection Grid */}
                <Grid container spacing={2.5} sx={{ mb: 3 }}>
                  <Grid item xs={12} sm={6}>
                    <TextField
                      fullWidth
                      label="Start Date"
                      type="date"
                      value={startDate}
                      onChange={(e) => setStartDate(e.target.value)}
                      InputLabelProps={{ shrink: true }}
                      size="small"
                    />
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField
                      fullWidth
                      label="End Date"
                      type="date"
                      value={endDate}
                      onChange={(e) => setEndDate(e.target.value)}
                      InputLabelProps={{ shrink: true }}
                      size="small"
                    />
                  </Grid>
                </Grid>

                {/* Working Days Calculated Badge */}
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
                    }}
                  >
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <DateRangeOutlinedIcon sx={{ color: '#B8976C' }} />
                      <Typography variant="body2" sx={{ color: '#4B5563' }}>
                        Calculated Business Working Days (excluding weekends):
                      </Typography>
                    </Box>
                    <Typography
                      variant="h6"
                      sx={{
                        fontFamily: '"Playfair Display", Georgia, serif',
                        fontWeight: 700,
                        color: '#16181B',
                      }}
                    >
                      {calculatedDays} {calculatedDays === 1 ? 'Day' : 'Days'}
                    </Typography>
                  </Box>
                )}

                {/* Reason Field */}
                <TextField
                  fullWidth
                  multiline
                  rows={4}
                  label="Business Reason & Context"
                  placeholder="Please state the purpose of your leave and handover preparations..."
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  sx={{ mb: 3.5 }}
                />

                {/* Action Buttons */}
                <Box sx={{ display: 'flex', gap: 2 }}>
                  <Button
                    type="submit"
                    variant="contained"
                    disabled={submitting}
                    startIcon={submitting ? <CircularProgress size={18} color="inherit" /> : <SendIcon sx={{ fontSize: 18 }} />}
                    sx={{
                      px: 3,
                      py: 1,
                      backgroundColor: '#111315',
                      border: '1px solid #111315',
                      '&:hover': {
                        backgroundColor: '#23272C',
                      },
                    }}
                  >
                    {submitting ? 'Submitting...' : 'Submit Application'}
                  </Button>
                  <Button
                    variant="outlined"
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
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* Guidance Side Panel */}
        <Grid item xs={12} md={4}>
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
                  • <strong>Notice Period</strong>: Submit leave requests at least 3 business days in advance when possible.
                </Typography>
                <Typography variant="body2">
                  • <strong>Approval Chain</strong>: Your application is automatically routed to your assigned department manager.
                </Typography>
                <Typography variant="body2">
                  • <strong>Weekend Exclusion</strong>: Saturdays and Sundays are excluded from your annual quota deduction.
                </Typography>
                <Typography variant="body2">
                  • <strong>Cancellation</strong>: Applications in pending status can be freely cancelled from your history.
                </Typography>
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};

export default ApplyLeave;
