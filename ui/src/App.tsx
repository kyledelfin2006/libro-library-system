import { Navigate, Route, Routes } from 'react-router-dom';
import { PublicOnly, RequireAuth } from './auth/RouteGuards';
import { Shell } from './components/Shell';
import { Account } from './pages/Account';
import { Accounts } from './pages/Accounts';
import { Audit } from './pages/Audit';
import { BookDetail } from './pages/BookDetail';
import { Catalog } from './pages/Catalog';
import { ChangePassword } from './pages/ChangePassword';
import { MyLoans } from './pages/MyLoans';
import { Dashboard } from './pages/Dashboard';
import { Inventory } from './pages/Inventory';
import { Loans } from './pages/Loans';
import { Register } from './pages/Register';
import { Releases } from './pages/Releases';
import { SignIn } from './pages/SignIn';
import { Status } from './pages/Status';
import { Version } from './pages/Version';

/** Route table. Capability gates mirror permissions.ts; the server remains the security boundary. */
export function App() {
  return (
    <Routes>
      {/* The status page is public: it is what people check when sign-in itself is failing. */}
      <Route path="/status" element={<Status />} />
      <Route element={<PublicOnly />}>
        <Route path="/signin" element={<SignIn />} />
        <Route path="/register" element={<Register />} />
      </Route>
      <Route element={<RequireAuth />}>
        <Route path="/change-password" element={<ChangePassword />} />
        <Route element={<Shell />}>
          <Route path="/catalog" element={<Catalog />} />
          <Route path="/catalog/:id" element={<BookDetail />} />
          <Route path="/loans" element={<MyLoans />} />
          <Route path="/account" element={<Account />} />
          <Route element={<RequireAuth capability="staffDashboard" />}>
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/staff/loans" element={<Loans />} />
            <Route path="/inventory" element={<Inventory />} />
          </Route>
          <Route element={<RequireAuth capability="listAccounts" />}>
            <Route path="/accounts" element={<Accounts />} />
          </Route>
          <Route element={<RequireAuth capability="audit" />}>
            <Route path="/audit" element={<Audit />} />
          </Route>
          <Route element={<RequireAuth capability="buildInfo" />}>
            <Route path="/version" element={<Version />} />
            <Route path="/releases" element={<Releases />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/signin" replace />} />
    </Routes>
  );
}
