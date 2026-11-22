# Leave Management System - Frontend

A responsive, clean, and intuitive React + TypeScript frontend for the Leave Management System, styled using an approachable tertiary color palette (off-whites, soft slates, warm card surfaces) and built with Material UI (MUI v5) and Vite.

---

## 🎨 Design Philosophy: Tertiary Minimalist Theme

- **Background Canvas**: `#F8F9FA` (soft off-white canvas, calming and non-intrusive)
- **Card Surfaces**: `#FFFFFF` with `#E4E7EB` subtle hairline borders
- **Navigation & Drawer**: `#F5F5F3` warm alabaster
- **Typography & Primary Accents**: `#39424E` charcoal slate (high legibility, zero harsh primary glare)
- **Status Indicators**:
  - **Approved**: `#EBF3ED` sage background with `#2E5A36` text
  - **Pending**: `#FBF6EA` warm sand background with `#7A5C1E` text
  - **Rejected**: `#F9EEEE` dusty rose background with `#7D3030` text

---

## 🚀 Features & Architecture

### 1. Foundation & Layout
- **Vite + React 18 + TypeScript**: Lightning-fast HMR and type-safe interfaces.
- **Axios API Client (`src/api/client.ts`)**: Automatic JWT `Bearer` token injection, seamless `401 Unauthorized` token refresh flow, and base URL resolution via `VITE_API_BASE_URL`.
- **Global Error Boundary (`src/components/common/ErrorBoundary.tsx`)**: Graceful UI error fallback with reset capability.
- **Notification Provider (`src/context/NotificationContext.tsx`)**: Toast snackbar alerts for user feedback.
- **Auth Provider (`src/context/AuthContext.tsx`)**: Global auth state tracking token, user role permissions, and active employee profile.
- **Layout Shell (`src/components/layout/`)**:
  - Responsive header with user profile menu and mobile drawer toggle.
  - Collapsible sidebar with active link styling and simple iconography.

### 2. Employee Module
- **Dashboard (`/dashboard`)**:
  - Live summary of leave balance (Available, Used, Pending).
  - Leave quota cards breakdown by leave type (Annual, Sick, Casual, etc.).
  - Recent leave requests overview with status tags.
- **Apply for Leave (`/apply-leave`)**:
  - Dynamic leave type selection.
  - Start/End date pickers with automatic weekend-excluding working day calculation.
  - Real-time leave overlap validation against existing applications.
- **My Leave History (`/my-leaves`)**:
  - Filterable tabs (`All`, `Pending`, `Approved`, `Rejected`).
  - Actionable cancel request dialog for pending leaves (`DELETE /api/leave-requests/{id}`).
- **Profile & Security (`/profile`)**:
  - Personal details and employment hierarchy view.
  - Edit personal details modal (`PUT /api/employees/me/profile`).
  - Secure change password form (`PUT /api/employees/me/password`).

---

## 🛠️ Quick Start

### Prerequisites
- Node.js $\ge$ 18
- Running Spring Boot Backend (`http://localhost:8080`)

### Installation & Execution
```bash
# Navigate to frontend directory
cd frontend

# Install dependencies (already installed)
npm install

# Start Vite development server
npm run dev
```

The frontend will run at: **`http://localhost:5173`**

### Production Build
```bash
npm run build
npm run preview
```
