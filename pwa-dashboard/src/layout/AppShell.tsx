import { Outlet, NavLink } from 'react-router-dom';
import { useAuthStore } from '../auth/auth-store';
import { useNavigate } from 'react-router-dom';

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', icon: 'dashboard', end: true },
  { to: '/categories', label: 'Categories', icon: 'folder', end: false },
  { to: '/reminders', label: 'Reminders', icon: 'notifications', end: false },
  { to: '/settings', label: 'Settings', icon: 'settings', end: false },
];

export default function AppShell() {
  const logout = useAuthStore((state) => state.logout);
  const displayName = useAuthStore((state) => state.displayName);
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/login');
  }

  return (
    <div className="min-h-screen flex flex-col">
      {/* Top navigation bar */}
      <header className="bg-[#f2f1f0] border-b border-[#D9C196]/30 sticky top-0 z-10">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-14 flex items-center justify-between">
          <div className="flex items-center gap-6">
            <span className="text-primary-dark font-bold text-lg">TabVault</span>
            <nav className="hidden sm:flex items-center gap-1">
              {NAV_ITEMS.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    `px-3 py-1.5 rounded-md text-sm font-medium transition-colors flex items-center gap-1.5 ${
                      isActive
                        ? 'bg-primary/20 text-dark'
                        : 'text-gray-600 hover:bg-gray-100'
                    }`
                  }
                >
                  <span className="material-symbols-outlined">{item.icon}</span>
                  {item.label}
                </NavLink>
              ))}
            </nav>
          </div>

          <div className="flex items-center gap-3">
            {displayName && (
              <span className="text-sm text-gray-500 hidden sm:block">{displayName}</span>
            )}
            <button
              onClick={handleLogout}
              className="text-sm text-gray-600 hover:text-highlight transition-colors"
            >
              Sign out
            </button>
          </div>
        </div>
      </header>

      {/* Main content */}
      <main className="flex-1 max-w-7xl mx-auto w-full px-4 sm:px-6 lg:px-8 py-6">
        <Outlet />
      </main>

      {/* Mobile bottom nav */}
      <nav className="sm:hidden bg-[#f2f1f0] border-t border-[#D9C196]/30 fixed bottom-0 left-0 right-0 flex">
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) =>
              `flex-1 flex flex-col items-center py-2 text-xs font-medium transition-colors ${
                isActive ? 'text-dark' : 'text-dark/50'
              }`
            }
          >
            <span className="material-symbols-outlined text-xl leading-tight">{item.icon}</span>
            {item.label}
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
