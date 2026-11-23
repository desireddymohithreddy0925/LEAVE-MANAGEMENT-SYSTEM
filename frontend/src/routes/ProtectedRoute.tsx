import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import LoadingSpinner from '../components/common/LoadingSpinner';
import Unauthorized from '../pages/Unauthorized';
import { RoleType } from '../types';

interface ProtectedRouteProps {
  children: React.ReactElement;
  allowedRoles?: Array<RoleType | string>;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, allowedRoles }) => {
  const { isAuthenticated, isLoading, hasAnyRole } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return <LoadingSpinner message="Verifying authentication credentials and roles..." minHeight="80vh" />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // If specific roles are required, verify user has at least one of them
  if (allowedRoles && allowedRoles.length > 0 && !hasAnyRole(allowedRoles)) {
    return <Unauthorized allowedRoles={allowedRoles as string[]} />;
  }

  return children;
};

export default ProtectedRoute;
