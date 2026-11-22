import React, { useEffect, useState } from 'react';
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
  Tabs,
  Tab,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  DialogContentText,
  Paper,
} from '@mui/material';
import EventAvailableOutlinedIcon from '@mui/icons-material/EventAvailableOutlined';
import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined';
import AddIcon from '@mui/icons-material/Add';
import { useNavigate } from 'react-router-dom';
import apiClient from '../api/client';
import { LeaveRequest, Page } from '../types';
import LoadingSpinner from '../components/common/LoadingSpinner';
import PageHeader from '../components/common/PageHeader';
import { useNotification } from '../context/NotificationContext';

export const MyLeaves: React.FC = () => {
  const navigate = useNavigate();
  const { showSuccess, showError } = useNotification();

  const [requests, setRequests] = useState<LeaveRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedTab, setSelectedTab] = useState<'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED'>('ALL');
  const [cancelTargetId, setCancelTargetId] = useState<number | null>(null);
  const [cancelling, setCancelling] = useState(false);

  const fetchLeaves = async () => {
    try {
      const response = await apiClient.get<Page<LeaveRequest>>('/api/leave-requests/me?page=0&size=50');
      setRequests(response.data?.content || []);
    } catch {
      setRequests([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLeaves();
  }, []);

  const handleConfirmCancel = async () => {
    if (!cancelTargetId) return;

    setCancelling(true);
    try {
      await apiClient.put(`/api/leave-requests/${cancelTargetId}/cancel`);
      showSuccess('Leave request cancelled successfully.');
      setCancelTargetId(null);
      await fetchLeaves();
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      showError(axiosErr.response?.data?.message || 'Failed to cancel leave request.');
    } finally {
      setCancelling(false);
    }
  };

  const filteredRequests = requests.filter((r) => {
    if (selectedTab === 'ALL') return true;
    return r.status === selectedTab;
  });

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

  if (loading) {
    return <LoadingSpinner message="Loading leave records..." minHeight="50vh" />;
  }

  return (
    <Box>
      <PageHeader
        category="RECORDS & AUDIT"
        title="My Leave History"
        subtitle="Review past, approved, and pending leave applications with self-service cancellation capability."
        action={
          <Button
            variant="contained"
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
            New Application
          </Button>
        }
      />

      <Card sx={{ border: '1px solid #EAE6DF' }}>
        {/* Filter Tabs with Champagne Underline */}
        <Box sx={{ borderBottom: '1px solid #EAE6DF', px: 2 }}>
          <Tabs
            value={selectedTab}
            onChange={(_, val) => setSelectedTab(val)}
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
            <Tab label={`All (${requests.length})`} value="ALL" />
            <Tab label={`Pending (${requests.filter((r) => r.status === 'PENDING').length})`} value="PENDING" />
            <Tab label={`Approved (${requests.filter((r) => r.status === 'APPROVED').length})`} value="APPROVED" />
            <Tab label={`Rejected (${requests.filter((r) => r.status === 'REJECTED').length})`} value="REJECTED" />
          </Tabs>
        </Box>

        {/* Requests Table */}
        <TableContainer component={Paper} elevation={0}>
          <Table sx={{ minWidth: 700 }}>
            <TableHead sx={{ backgroundColor: '#F7F5F0' }}>
              <TableRow>
                <TableCell sx={{ fontWeight: 600, color: '#16181B', fontSize: '0.8rem', letterSpacing: '0.05em' }}>
                  LEAVE TYPE
                </TableCell>
                <TableCell sx={{ fontWeight: 600, color: '#16181B', fontSize: '0.8rem', letterSpacing: '0.05em' }}>
                  START DATE
                </TableCell>
                <TableCell sx={{ fontWeight: 600, color: '#16181B', fontSize: '0.8rem', letterSpacing: '0.05em' }}>
                  END DATE
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
                <TableCell align="right" sx={{ fontWeight: 600, color: '#16181B', fontSize: '0.8rem', letterSpacing: '0.05em' }}>
                  ACTION
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredRequests.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7} align="center" sx={{ py: 6, color: '#6B7280' }}>
                    <EventAvailableOutlinedIcon sx={{ fontSize: 36, color: '#B8976C', mb: 1, display: 'block', mx: 'auto' }} />
                    <Typography variant="body2" sx={{ color: '#6B7280' }}>
                      No leave records found matching the selected filter.
                    </Typography>
                  </TableCell>
                </TableRow>
              ) : (
                filteredRequests.map((req) => (
                  <TableRow key={req.id} hover sx={{ '&:last-child td, &:last-child th': { border: 0 } }}>
                    <TableCell sx={{ fontWeight: 600, color: '#16181B' }}>
                      {req.leaveType?.name || 'Leave'}
                    </TableCell>
                    <TableCell sx={{ color: '#4B5563' }}>{req.startDate}</TableCell>
                    <TableCell sx={{ color: '#4B5563' }}>{req.endDate}</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>{req.days}</TableCell>
                    <TableCell sx={{ color: '#6B7280', maxWidth: 240, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {req.reason || '-'}
                    </TableCell>
                    <TableCell>{getStatusChip(req.status)}</TableCell>
                    <TableCell align="right">
                      {req.status === 'PENDING' && (
                        <Button
                          size="small"
                          color="error"
                          variant="outlined"
                          startIcon={<CancelOutlinedIcon sx={{ fontSize: 16 }} />}
                          onClick={() => setCancelTargetId(req.id)}
                          sx={{
                            fontSize: '0.78rem',
                            py: 0.35,
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
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      {/* Confirmation Dialog for Cancellation */}
      <Dialog
        open={Boolean(cancelTargetId)}
        onClose={() => setCancelTargetId(null)}
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
            Are you sure you want to cancel this pending leave request? This action will restore your allocated quota balance.
          </DialogContentText>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setCancelTargetId(null)} color="inherit">
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
