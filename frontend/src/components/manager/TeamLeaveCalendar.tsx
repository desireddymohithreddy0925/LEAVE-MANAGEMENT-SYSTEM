import React, { useMemo, useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  IconButton,
  Button,
  Stack,
  ToggleButtonGroup,
  ToggleButton,
  Divider,
  Grid,
  Tooltip,
} from '@mui/material';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import TodayOutlinedIcon from '@mui/icons-material/TodayOutlined';
import EventBusyOutlinedIcon from '@mui/icons-material/EventBusyOutlined';
import PersonSearchOutlinedIcon from '@mui/icons-material/PersonSearchOutlined';
import { Employee, LeaveRequest } from '../../types';
import LeaveStatusChip from '../common/LeaveStatusChip';

interface TeamLeaveCalendarProps {
  leaveRequests: LeaveRequest[];
  teamSize: number;
  onInspectEmployee?: (employee: Employee) => void;
}

type CalendarFilter = 'ACTIVE' | 'APPROVED' | 'PENDING';

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

const formatIsoDate = (date: Date): string => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const isPendingStatus = (status: string): boolean =>
  status === 'PENDING' || status === 'PARTIALLY_APPROVED';

export const TeamLeaveCalendar: React.FC<TeamLeaveCalendarProps> = ({
  leaveRequests,
  teamSize,
  onInspectEmployee,
}) => {
  const today = useMemo(() => new Date(), []);
  const todayIso = useMemo(() => formatIsoDate(today), [today]);

  const [currentMonth, setCurrentMonth] = useState<Date>(
    () => new Date(today.getFullYear(), today.getMonth(), 1)
  );
  const [selectedDateIso, setSelectedDateIso] = useState<string>(todayIso);
  const [filterMode, setFilterMode] = useState<CalendarFilter>('ACTIVE');

  // Filter requests shown on the calendar according to selected toggle
  const calendarLeaves = useMemo(() => {
    return leaveRequests.filter((req) => {
      if (filterMode === 'APPROVED') return req.status === 'APPROVED';
      if (filterMode === 'PENDING') return isPendingStatus(req.status);
      return req.status === 'APPROVED' || isPendingStatus(req.status);
    });
  }, [leaveRequests, filterMode]);

  // Build 6-week (42-day) grid aligned to Monday start
  const calendarDays = useMemo(() => {
    const year = currentMonth.getFullYear();
    const month = currentMonth.getMonth();
    const firstOfMonth = new Date(year, month, 1);
    const jsDay = firstOfMonth.getDay(); // 0=Sun .. 6=Sat
    const mondayOffset = (jsDay + 6) % 7; // Convert to 0=Mon .. 6=Sun

    const gridStart = new Date(year, month, 1 - mondayOffset);
    const days: Array<{ date: Date; iso: string; inMonth: boolean; isWeekend: boolean }> = [];

    for (let i = 0; i < 42; i++) {
      const cellDate = new Date(gridStart.getFullYear(), gridStart.getMonth(), gridStart.getDate() + i);
      const dayOfWeek = cellDate.getDay();
      days.push({
        date: cellDate,
        iso: formatIsoDate(cellDate),
        inMonth: cellDate.getMonth() === month,
        isWeekend: dayOfWeek === 0 || dayOfWeek === 6,
      });
    }
    return days;
  }, [currentMonth]);

  // Pre-compute map of ISO date -> active team leaves for visible grid cells
  const leavesByDate = useMemo(() => {
    const map = new Map<string, LeaveRequest[]>();
    if (calendarLeaves.length === 0) return map;

    calendarDays.forEach((cell) => {
      // Only compute leave overlaps on working days unless leave specifically spans; still display range cleanly
      const active = calendarLeaves.filter(
        (req) => req.startDate <= cell.iso && req.endDate >= cell.iso
      );
      if (active.length > 0) {
        map.set(cell.iso, active);
      }
    });
    return map;
  }, [calendarDays, calendarLeaves]);

  const selectedDayLeaves = useMemo(() => {
    return calendarLeaves.filter(
      (req) => req.startDate <= selectedDateIso && req.endDate >= selectedDateIso
    );
  }, [calendarLeaves, selectedDateIso]);

  const handlePrevMonth = () => {
    setCurrentMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() - 1, 1));
  };

  const handleNextMonth = () => {
    setCurrentMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() + 1, 1));
  };

  const handleJumpToday = () => {
    setCurrentMonth(new Date(today.getFullYear(), today.getMonth(), 1));
    setSelectedDateIso(todayIso);
  };

  const monthLabel = currentMonth.toLocaleDateString(undefined, {
    month: 'long',
    year: 'numeric',
  });

  const selectedDateDisplay = useMemo(() => {
    const parsed = new Date(`${selectedDateIso}T00:00:00`);
    if (isNaN(parsed.getTime())) return selectedDateIso;
    return parsed.toLocaleDateString(undefined, {
      weekday: 'long',
      month: 'long',
      day: 'numeric',
      year: 'numeric',
    });
  }, [selectedDateIso]);

  return (
    <Grid container spacing={3}>
      {/* Calendar Grid */}
      <Grid item xs={12} lg={8}>
        <Card
          sx={{
            border: '1px solid #EAE6DF',
            backgroundColor: '#FFFFFF',
            boxShadow: '0 2px 8px rgba(17, 19, 21, 0.04)',
          }}
        >
          {/* Calendar Toolbar */}
          <Box
            sx={{
              p: 2.5,
              borderBottom: '1px solid #EAE6DF',
              display: 'flex',
              flexDirection: { xs: 'column', sm: 'row' },
              alignItems: { xs: 'stretch', sm: 'center' },
              justifyContent: 'space-between',
              gap: 2,
            }}
          >
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <IconButton
                size="small"
                onClick={handlePrevMonth}
                aria-label="previous month"
                sx={{ border: '1px solid #EAE6DF', '&:hover': { borderColor: '#B8976C' } }}
              >
                <ChevronLeftIcon fontSize="small" />
              </IconButton>
              <Typography
                variant="h6"
                sx={{
                  fontFamily: '"Playfair Display", Georgia, serif',
                  fontWeight: 600,
                  color: '#16181B',
                  minWidth: 165,
                  textAlign: 'center',
                }}
              >
                {monthLabel}
              </Typography>
              <IconButton
                size="small"
                onClick={handleNextMonth}
                aria-label="next month"
                sx={{ border: '1px solid #EAE6DF', '&:hover': { borderColor: '#B8976C' } }}
              >
                <ChevronRightIcon fontSize="small" />
              </IconButton>
              <Button
                size="small"
                variant="outlined"
                startIcon={<TodayOutlinedIcon fontSize="small" />}
                onClick={handleJumpToday}
                sx={{
                  ml: 1,
                  borderColor: '#D4CEBE',
                  color: '#16181B',
                  fontSize: '0.75rem',
                  '&:hover': { borderColor: '#B8976C', backgroundColor: 'rgba(184, 151, 108, 0.06)' },
                }}
              >
                Today
              </Button>
            </Box>

            <ToggleButtonGroup
              size="small"
              exclusive
              value={filterMode}
              onChange={(_, val) => val && setFilterMode(val)}
              aria-label="calendar status filter"
              sx={{
                '& .MuiToggleButton-root': {
                  textTransform: 'none',
                  fontSize: '0.775rem',
                  px: 1.5,
                  color: '#6B7280',
                  borderColor: '#EAE6DF',
                  '&.Mui-selected': {
                    backgroundColor: '#111315',
                    color: '#FAF9F6',
                    '&:hover': { backgroundColor: '#23272C' },
                  },
                },
              }}
            >
              <ToggleButton value="ACTIVE">All Active</ToggleButton>
              <ToggleButton value="APPROVED">Approved</ToggleButton>
              <ToggleButton value="PENDING">Pending</ToggleButton>
            </ToggleButtonGroup>
          </Box>

          {/* Weekday Header + 7-Column Calendar Matrix */}
          <Box sx={{ p: { xs: 1.5, sm: 2.5 }, overflowX: 'auto' }}>
            <Box sx={{ minWidth: 620 }}>
              <Box
                sx={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(7, minmax(0, 1fr))',
                  gap: 0.75,
                  mb: 0.75,
                }}
              >
                {WEEKDAYS.map((day) => (
                  <Typography
                    key={day}
                    variant="caption"
                    sx={{
                      textAlign: 'center',
                      fontWeight: 700,
                      color: day === 'Sat' || day === 'Sun' ? '#9CA3AF' : '#6B7280',
                      letterSpacing: '0.08em',
                      textTransform: 'uppercase',
                      py: 0.5,
                    }}
                  >
                    {day}
                  </Typography>
                ))}
              </Box>

              <Box
                sx={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(7, minmax(0, 1fr))',
                  gap: 0.75,
                }}
              >
                {calendarDays.map((cell) => {
                  const dayLeaves = leavesByDate.get(cell.iso) || [];
                  const isToday = cell.iso === todayIso;
                  const isSelected = cell.iso === selectedDateIso;

                  return (
                    <Box
                      key={cell.iso}
                      onClick={() => setSelectedDateIso(cell.iso)}
                      sx={{
                        minHeight: 88,
                        p: 0.85,
                        borderRadius: 1.25,
                        cursor: 'pointer',
                        border: isSelected
                          ? '1.5px solid #B8976C'
                          : isToday
                          ? '1.5px solid #111315'
                          : '1px solid #EAE6DF',
                        backgroundColor: !cell.inMonth
                          ? '#F9F8F5'
                          : cell.isWeekend
                          ? '#FAF9F6'
                          : '#FFFFFF',
                        opacity: cell.inMonth ? 1 : 0.55,
                        transition: 'border-color 0.15s ease, background-color 0.15s ease',
                        '&:hover': {
                          borderColor: '#B8976C',
                          backgroundColor: '#FAF8F4',
                        },
                      }}
                    >
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 0.5 }}>
                        <Typography
                          variant="caption"
                          sx={{
                            fontWeight: isToday || isSelected ? 700 : 500,
                            color: isToday ? '#FAF9F6' : '#16181B',
                            backgroundColor: isToday ? '#111315' : 'transparent',
                            borderRadius: '50%',
                            width: 22,
                            height: 22,
                            display: 'inline-flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            fontSize: '0.725rem',
                          }}
                        >
                          {cell.date.getDate()}
                        </Typography>
                        {dayLeaves.length > 0 && (
                          <Typography
                            variant="caption"
                            sx={{ fontSize: '0.65rem', color: '#8C6E45', fontWeight: 600 }}
                          >
                            {dayLeaves.length} away
                          </Typography>
                        )}
                      </Box>

                      {/* Leave event pills inside day cell */}
                      <Stack spacing={0.4}>
                        {dayLeaves.slice(0, 2).map((leave) => {
                          const approved = leave.status === 'APPROVED';
                          const empName = leave.employee
                            ? `${leave.employee.firstName} ${leave.employee.lastName?.charAt(0) || ''}.`
                            : 'Member';
                          return (
                            <Tooltip
                              key={leave.id}
                              title={`${leave.employee?.firstName || ''} ${leave.employee?.lastName || ''} · ${leave.leaveType?.name || 'Leave'} (${leave.status})`}
                            >
                              <Box
                                sx={{
                                  px: 0.65,
                                  py: 0.2,
                                  borderRadius: 0.75,
                                  fontSize: '0.66rem',
                                  fontWeight: 600,
                                  whiteSpace: 'nowrap',
                                  overflow: 'hidden',
                                  textOverflow: 'ellipsis',
                                  backgroundColor: approved ? '#EBF5EE' : '#FDF8EB',
                                  color: approved ? '#235A36' : '#8C6B1C',
                                  border: approved ? '1px solid #D1E8D7' : '1px dashed #E5C88B',
                                }}
                              >
                                {empName}
                              </Box>
                            </Tooltip>
                          );
                        })}
                        {dayLeaves.length > 2 && (
                          <Typography
                            variant="caption"
                            sx={{ fontSize: '0.65rem', color: '#6B7280', pl: 0.5, fontWeight: 500 }}
                          >
                            +{dayLeaves.length - 2} more
                          </Typography>
                        )}
                      </Stack>
                    </Box>
                  );
                })}
              </Box>
            </Box>

            {/* Legend */}
            <Stack direction="row" spacing={2.5} sx={{ mt: 2, pt: 1.5, borderTop: '1px solid #EAE6DF' }} flexWrap="wrap">
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                <Box sx={{ width: 12, height: 12, borderRadius: 0.5, bgcolor: '#EBF5EE', border: '1px solid #D1E8D7' }} />
                <Typography variant="caption" sx={{ color: '#6B7280' }}>Approved Leave</Typography>
              </Box>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                <Box sx={{ width: 12, height: 12, borderRadius: 0.5, bgcolor: '#FDF8EB', border: '1px dashed #E5C88B' }} />
                <Typography variant="caption" sx={{ color: '#6B7280' }}>Pending / Partial Approval</Typography>
              </Box>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                <Box sx={{ width: 12, height: 12, borderRadius: '50%', bgcolor: '#111315' }} />
                <Typography variant="caption" sx={{ color: '#6B7280' }}>Today</Typography>
              </Box>
            </Stack>
          </Box>
        </Card>
      </Grid>

      {/* Selected Day Agenda & Team Coverage Panel */}
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
              variant="caption"
              sx={{ color: '#B8976C', fontWeight: 700, letterSpacing: '0.12em', display: 'block' }}
            >
              DAILY TEAM COVERAGE
            </Typography>
            <Typography
              variant="h6"
              sx={{ fontFamily: '"Playfair Display", Georgia, serif', fontWeight: 600, color: '#16181B' }}
            >
              {selectedDateDisplay}
            </Typography>
            <Typography variant="caption" sx={{ color: '#6B7280', display: 'block', mt: 0.5 }}>
              {selectedDayLeaves.length} away · {Math.max(0, teamSize - selectedDayLeaves.length)} of {teamSize} available
            </Typography>
          </Box>

          <CardContent sx={{ p: 2.5 }}>
            {selectedDayLeaves.length === 0 ? (
              <Box sx={{ textAlign: 'center', py: 5 }}>
                <EventBusyOutlinedIcon sx={{ color: '#D4CEBE', fontSize: 38, mb: 1 }} />
                <Typography variant="body2" sx={{ fontWeight: 600, color: '#16181B' }}>
                  Full Team Availability
                </Typography>
                <Typography variant="caption" sx={{ color: '#6B7280', display: 'block', mt: 0.5 }}>
                  No scheduled or pending leaves intersect this calendar date.
                </Typography>
              </Box>
            ) : (
              <Stack spacing={1.5}>
                {selectedDayLeaves.map((leave) => (
                  <Box
                    key={leave.id}
                    sx={{
                      p: 1.75,
                      borderRadius: 1.5,
                      backgroundColor: '#FAF9F6',
                      border: '1px solid #EAE6DF',
                    }}
                  >
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 0.75, gap: 1 }}>
                      <Box>
                        <Typography variant="body2" sx={{ fontWeight: 600, color: '#16181B' }}>
                          {leave.employee
                            ? `${leave.employee.firstName} ${leave.employee.lastName}`
                            : 'Team Member'}
                        </Typography>
                        <Typography variant="caption" sx={{ color: '#6B7280' }}>
                          {leave.employee?.employeeCode ? `${leave.employee.employeeCode} · ` : ''}
                          {leave.leaveType?.name || 'Leave'}
                        </Typography>
                      </Box>
                      <LeaveStatusChip status={leave.status} />
                    </Box>

                    <Typography variant="caption" sx={{ color: '#4B5563', display: 'block', mb: 1 }}>
                      {leave.startDate} to {leave.endDate} ({leave.requestedDays ?? leave.days ?? 1} working days)
                    </Typography>

                    {leave.reason && (
                      <Typography
                        variant="caption"
                        sx={{ color: '#6B7280', display: 'block', fontStyle: 'italic', mb: 1 }}
                      >
                        "{leave.reason}"
                      </Typography>
                    )}

                    {leave.employee && onInspectEmployee && (
                      <>
                        <Divider sx={{ my: 1, borderColor: '#EAE6DF' }} />
                        <Button
                          size="small"
                          startIcon={<PersonSearchOutlinedIcon fontSize="small" />}
                          onClick={() => onInspectEmployee(leave.employee!)}
                          sx={{
                            p: 0,
                            fontSize: '0.75rem',
                            color: '#8C6E45',
                            fontWeight: 600,
                            textTransform: 'none',
                            '&:hover': { backgroundColor: 'transparent', color: '#16181B' },
                          }}
                        >
                          View Employee Balances & History
                        </Button>
                      </>
                    )}
                  </Box>
                ))}
              </Stack>
            )}
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );
};

export default TeamLeaveCalendar;
