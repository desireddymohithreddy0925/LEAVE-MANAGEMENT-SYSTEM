import React, { useEffect, useState } from 'react';
import {
  Drawer,
  Box,
  Typography,
  IconButton,
  Divider,
  Stack,
  Chip,
  LinearProgress,
  CircularProgress,
  Alert,
  Avatar,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import EmailOutlinedIcon from '@mui/icons-material/EmailOutlined';
import BadgeOutlinedIcon from '@mui/icons-material/BadgeOutlined';
import BusinessOutlinedIcon from '@mui/icons-material/BusinessOutlined';
import apiClient from '../../api/client';
import { Employee, LeaveBalance, LeaveRequest } from '../../types';
import LeaveStatusChip from '../common/LeaveStatusChip';

interface EmployeeLeaveDrawerProps {
  employee: Employee | null;
  onClose: () => void;
}

export const EmployeeLeaveDrawer: React.FC<EmployeeLeaveDrawerProps> = ({ employee, onClose }) => {
  const [balances, setBalances] = useState<LeaveBalance[]>([]);
  const [history, setHistory] = useState<LeaveRequest[]>([]);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  useEffect(() => {
    if (!employee) return;

    let cancelled = false;
    const loadEmployeeLeaveDetails = async () => {
      setLoading(true);
      setErrorMsg(null);
      const currentYear = new Date().getFullYear();

      try {
        const [balRes, reqRes] = await Promise.all([
          apiClient
            .get<LeaveBalance[]>(`/api/leave-balances/employee/${employee.id}`, {
              params: { year: currentYear },
            })
            .catch(() =>
              apiClient.get<LeaveBalance[]>(`/api/leave-balances/employee/${employee.id}`)
            ),
          apiClient.get<LeaveRequest[]>(`/api/leave-requests/employee/${employee.id}`),
        ]);

        if (!cancelled) {
          setBalances(balRes.data || []);
          const sortedHistory = [...(reqRes.data || [])].sort((a, b) =>
            (b.startDate || '').localeCompare(a.startDate || '')
          );
          setHistory(sortedHistory);
        }
      } catch (err: unknown) {
        if (!cancelled) {
          const axiosErr = err as { response?: { data?: { message?: string } } };
          setErrorMsg(
            axiosErr.response?.data?.message ||
              'Unable to retrieve leave balances and history for this team member.'
          );
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    loadEmployeeLeaveDetails();
    return () => {
      cancelled = true;
    };
  }, [employee]);

  const fullName = employee ? `${employee.firstName} ${employee.lastName}` : '';

  return (
    <Drawer
      anchor="right"
      open={Boolean(employee)}
      onClose={onClose}
      PaperProps={{
        sx: {
          width: { xs: '100%', sm: 460 },
          backgroundColor: '#FAF9F6',
          borderLeft: '1px solid #EAE6DF',
        },
      }}
    >
      {employee && (
        <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
          {/* Top Obsidian Profile Header */}
          <Box
            sx={{
              backgroundColor: '#0D0F12',
              color: '#FAF9F6',
              p: 3,
              borderBottom: '1px solid rgba(197, 168, 128, 0.25)',
            }}
          >
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.75 }}>
                <Avatar
                  sx={{
                    width: 46,
                    height: 46,
                    bgcolor: 'rgba(197, 168, 128, 0.14)',
                    border: '1.5px solid #C5A880',
                    color: '#C5A880',
                    fontFamily: '"Playfair Display", Georgia, serif',
                    fontWeight: 600,
                  }}
                >
                  {employee.firstName?.charAt(0).toUpperCase()}
                </Avatar>
                <Box>
                  <Typography
                    variant="caption"
                    sx={{ color: '#C5A880', letterSpacing: '0.12em', fontWeight: 600, display: 'block' }}
                  >
                    TEAM MEMBER PROFILE
                  </Typography>
                  <Typography
                    variant="h6"
                    sx={{ fontFamily: '"Playfair Display", Georgia, serif', fontWeight: 600, color: '#FAF9F6' }}
                  >
                    {fullName}
                  </Typography>
                </Box>
              </Box>
              <IconButton onClick={onClose} sx={{ color: '#FAF9F6' }} size="small" aria-label="close drawer">
                <CloseIcon fontSize="small" />
              </IconButton>
            </Box>

            <Stack spacing={0.6} sx={{ fontSize: '0.8rem', color: '#D1D5DB' }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <BadgeOutlinedIcon sx={{ fontSize: 15, color: '#C5A880' }} />
                <span>
                  {employee.employeeCode} · Status: {employee.status}
                </span>
              </Box>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <EmailOutlinedIcon sx={{ fontSize: 15, color: '#C5A880' }} />
                <span>{employee.email || 'No email linked'}</span>
              </Box>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <BusinessOutlinedIcon sx={{ fontSize: 15, color: '#C5A880' }} />
                <span>{employee.department?.name || 'General Department'}</span>
              </Box>
            </Stack>
          </Box>

          {/* Body Content */}
          <Box sx={{ p: 3, overflowY: 'auto', flexGrow: 1 }}>
            {loading ? (
              <Box sx={{ py: 6, textAlign: 'center' }}>
                <CircularProgress size={28} sx={{ color: '#B8976C', mb: 1.5 }} />
                <Typography variant="body2" sx={{ color: '#6B7280' }}>
                  Loading real-time employee balances and requests...
                </Typography>
              </Box>
            ) : errorMsg ? (
              <Alert
                severity="error"
                sx={{ backgroundColor: '#FAF0F0', color: '#8C2D2D', border: '1px solid #ECCACA' }}
              >
                {errorMsg}
              </Alert>
            ) : (
              <Stack spacing={3}>
                {/* Leave Balances Section */}
                <Box>
                  <Typography
                    variant="subtitle2"
                    sx={{
                      color: '#B8976C',
                      letterSpacing: '0.12em',
                      fontWeight: 700,
                      fontSize: '0.725rem',
                      mb: 1.5,
                    }}
                  >
                    LEAVE BALANCES ({balances[0]?.year || new Date().getFullYear()})
                  </Typography>

                  {balances.length === 0 ? (
                    <Box
                      sx={{
                        p: 2.5,
                        backgroundColor: '#FFFFFF',
                        border: '1px solid #EAE6DF',
                        borderRadius: 1.5,
                        textAlign: 'center',
                      }}
                    >
                      <Typography variant="body2" sx={{ color: '#6B7280' }}>
                        No leave balances initialized for this employee.
                      </Typography>
                    </Box>
                  ) : (
                    <Stack spacing={1.5}>
                      {balances.map((bal) => {
                        const avail = bal.available ?? bal.availableDays ?? 0;
                        const used = bal.used ?? bal.usedDays ?? 0;
                        const total = bal.total ?? bal.totalDays ?? (avail + used || 1);
                        const pct = Math.min(100, Math.round((used / total) * 100));

                        return (
                          <Box
                            key={bal.id}
                            sx={{
                              p: 2,
                              backgroundColor: '#FFFFFF',
                              border: '1px solid #EAE6DF',
                              borderRadius: 1.5,
                            }}
                          >
                            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                              <Typography variant="body2" sx={{ fontWeight: 600, color: '#16181B' }}>
                                {bal.leaveType?.name || 'Leave Quota'}
                              </Typography>
                              <Chip
                                size="small"
                                label={`${avail} / ${total} available`}
                                sx={{
                                  height: 20,
                                  fontSize: '0.7rem',
                                  fontWeight: 600,
                                  backgroundColor: 'rgba(184, 151, 108, 0.12)',
                                  color: '#8C6E45',
                                }}
                              />
                            </Box>
                            <LinearProgress
                              variant="determinate"
                              value={pct}
                              sx={{
                                height: 6,
                                borderRadius: 3,
                                backgroundColor: '#F3EFEA',
                                '& .MuiLinearProgress-bar': {
                                  backgroundColor: pct > 80 ? '#8C2D2D' : '#B8976C',
                                  borderRadius: 3,
                                },
                              }}
                            />
                            <Typography variant="caption" sx={{ color: '#6B7280', display: 'block', mt: 0.75 }}>
                              Used: {used} days ({pct}%)
                            </Typography>
                          </Box>
                        );
                      })}
                    </Stack>
                  )}
                </Box>

                <Divider sx={{ borderColor: '#EAE6DF' }} />

                {/* Employee Leave Request History */}
                <Box>
                  <Typography
                    variant="subtitle2"
                    sx={{
                      color: '#B8976C',
                      letterSpacing: '0.12em',
                      fontWeight: 700,
                      fontSize: '0.725rem',
                      mb: 1.5,
                    }}
                  >
                    LEAVE HISTORY ({history.length})
                  </Typography>

                  {history.length === 0 ? (
                    <Box
                      sx={{
                        p: 2.5,
                        backgroundColor: '#FFFFFF',
                        border: '1px solid #EAE6DF',
                        borderRadius: 1.5,
                        textAlign: 'center',
                      }}
                    >
                      <Typography variant="body2" sx={{ color: '#6B7280' }}>
                        No leave requests submitted by this team member.
                      </Typography>
                    </Box>
                  ) : (
                    <Stack spacing={1.25}>
                      {history.map((req) => (
                        <Box
                          key={req.id}
                          sx={{
                            p: 1.75,
                            backgroundColor: '#FFFFFF',
                            border: '1px solid #EAE6DF',
                            borderRadius: 1.5,
                          }}
                        >
                          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 0.5 }}>
                            <Typography variant="body2" sx={{ fontWeight: 600, color: '#16181B' }}>
                              {req.leaveType?.name || 'Leave'}
                            </Typography>
                            <LeaveStatusChip status={req.status} />
                          </Box>
                          <Typography variant="caption" sx={{ color: '#4B5563', display: 'block' }}>
                            {req.startDate} to {req.endDate} · {req.requestedDays ?? req.days ?? 1} working day(s)
                          </Typography>
                          {req.reason && (
                            <Typography variant="caption" sx={{ color: '#6B7280', display: 'block', mt: 0.5 }}>
                              {req.reason}
                            </Typography>
                          )}
                          {req.rejectionReason && (
                            <Typography variant="caption" sx={{ color: '#8C2D2D', display: 'block', mt: 0.5 }}>
                              Rejected: {req.rejectionReason}
                            </Typography>
                          )}
                        </Box>
                      ))}
                    </Stack>
                  )}
                </Box>
              </Stack>
            )}
          </Box>
        </Box>
      )}
    </Drawer>
  );
};

export default EmployeeLeaveDrawer;
