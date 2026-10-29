import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { lazy, Suspense } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'

import { FullPageLoader } from '@/components/full-page-loader'
import { ThemeProvider } from '@/components/theme-provider'
import { CheckEmailPage } from '@/features/auth/check-email-page'
import { ForgotPasswordPage } from '@/features/auth/forgot-password-page'
import { LoginPage } from '@/features/auth/login-page'
import { RegisterPage } from '@/features/auth/register-page'
import { ResetPasswordPage } from '@/features/auth/reset-password-page'
import { RedirectIfAuthenticated, RequireAuth } from '@/features/auth/route-guards'
import { VerifyEmailPage } from '@/features/auth/verify-email-page'
import { WorkspaceProvider } from '@/features/workspace/workspace-context'

// The signed-in part of the app is loaded on demand, so the sign-in page loads fast
const AppShell = lazy(() => import('@/components/app-shell').then((m) => ({ default: m.AppShell })))
const OrdersPage = lazy(() => import('@/features/orders/orders-page').then((m) => ({ default: m.OrdersPage })))
const OrderDetailPage = lazy(() =>
  import('@/features/orders/order-detail-page').then((m) => ({ default: m.OrderDetailPage })),
)
const ClientsPage = lazy(() => import('@/features/clients/clients-page').then((m) => ({ default: m.ClientsPage })))
const ClientDetailPage = lazy(() =>
  import('@/features/clients/client-detail-page').then((m) => ({ default: m.ClientDetailPage })),
)
const DashboardPage = lazy(() =>
  import('@/features/dashboard/dashboard-page').then((m) => ({ default: m.DashboardPage })),
)
const ReportsPage = lazy(() => import('@/features/reports/reports-page').then((m) => ({ default: m.ReportsPage })))
const SettingsPage = lazy(() =>
  import('@/features/settings/settings-page').then((m) => ({ default: m.SettingsPage })),
)
const ServicesPage = lazy(() => import('@/features/catalog/services-page').then((m) => ({ default: m.ServicesPage })))
const CalendarPage = lazy(() => import('@/features/calendar/calendar-page').then((m) => ({ default: m.CalendarPage })))
const InvoicePage = lazy(() => import('@/features/orders/invoice-page').then((m) => ({ default: m.InvoicePage })))
const PortalPage = lazy(() => import('@/features/portal/portal-page').then((m) => ({ default: m.PortalPage })))
const TeamPage = lazy(() => import('@/features/workspace/team-page').then((m) => ({ default: m.TeamPage })))

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false },
  },
})

export function AppRoutes() {
  return (
    <Suspense fallback={<FullPageLoader />}>
      <Routes>
        <Route path="/login" element={<RedirectIfAuthenticated><LoginPage /></RedirectIfAuthenticated>} />
        <Route path="/register" element={<RedirectIfAuthenticated><RegisterPage /></RedirectIfAuthenticated>} />
        <Route path="/forgot-password" element={<RedirectIfAuthenticated><ForgotPasswordPage /></RedirectIfAuthenticated>} />
        <Route path="/check-email" element={<CheckEmailPage />} />
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />

        <Route
          element={
            <RequireAuth>
              <WorkspaceProvider>
                <AppShell />
              </WorkspaceProvider>
            </RequireAuth>
          }
        >
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/reports" element={<ReportsPage />} />
          <Route path="/orders" element={<OrdersPage />} />
          <Route path="/orders/:orderId" element={<OrderDetailPage />} />
          <Route path="/clients" element={<ClientsPage />} />
          <Route path="/clients/:clientId" element={<ClientDetailPage />} />
          <Route path="/team" element={<TeamPage />} />
          <Route path="/services" element={<ServicesPage />} />
          <Route path="/calendar" element={<CalendarPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>

        {/* Printable invoice: signed in, but without the app layout */}
        <Route
          path="/orders/:orderId/invoice"
          element={
            <RequireAuth>
              <WorkspaceProvider>
                <InvoicePage />
              </WorkspaceProvider>
            </RequireAuth>
          }
        />
        {/* Public customer page (no account) */}
        <Route path="/p/:token" element={<PortalPage />} />

        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  )
}

export default function App() {
  return (
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </QueryClientProvider>
    </ThemeProvider>
  )
}
