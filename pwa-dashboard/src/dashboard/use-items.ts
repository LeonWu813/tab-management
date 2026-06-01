/**
 * React Query hooks for items and categories.
 */
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiRequest } from '../api/api-client';
import type { ItemResponse, Page, CategoryResponse } from '../api/types';

export interface ItemFilters {
  query?: string;
  categoryId?: number | null;
  itemType?: string | null;
  page?: number;
  pageSize?: number;
}

export function useItems(filters: ItemFilters = {}) {
  const params = new URLSearchParams();
  if (filters.query) params.set('query', filters.query);
  if (filters.page !== undefined) params.set('page', String(filters.page));
  if (filters.pageSize !== undefined) params.set('pageSize', String(filters.pageSize));

  const queryString = params.toString();
  const path = `/api/items${queryString ? `?${queryString}` : ''}`;

  return useQuery<Page<ItemResponse>>({
    queryKey: ['items', filters],
    queryFn: () => apiRequest<Page<ItemResponse>>(path),
  });
}

export function useCategories() {
  return useQuery<CategoryResponse[]>({
    queryKey: ['categories'],
    queryFn: () => apiRequest<CategoryResponse[]>('/api/categories'),
  });
}

export function useUpdateItemCategory() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ itemId, categoryId }: { itemId: number; categoryId: number | null }) =>
      apiRequest<ItemResponse>(`/api/items/${itemId}/category`, {
        method: 'PATCH',
        body: JSON.stringify({ targetCategoryId: categoryId }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['items'] });
    },
  });
}

export function useUpdateItemTitle() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ itemId, title, summary }: { itemId: number; title?: string; summary?: string }) =>
      apiRequest<ItemResponse>(`/api/items/${itemId}`, {
        method: 'PATCH',
        body: JSON.stringify({ title, summary }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['items'] });
    },
  });
}

export function useRecordVisit() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (itemId: number) =>
      apiRequest<void>(`/api/items/${itemId}/visit`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['items'] });
    },
  });
}

export function useCreateNote() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (noteBody: string) =>
      apiRequest<ItemResponse>('/api/items/notes', {
        method: 'POST',
        body: JSON.stringify({ noteBody }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['items'] });
    },
  });
}
