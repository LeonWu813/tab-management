/**
 * Zustand store for authentication state.
 *
 * accessToken is stored in localStorage (key: tabvault_access_token) so it
 * persists across page reloads. The store reads localStorage on initialization.
 */
import { create } from 'zustand';
import { clearTokens, storeTokens } from '../api/api-client';

interface AuthState {
  accessToken: string | null;
  displayName: string | null;
  login: (accessToken: string, refreshToken: string, displayName?: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: localStorage.getItem('tabvault_access_token'),
  displayName: localStorage.getItem('tabvault_display_name'),

  login: (accessToken, refreshToken, displayName) => {
    storeTokens(accessToken, refreshToken);
    if (displayName !== undefined) {
      localStorage.setItem('tabvault_display_name', displayName);
    }
    set({ accessToken, displayName: displayName ?? null });
  },

  logout: () => {
    clearTokens();
    localStorage.removeItem('tabvault_display_name');
    set({ accessToken: null, displayName: null });
  },
}));
