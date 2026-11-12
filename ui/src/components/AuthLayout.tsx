import type { ReactNode } from 'react';

/** Split brand/form layout shared by sign in, register, and forced password change (plan section 6.2). */
export function AuthLayout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="auth-layout">
      <aside className="auth-brand">
        <img src="/brand/libro-256.png" alt="Libro" width={160} height={151} />
        <p>A quiet place to borrow, return and keep track of your books.</p>
      </aside>
      <main className="auth-panel">
        <h1>{title}</h1>
        {children}
      </main>
    </div>
  );
}
