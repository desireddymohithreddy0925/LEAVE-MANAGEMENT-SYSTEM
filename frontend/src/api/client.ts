import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { AuthResponse } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15000,
});

// Storage keys
export const ACCESS_TOKEN_KEY = 'access_token';
export const REFRESH_TOKEN_KEY = 'refresh_token';
export const USER_DATA_KEY = 'user_data';

// Helper storage utilities
export const getStoredAccessToken = (): string | null => localStorage.getItem(ACCESS_TOKEN_KEY);
export const getStoredRefreshToken = (): string | null => localStorage.getItem(REFRESH_TOKEN_KEY);

export const setAuthSession = (accessToken: string, refreshToken?: string, userData?: unknown): void => {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  if (refreshToken) {
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  }
  if (userData) {
    localStorage.setItem(USER_DATA_KEY, typeof userData === 'string' ? userData : JSON.stringify(userData));
  }
};

export const clearAuthSession = (): void => {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USER_DATA_KEY);
};

// Request Interceptor: Attach JWT Access Token
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getStoredAccessToken();
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error: AxiosError) => Promise.reject(error)
);

// Concurrency handling for token refresh
interface PendingRequest {
  resolve: (value?: unknown) => void;
  reject: (reason?: unknown) => void;
}

let isRefreshing = false;
let failedQueue: PendingRequest[] = [];

const processQueue = (error: unknown, token: string | null = null): void => {
  failedQueue.forEach((promise) => {
    if (error) {
      promise.reject(error);
    } else {
      promise.resolve(token);
    }
  });
  failedQueue = [];
};

// Response Interceptor: Handle 401 unauthorized & automatic token refresh queue
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };
    const status = error.response?.status;
    const requestUrl = originalRequest?.url || '';

    // Ignore public auth routes to prevent refresh loops & display proper validation/login errors
    const isAuthRoute =
      requestUrl.includes('/api/auth/login') ||
      requestUrl.includes('/api/auth/register') ||
      requestUrl.includes('/api/auth/refresh');

    if (status === 401 && !isAuthRoute && originalRequest) {
      if (originalRequest._retry) {
        // If this retry already failed, purge credentials and redirect to login
        clearAuthSession();
        if (window.location.pathname !== '/login') {
          window.location.href = '/login?expired=true';
        }
        return Promise.reject(error);
      }

      const refreshToken = getStoredRefreshToken();

      if (!refreshToken) {
        clearAuthSession();
        if (window.location.pathname !== '/login') {
          window.location.href = '/login?expired=true';
        }
        return Promise.reject(error);
      }

      if (isRefreshing) {
        // Queue this request while token refresh is in flight
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            if (originalRequest.headers && token) {
              originalRequest.headers.Authorization = `Bearer ${token}`;
            }
            return apiClient(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        // Spring Boot backend expects JSON body: { refreshToken: string }
        const response = await axios.post<AuthResponse>(
          `${API_BASE_URL}/api/auth/refresh`,
          { refreshToken },
          {
            headers: {
              'Content-Type': 'application/json',
            },
          }
        );

        const newAccessToken = response.data?.accessToken;
        const newRefreshToken = response.data?.refreshToken;

        if (newAccessToken) {
          setAuthSession(newAccessToken, newRefreshToken, response.data?.user);

          if (originalRequest.headers) {
            originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
          }

          processQueue(null, newAccessToken);
          return apiClient(originalRequest);
        } else {
          throw new Error('Refresh response did not contain an access token');
        }
      } catch (refreshErr) {
        processQueue(refreshErr, null);
        clearAuthSession();
        if (window.location.pathname !== '/login') {
          window.location.href = '/login?expired=true';
        }
        return Promise.reject(refreshErr);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

export default apiClient;
