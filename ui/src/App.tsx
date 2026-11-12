import { Navigate, Route, Routes } from 'react-router-dom';
import { PublicOnly, RequireAuth } from './auth/RouteGuards';
import { Shell } from './components/Shell';
import { Account } from './pages/Account';
import { BookDetail } from './pages/BookDetail';
import { Catalog } from './pages/Catalog';
import { ChangePassword } from './pages/ChangePassword';
import { MyLoans } from './pages/MyLoans';
import { Placeholder } from './pages/Placeholder';
import { Register } from './pages/Register';
import { SignIn } from './pages/SignIn';

/** Route table. Capability gates mirror permissions.ts; the server remains the security boundary. */
export function App() {
  return (
    <Routes>
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
            <Route path="/dashboard" element={<Placeholder title="Dashboard" step={5} />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/signin" replace />} />
    </Routes>
  );
}
