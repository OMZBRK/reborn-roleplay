import { Route, Routes, Navigate } from "react-router";
import { Login } from "./routes/Login";
import { Home } from "./routes/Home";
import { AuthenticatedLayout } from "./components/AuthenticatedLayout";
import { useAuthStore } from "./stores/auth-store";

export function App() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return (
    <Routes>
      <Route path="/login" element={<Login />} />

      <Route
        element={isAuthenticated ? <AuthenticatedLayout /> : <Navigate to="/login" replace />}
      >
        <Route path="/home" element={<Home />} />
        <Route path="/shop" element={<PlaceholderPage title="Boutique" />} />
        <Route path="/whitelist" element={<PlaceholderPage title="Whitelist" />} />
        <Route path="/rules" element={<PlaceholderPage title="Reglement" />} />
        <Route path="/lore" element={<PlaceholderPage title="Lore" />} />
        <Route path="/patchnotes" element={<PlaceholderPage title="Patch Notes" />} />
        <Route path="/tickets" element={<PlaceholderPage title="Tickets" />} />
        <Route path="/docs" element={<PlaceholderPage title="Documentation" />} />
      </Route>

      <Route path="*" element={<Navigate to={isAuthenticated ? "/home" : "/login"} replace />} />
    </Routes>
  );
}

function PlaceholderPage({ title }: { title: string }) {
  return (
    <div className="flex h-full items-center justify-center">
      <div className="text-center">
        <h1 className="text-3xl font-semibold">{title}</h1>
        <p className="mt-2 text-foreground-subtle">Cette page sera implementee prochainement.</p>
      </div>
    </div>
  );
}
