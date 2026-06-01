/**
 * Persists the user's grid/list view preference to localStorage.
 * AC-016: persist selected view preference across sessions.
 */
import { useState } from 'react';

export type ViewPreference = 'grid' | 'list';

const STORAGE_KEY = 'tabvault_view_preference';

export function useViewPreference(): [ViewPreference, (view: ViewPreference) => void] {
  const [view, setViewState] = useState<ViewPreference>(() => {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === 'list' ? 'list' : 'grid';
  });

  function setView(newView: ViewPreference) {
    localStorage.setItem(STORAGE_KEY, newView);
    setViewState(newView);
  }

  return [view, setView];
}
