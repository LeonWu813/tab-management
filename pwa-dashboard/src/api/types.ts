/**
 * TypeScript interfaces matching the TabVault backend API response shapes.
 */

export interface ItemResponse {
  id: number;
  itemType: 'LINK' | 'NOTE' | 'VIDEO';
  url: string | null;
  title: string | null;
  faviconUrl: string | null;
  summary: string | null;
  suggestedCategory: string | null;
  contentType: string | null;
  noteBody: string | null;
  thumbnailUrl: string | null;
  platform: string | null;
  categoryId: number | null;
  pinned: boolean;
  archived: boolean;
  lastVisitedAt: string | null;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // current page (0-based)
  size: number;
  first: boolean;
  last: boolean;
}

export interface CategoryResponse {
  id: number;
  name: string;
  color: string;
  icon: string | null;
  sortOrder: number;
  createdAt: string;
}

export interface ReminderResponse {
  id: number;
  itemId: number;
  userId: number;
  dueDate: string;
  label: string;
  status: 'CONFIRMED' | 'PENDING_CONFIRMATION' | 'DISMISSED' | 'PENDING';
  dueWithin24Hours: boolean;
  createdAt: string;
}

export interface CleanupSettingsResponse {
  userId: number;
  stalenessThresholdDays: number;
  autoCleanupEnabled: boolean;
}

export interface VapidPublicKeyResponse {
  publicKey: string;
}

export interface PushSubscriptionResponse {
  id: number;
  userId: number;
  endpoint: string;
  createdAt: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
}

export interface RegisterResponse {
  displayName: string;
  accessToken: string;
}
