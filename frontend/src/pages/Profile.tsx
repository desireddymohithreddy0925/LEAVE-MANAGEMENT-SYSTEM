import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  TextField,
  Button,
  Chip,
  Divider,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  CircularProgress,
  Avatar,
  Stack,
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import LockResetIcon from '@mui/icons-material/LockReset';
import BadgeOutlinedIcon from '@mui/icons-material/BadgeOutlined';
import EmailOutlinedIcon from '@mui/icons-material/EmailOutlined';
import PhoneOutlinedIcon from '@mui/icons-material/PhoneOutlined';
import BusinessOutlinedIcon from '@mui/icons-material/BusinessOutlined';
import SupervisorAccountOutlinedIcon from '@mui/icons-material/SupervisorAccountOutlined';
import { useAuth } from '../context/AuthContext';
import { useNotification } from '../context/NotificationContext';
import apiClient from '../api/client';
import PageHeader from '../components/common/PageHeader';
import LoadingSpinner from '../components/common/LoadingSpinner';
import { Employee, EmployeeProfileUpdate, PasswordChangeRequest } from '../types';

export const Profile: React.FC = () => {
  const { user, employee, refreshProfile, isLoading } = useAuth();
  const { showSuccess, showError } = useNotification();

  const [openEditDialog, setOpenEditDialog] = useState<boolean>(false);
  const [savingProfile, setSavingProfile] = useState<boolean>(false);
  const [profileForm, setProfileForm] = useState<EmployeeProfileUpdate>({
    firstName: '',
    lastName: '',
    phone: '',
  });

  // Password change state
  const [currentPassword, setCurrentPassword] = useState<string>('');
  const [newPassword, setNewPassword] = useState<string>('');
  const [confirmPassword, setConfirmPassword] = useState<string>('');
  const [changingPassword, setChangingPassword] = useState<boolean>(false);

  useEffect(() => {
    if (employee) {
      setProfileForm({
        firstName: employee.firstName || '',
        lastName: employee.lastName || '',
        phone: employee.phone || '',
      });
    }
  }, [employee]);

  const handleOpenEdit = () => {
    if (employee) {
      setProfileForm({
        firstName: employee.firstName || '',
        lastName: employee.lastName || '',
        phone: employee.phone || '',
      });
    }
    setOpenEditDialog(true);
  };

  const handleCloseEdit = () => {
    setOpenEditDialog(false);
  };

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!profileForm.firstName.trim() || !profileForm.lastName.trim()) {
      showError('First name and last name are required');
      return;
    }

    try {
      setSavingProfile(true);
      await apiClient.put<Employee>('/api/employees/me/profile', profileForm);
      await refreshProfile();
      showSuccess('Profile updated successfully');
      setOpenEditDialog(false);
    } catch (err: unknown) {
      const errObj = err as { response?: { data?: { message?: string } } };
      showError(errObj?.response?.data?.message || 'Failed to update profile');
    } finally {
      setSavingProfile(false);
    }
  };

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!currentPassword) {
      showError('Please enter your current password');
      return;
    }

    if (newPassword.length < 8) {
      showError('New password must be at least 8 characters long');
      return;
    }

    if (newPassword !== confirmPassword) {
      showError('New password and confirmation do not match');
      return;
    }

    try {
      setChangingPassword(true);
      const payload: PasswordChangeRequest = {
        currentPassword,
        newPassword,
      };
      await apiClient.put('/api/employees/me/password', payload);
      showSuccess('Password updated successfully');
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err: unknown) {
      const errObj = err as { response?: { data?: { message?: string } } };
      showError(errObj?.response?.data?.message || 'Failed to change password');
    } finally {
      setChangingPassword(false);
    }
  };

  if (isLoading) {
    return <LoadingSpinner message="Loading profile..." />;
  }

  const initials = employee
    ? `${employee.firstName?.charAt(0) || ''}${employee.lastName?.charAt(0) || ''}`.toUpperCase()
    : user?.email?.charAt(0).toUpperCase() || 'U';

  return (
    <Box>
      <PageHeader
        category="IDENTITY & SECURITY"
        title="My Profile"
        subtitle="Manage your personal information, corporate details, and account security credentials."
        action={
          employee && (
            <Button
              variant="outlined"
              startIcon={<EditIcon sx={{ color: '#B8976C' }} />}
              onClick={handleOpenEdit}
              sx={{
                borderColor: '#D4CEBE',
                color: '#16181B',
                '&:hover': {
                  borderColor: '#B8976C',
                  backgroundColor: 'rgba(184, 151, 108, 0.08)',
                },
              }}
            >
              Edit Details
            </Button>
          )
        }
      />

      <Grid container spacing={3}>
        {/* Profile Card with Left Champagne Accent Bar (Image 3 Style) */}
        <Grid item xs={12} md={7}>
          <Card
            sx={{
              borderLeft: '4px solid #B8976C',
              backgroundColor: '#FFFFFF',
            }}
          >
            <CardContent sx={{ p: { xs: 3, sm: 4 } }}>
              {/* Header with avatar & name */}
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2.5, mb: 3 }}>
                <Avatar
                  sx={{
                    width: 64,
                    height: 64,
                    bgcolor: 'rgba(197, 168, 128, 0.1)',
                    border: '2px solid #C5A880',
                    color: '#B8976C',
                    fontSize: '1.4rem',
                    fontWeight: 600,
                    fontFamily: '"Playfair Display", Georgia, serif',
                  }}
                >
                  {initials}
                </Avatar>
                <Box sx={{ flexGrow: 1 }}>
                  <Typography
                    variant="h5"
                    sx={{
                      fontFamily: '"Playfair Display", Georgia, serif',
                      fontWeight: 600,
                      color: '#16181B',
                    }}
                  >
                    {employee ? `${employee.firstName} ${employee.lastName}` : user?.email}
                  </Typography>
                  <Typography variant="body2" sx={{ color: '#6B7280', mt: 0.25 }}>
                    {employee?.department?.name || 'Leave Management System Member'}
                  </Typography>
                  <Box sx={{ mt: 1, display: 'flex', gap: 1 }}>
                    <Chip
                      size="small"
                      label={employee?.status || (user?.active ? 'Active' : 'Inactive')}
                      sx={{
                        backgroundColor: '#EBF5EE',
                        color: '#235A36',
                        border: '1px solid #D1E8D7',
                        fontWeight: 500,
                      }}
                    />
                    {user?.roles?.map((role) => (
                      <Chip
                        key={role.id}
                        size="small"
                        label={role.name.replace('ROLE_', '')}
                        variant="outlined"
                        sx={{
                          fontSize: '0.725rem',
                          borderColor: '#D4CEBE',
                          color: '#B8976C',
                        }}
                      />
                    ))}
                  </Box>
                </Box>
              </Box>

              <Divider sx={{ my: 2.5, borderColor: '#EAE6DF' }} />

              {/* Details Grid */}
              <Typography
                variant="subtitle2"
                sx={{
                  fontWeight: 600,
                  mb: 2,
                  color: '#B8976C',
                  textTransform: 'uppercase',
                  letterSpacing: '0.12em',
                  fontSize: '0.725rem',
                }}
              >
                Employment Details
              </Typography>

              <Grid container spacing={2.5}>
                <Grid item xs={12} sm={6}>
                  <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5 }}>
                    <BadgeOutlinedIcon sx={{ color: '#B8976C', fontSize: 20, mt: 0.25 }} />
                    <Box>
                      <Typography variant="caption" sx={{ color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                        Employee ID
                      </Typography>
                      <Typography variant="body1" sx={{ fontWeight: 600, color: '#16181B' }}>
                        {employee?.employeeCode || 'Not Assigned'}
                      </Typography>
                    </Box>
                  </Box>
                </Grid>

                <Grid item xs={12} sm={6}>
                  <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5 }}>
                    <EmailOutlinedIcon sx={{ color: '#B8976C', fontSize: 20, mt: 0.25 }} />
                    <Box>
                      <Typography variant="caption" sx={{ color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                        Corporate Email
                      </Typography>
                      <Typography variant="body1" sx={{ fontWeight: 600, color: '#16181B' }}>
                        {employee?.email || user?.email}
                      </Typography>
                    </Box>
                  </Box>
                </Grid>

                <Grid item xs={12} sm={6}>
                  <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5 }}>
                    <BusinessOutlinedIcon sx={{ color: '#B8976C', fontSize: 20, mt: 0.25 }} />
                    <Box>
                      <Typography variant="caption" sx={{ color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                        Department
                      </Typography>
                      <Typography variant="body1" sx={{ fontWeight: 600, color: '#16181B' }}>
                        {employee?.department?.name || 'General Enterprise'}
                      </Typography>
                    </Box>
                  </Box>
                </Grid>

                <Grid item xs={12} sm={6}>
                  <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5 }}>
                    <PhoneOutlinedIcon sx={{ color: '#B8976C', fontSize: 20, mt: 0.25 }} />
                    <Box>
                      <Typography variant="caption" sx={{ color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                        Contact Phone
                      </Typography>
                      <Typography variant="body1" sx={{ fontWeight: 600, color: '#16181B' }}>
                        {employee?.phone || 'Not provided'}
                      </Typography>
                    </Box>
                  </Box>
                </Grid>

                {employee?.managerName && (
                  <Grid item xs={12} sm={6}>
                    <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5 }}>
                      <SupervisorAccountOutlinedIcon sx={{ color: '#B8976C', fontSize: 20, mt: 0.25 }} />
                      <Box>
                        <Typography variant="caption" sx={{ color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                          Reporting Manager
                        </Typography>
                        <Typography variant="body1" sx={{ fontWeight: 600, color: '#16181B' }}>
                          {employee.managerName}
                        </Typography>
                      </Box>
                    </Box>
                  </Grid>
                )}
              </Grid>
            </CardContent>
          </Card>
        </Grid>

        {/* Change Password Card with Left Obsidian Accent Bar */}
        <Grid item xs={12} md={5}>
          <Card
            sx={{
              borderLeft: '4px solid #111315',
              backgroundColor: '#FFFFFF',
            }}
          >
            <CardContent sx={{ p: { xs: 3, sm: 4 } }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1 }}>
                <LockResetIcon sx={{ color: '#111315', fontSize: 24 }} />
                <Typography
                  variant="h6"
                  sx={{
                    fontFamily: '"Playfair Display", Georgia, serif',
                    fontWeight: 600,
                    color: '#16181B',
                  }}
                >
                  Account Security
                </Typography>
              </Box>
              <Typography variant="body2" sx={{ color: '#6B7280', mb: 3 }}>
                Update your account password. Must contain at least 8 characters.
              </Typography>

              <Box component="form" onSubmit={handleChangePassword}>
                <Stack spacing={2.5}>
                  <TextField
                    label="Current Password"
                    type="password"
                    fullWidth
                    required
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    size="small"
                  />

                  <TextField
                    label="New Password"
                    type="password"
                    fullWidth
                    required
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    helperText="At least 8 characters"
                    size="small"
                  />

                  <TextField
                    label="Confirm New Password"
                    type="password"
                    fullWidth
                    required
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    size="small"
                  />

                  <Button
                    type="submit"
                    variant="contained"
                    disabled={changingPassword}
                    startIcon={changingPassword ? <CircularProgress size={18} color="inherit" /> : <LockResetIcon />}
                    sx={{
                      mt: 1,
                      backgroundColor: '#111315',
                      border: '1px solid #111315',
                      '&:hover': {
                        backgroundColor: '#23272C',
                      },
                    }}
                  >
                    {changingPassword ? 'Updating...' : 'Update Password'}
                  </Button>
                </Stack>
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Edit Profile Dialog */}
      <Dialog
        open={openEditDialog}
        onClose={handleCloseEdit}
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
            fontFamily: '"Playfair Display", Georgia, serif',
            fontWeight: 600,
            color: '#16181B',
          }}
        >
          Edit Personal Details
        </DialogTitle>
        <Box component="form" onSubmit={handleSaveProfile}>
          <DialogContent dividers sx={{ borderColor: '#EAE6DF' }}>
            <Stack spacing={2.5} sx={{ mt: 1 }}>
              <TextField
                label="First Name"
                fullWidth
                required
                value={profileForm.firstName}
                onChange={(e) => setProfileForm({ ...profileForm, firstName: e.target.value })}
                size="small"
              />

              <TextField
                label="Last Name"
                fullWidth
                required
                value={profileForm.lastName}
                onChange={(e) => setProfileForm({ ...profileForm, lastName: e.target.value })}
                size="small"
              />

              <TextField
                label="Phone Number"
                fullWidth
                value={profileForm.phone}
                onChange={(e) => setProfileForm({ ...profileForm, phone: e.target.value })}
                placeholder="+1 555-0199"
                size="small"
                helperText="Optional contact number"
              />
            </Stack>
          </DialogContent>
          <DialogActions sx={{ px: 3, py: 2 }}>
            <Button onClick={handleCloseEdit} color="inherit">
              Cancel
            </Button>
            <Button
              type="submit"
              variant="contained"
              disabled={savingProfile}
              sx={{
                backgroundColor: '#111315',
                '&:hover': { backgroundColor: '#23272C' },
              }}
              startIcon={savingProfile ? <CircularProgress size={18} color="inherit" /> : null}
            >
              {savingProfile ? 'Saving...' : 'Save Changes'}
            </Button>
          </DialogActions>
        </Box>
      </Dialog>
    </Box>
  );
};

export default Profile;
