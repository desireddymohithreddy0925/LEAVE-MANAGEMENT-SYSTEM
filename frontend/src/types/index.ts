export type RoleType = 'ADMIN' | 'HR' | 'MANAGER' | 'EMPLOYEE';

export interface Role {
  id: number;
  name: string;
}

export interface User {
  id: number;
  email: string;
  active: boolean;
  roles: Role[];
  accountNonLocked?: boolean;
  lastLoginAt?: string;
  lastLoginIp?: string;
  loginCount?: number;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: User;
}

export interface TokenRefreshRequest {
  refreshToken: string;
}

export interface LoginCredentials {
  email: string;
  password: string;
}

export interface Department {
  id: number;
  name: string;
  managerId?: number;
  managerName?: string;
  active?: boolean;
}

export interface Employee {
  id: number;
  firstName: string;
  lastName: string;
  employeeCode: string;
  email: string;
  phone?: string;
  status: string;
  salary?: number;
  department?: Department;
  managerId?: number;
  managerName?: string;
}

export interface EmployeeProfileUpdate {
  firstName: string;
  lastName: string;
  phone?: string;
}

export interface PasswordChangeRequest {
  currentPassword: string;
  newPassword: string;
}

export interface LeaveType {
  id: number;
  name: string;
  description?: string;
  defaultDays: number;
  active: boolean;
}

export interface LeaveBalance {
  id: number;
  leaveType: LeaveType;
  year: number;
  totalDays: number;
  usedDays: number;
  availableDays: number;
}

export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export interface LeaveRequest {
  id: number;
  employee?: Employee;
  leaveType: LeaveType;
  startDate: string;
  endDate: string;
  days: number;
  reason: string;
  status: LeaveStatus;
  rejectionReason?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface CreateLeaveRequestDTO {
  leaveTypeId: number;
  startDate: string;
  endDate: string;
  reason: string;
}

export interface OverlapCheckResponse {
  overlapping: boolean;
  message?: string;
}

export interface EmployeeHierarchy {
  employee: Employee;
  reportingChain: Employee[];
  directReports: Employee[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
