import React, { createContext, useContext, useState, useEffect, useMemo, ReactNode } from 'react';
import apiClient, {
  setAuthSession,
  clearAuthSession,
  getStoredAccessToken,
  getStoredRefreshToken,
} from '../api/client';
import { User, Employee, AuthResponse, RoleType } from '../types';

export interface AuthContextType {
  user: User | null;
  employee: Employee | null;
  roles: RoleType[];
  isAuthenticated: boolean;
  isLoading: boolean;
  isAdmin: boolean;
  isHR: boolean;
  isManager: boolean;
  isEmployee: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshProfile: () => Promise<void>;
  hasRole: (role: RoleType | string) => boolean;
  hasAnyRole: (roles: (RoleType | string)[]) => boolean;
  hasAllRoles: (roles: (RoleType | string)[]) => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(() => {
    const saved = localStorage.getItem('user_data');
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch {
        return null;
      }
    }
    return null;
  });
  const [employee, setEmployee] = useState<Employee | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  // Normalize user roles into uppercase standard names without 'ROLE_' prefix
  const roles = useMemo<RoleType[]>(() => {
    if (!user || !user.roles) return [];
    return user.roles.map((r) => r.name.replace(/^ROLE_/, '').toUpperCase() as RoleType);
  }, [user]);

  const hasRole = (roleName: RoleType | string): boolean => {
    if (!user || !user.roles) return false;
    const target = roleName.replace(/^ROLE_/, '').toUpperCase();
    return roles.includes(target as RoleType);
  };

  const hasAnyRole = (targetRoles: (RoleType | string)[]): boolean => {
    if (!user || !user.roles || targetRoles.length === 0) return false;
    return targetRoles.some((r) => hasRole(r));
  };

  const hasAllRoles = (targetRoles: (RoleType | string)[]): boolean => {
    if (!user || !user.roles || targetRoles.length === 0) return false;
    return targetRoles.every((r) => hasRole(r));
  };

  const isAdmin = useMemo(() => hasRole('ADMIN'), [roles]);
  const isHR = useMemo(() => hasRole('HR'), [roles]);
  const isManager = useMemo(() => hasRole('MANAGER'), [roles]);
  const isEmployee = useMemo(() => hasRole('EMPLOYEE'), [roles]);

  const fetchProfile = async () => {
    try {
      const response = await apiClient.get<Employee>('/api/employees/me');
      setEmployee(response.data);
    } catch {
      // If profile does not exist yet for this account (e.g. system admin), leave as null
      setEmployee(null);
    }
  };

  useEffect(() => {
    const token = getStoredAccessToken();
    if (token) {
      fetchProfile().finally(() => setIsLoading(false));
    } else {
      setIsLoading(false);
    }
  }, []);

  const login = async (email: string, password: string): Promise<void> => {
    const response = await apiClient.post<AuthResponse>('/api/auth/login', {
      email: email.trim(),
      password,
    });

    const { accessToken, refreshToken, user: userData } = response.data;
    setAuthSession(accessToken, refreshToken, userData);
    setUser(userData);

    await fetchProfile();
  };

  const logout = async (): Promise<void> => {
    const refreshToken = getStoredRefreshToken();
    try {
      if (refreshToken) {
        // Backend expects { refreshToken: string } to invalidate the active token
        await apiClient.post('/api/auth/logout', { refreshToken });
      }
    } catch {
      // Suppress network errors during logout; local credential purge is absolute
    } finally {
      clearAuthSession();
      setUser(null);
      setEmployee(null);
    }
  };

  const refreshProfile = async (): Promise<void> => {
    await fetchProfile();
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        employee,
        roles,
        isAuthenticated: !!user && !!getStoredAccessToken(),
        isLoading,
        isAdmin,
        isHR,
        isManager,
        isEmployee,
        login,
        logout,
        refreshProfile,
        hasRole,
        hasAnyRole,
        hasAllRoles,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
