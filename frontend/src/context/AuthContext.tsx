import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import apiClient from '../api/client';
import { User, Employee, AuthResponse } from '../types';

interface AuthContextType {
  user: User | null;
  employee: Employee | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  refreshProfile: () => Promise<void>;
  hasRole: (role: string) => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(() => {
    const saved = localStorage.getItem('user_data');
    return saved ? JSON.parse(saved) : null;
  });
  const [employee, setEmployee] = useState<Employee | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const fetchProfile = async () => {
    try {
      const response = await apiClient.get<Employee>('/api/employees/me');
      setEmployee(response.data);
    } catch {
      // If employee profile doesn't exist yet (e.g. fresh admin user), keep employee null
      setEmployee(null);
    }
  };

  useEffect(() => {
    const token = localStorage.getItem('access_token');
    if (token) {
      fetchProfile().finally(() => setIsLoading(false));
    } else {
      setIsLoading(false);
    }
  }, []);

  const login = async (email: string, password: string) => {
    const response = await apiClient.post<AuthResponse>('/api/auth/login', {
      email,
      password,
    });

    const { accessToken, refreshToken, user: userData } = response.data;
    localStorage.setItem('access_token', accessToken);
    if (refreshToken) {
      localStorage.setItem('refresh_token', refreshToken);
    }
    localStorage.setItem('user_data', JSON.stringify(userData));

    setUser(userData);
    await fetchProfile();
  };

  const logout = () => {
    const token = localStorage.getItem('access_token');
    if (token) {
      apiClient.post('/api/auth/logout').catch(() => {
        // Ignore logout network errors; local purge proceeds
      });
    }
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    localStorage.removeItem('user_data');
    setUser(null);
    setEmployee(null);
  };

  const refreshProfile = async () => {
    await fetchProfile();
  };

  const hasRole = (roleName: string): boolean => {
    if (!user || !user.roles) return false;
    const normalized = roleName.startsWith('ROLE_') ? roleName : `ROLE_${roleName}`;
    return user.roles.some((r) => r.name === normalized || r.name === roleName);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        employee,
        isAuthenticated: !!user && !!localStorage.getItem('access_token'),
        isLoading,
        login,
        logout,
        refreshProfile,
        hasRole,
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
