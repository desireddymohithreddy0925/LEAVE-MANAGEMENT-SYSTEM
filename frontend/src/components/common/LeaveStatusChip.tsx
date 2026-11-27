import React from 'react';
import { Chip, ChipProps } from '@mui/material';

interface LeaveStatusChipProps {
  status: string;
  size?: ChipProps['size'];
}

interface StatusStyle {
  label: string;
  bg: string;
  color: string;
  border: string;
}

const STATUS_STYLE_MAP: Record<string, StatusStyle> = {
  APPROVED: {
    label: 'Approved',
    bg: '#EBF5EE',
    color: '#235A36',
    border: '#D1E8D7',
  },
  PENDING: {
    label: 'Pending',
    bg: '#FDF8EB',
    color: '#8C6B1C',
    border: '#F4E3B8',
  },
  PARTIALLY_APPROVED: {
    label: 'Partial Approval',
    bg: '#FAF3E6',
    color: '#8C6E45',
    border: '#E5D3B3',
  },
  REJECTED: {
    label: 'Rejected',
    bg: '#FAF0F0',
    color: '#8C2D2D',
    border: '#ECCACA',
  },
  CANCELLED: {
    label: 'Cancelled',
    bg: '#F3F4F6',
    color: '#4B5563',
    border: '#E5E7EB',
  },
};

/**
 * Consistent executive badge representing leave approval lifecycle states.
 */
export const LeaveStatusChip: React.FC<LeaveStatusChipProps> = ({ status, size = 'small' }) => {
  const style = STATUS_STYLE_MAP[status];
  if (!style) {
    return <Chip size={size} label={status} />;
  }

  return (
    <Chip
      size={size}
      label={style.label}
      sx={{
        backgroundColor: style.bg,
        color: style.color,
        border: `1px solid ${style.border}`,
        fontWeight: 600,
        fontSize: '0.725rem',
      }}
    />
  );
};

export default LeaveStatusChip;
