/**
 * Categories management page.
 *
 * Allows users to create, view, and delete categories.
 * Deleting a category reassigns all its items to Uncategorized (handled by backend — AC-019).
 */
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { z } from 'zod';
import { apiRequest, ApiError } from '../api/api-client';
import type { CategoryResponse } from '../api/types';

const createCategorySchema = z.object({
  name: z
    .string()
    .min(1, 'Name is required')
    .max(50, 'Name must be 50 characters or fewer'),
  color: z
    .string()
    .regex(/^#[0-9a-fA-F]{6}$/, 'Enter a valid hex color (e.g. #3b82f6)'),
  icon: z.string().optional(),
});

type CreateCategoryFormValues = z.infer<typeof createCategorySchema>;

export default function CategoriesPage() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [formValues, setFormValues] = useState<CreateCategoryFormValues>({
    name: '',
    color: '#D9C196',
    icon: '',
  });
  const [formErrors, setFormErrors] = useState<
    Partial<Record<keyof CreateCategoryFormValues, string>>
  >({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  const { data: categories = [], isLoading } = useQuery<CategoryResponse[]>({
    queryKey: ['categories'],
    queryFn: () => apiRequest<CategoryResponse[]>('/api/categories'),
  });

  const createCategory = useMutation({
    mutationFn: (values: CreateCategoryFormValues) =>
      apiRequest<CategoryResponse>('/api/categories', {
        method: 'POST',
        body: JSON.stringify({
          name: values.name,
          color: values.color,
          icon: values.icon || null,
        }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      setShowForm(false);
      setFormValues({ name: '', color: '#D9C196', icon: '' });
      setFormErrors({});
      setServerError(null);
    },
    onError: (error) => {
      if (error instanceof ApiError && error.field) {
        setFormErrors({ [error.field as keyof CreateCategoryFormValues]: error.message });
      } else if (error instanceof ApiError) {
        setServerError(error.message);
      } else {
        setServerError('Failed to create category.');
      }
    },
  });

  const deleteCategory = useMutation({
    mutationFn: (categoryId: number) =>
      apiRequest<void>(`/api/categories/${categoryId}`, { method: 'DELETE' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      queryClient.invalidateQueries({ queryKey: ['items'] });
      setDeletingId(null);
    },
  });

  function handleFormChange(field: keyof CreateCategoryFormValues, value: string) {
    setFormValues((prev) => ({ ...prev, [field]: value }));
    setFormErrors((prev) => ({ ...prev, [field]: undefined }));
    setServerError(null);
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const parseResult = createCategorySchema.safeParse(formValues);
    if (!parseResult.success) {
      const errors: Partial<Record<keyof CreateCategoryFormValues, string>> = {};
      for (const issue of parseResult.error.issues) {
        errors[issue.path[0] as keyof CreateCategoryFormValues] = issue.message;
      }
      setFormErrors(errors);
      return;
    }
    createCategory.mutate(formValues);
  }

  function confirmDelete(categoryId: number) {
    if (deletingId === categoryId) {
      deleteCategory.mutate(categoryId);
    } else {
      setDeletingId(categoryId);
    }
  }

  return (
    <div className="pb-20 sm:pb-6 space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-gray-900">Categories</h1>
        <button
          onClick={() => setShowForm((s) => !s)}
          className="bg-primary hover:bg-primary-dark text-dark text-sm font-medium rounded-lg px-4 py-2 transition-colors"
        >
          {showForm ? 'Cancel' : '+ New category'}
        </button>
      </div>

      {/* Create category form */}
      {showForm && (
        <form
          onSubmit={handleSubmit}
          className="bg-white border border-gray-200 rounded-xl p-5 space-y-4"
        >
          <h2 className="text-sm font-semibold text-gray-800">New category</h2>

          {serverError && (
            <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg px-3 py-2 text-sm">
              {serverError}
            </div>
          )}

          <div className="flex flex-col sm:flex-row gap-4">
            <div className="flex-1">
              <label htmlFor="cat-name" className="block text-xs font-medium text-gray-700 mb-1">
                Name <span className="text-red-500">*</span>
              </label>
              <input
                id="cat-name"
                type="text"
                value={formValues.name}
                onChange={(e) => handleFormChange('name', e.target.value)}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                placeholder="Category name"
                maxLength={50}
              />
              {formErrors.name && (
                <p className="text-red-600 text-xs mt-1">{formErrors.name}</p>
              )}
            </div>

            <div>
              <label htmlFor="cat-color" className="block text-xs font-medium text-gray-700 mb-1">
                Color <span className="text-red-500">*</span>
              </label>
              <div className="flex items-center gap-2">
                <input
                  id="cat-color"
                  type="color"
                  value={formValues.color}
                  onChange={(e) => handleFormChange('color', e.target.value)}
                  className="h-9 w-14 rounded border border-gray-300 cursor-pointer"
                />
                <input
                  type="text"
                  value={formValues.color}
                  onChange={(e) => handleFormChange('color', e.target.value)}
                  className="w-24 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary font-mono"
                  placeholder="#D9C196"
                  maxLength={7}
                />
              </div>
              {formErrors.color && (
                <p className="text-red-600 text-xs mt-1">{formErrors.color}</p>
              )}
            </div>

            <div className="flex-1">
              <label htmlFor="cat-icon" className="block text-xs font-medium text-gray-700 mb-1">
                Icon <span className="text-gray-400">(optional)</span>
              </label>
              <input
                id="cat-icon"
                type="text"
                value={formValues.icon}
                onChange={(e) => handleFormChange('icon', e.target.value)}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                placeholder="e.g. briefcase"
              />
            </div>
          </div>

          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={() => {
                setShowForm(false);
                setFormErrors({});
                setServerError(null);
              }}
              className="px-4 py-2 text-sm text-gray-600 hover:text-gray-900"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={createCategory.isPending}
              className="bg-primary hover:bg-primary-dark disabled:bg-primary/60 text-dark font-medium rounded-lg px-4 py-2 text-sm transition-colors"
            >
              {createCategory.isPending ? 'Creating...' : 'Create category'}
            </button>
          </div>
        </form>
      )}

      {/* Category list */}
      {isLoading && (
        <div className="text-center py-8 text-gray-400">Loading categories...</div>
      )}

      {!isLoading && categories.length === 0 && (
        <div className="text-center py-12 text-gray-400">
          No categories yet. Create one to organize your saved items.
        </div>
      )}

      {!isLoading && categories.length > 0 && (
        <div className="space-y-2">
          {categories.map((cat) => (
            <div
              key={cat.id}
              className="bg-white border border-gray-200 rounded-lg px-4 py-3 flex items-center justify-between"
            >
              <div className="flex items-center gap-3">
                <div
                  className="w-4 h-4 rounded-full flex-shrink-0"
                  style={{ backgroundColor: cat.color }}
                  aria-hidden="true"
                />
                <div>
                  <span className="text-sm font-medium text-gray-900">{cat.name}</span>
                  {cat.icon && (
                    <span className="ml-2 text-xs text-gray-400">{cat.icon}</span>
                  )}
                </div>
              </div>

              <button
                onClick={() => confirmDelete(cat.id)}
                className={`text-xs font-medium rounded px-2.5 py-1 transition-colors ${
                  deletingId === cat.id
                    ? 'bg-red-100 text-red-700 hover:bg-red-200'
                    : 'text-gray-400 hover:text-red-600 hover:bg-red-50'
                }`}
                aria-label={
                  deletingId === cat.id
                    ? `Confirm delete ${cat.name}`
                    : `Delete ${cat.name}`
                }
              >
                {deletingId === cat.id ? 'Confirm delete' : 'Delete'}
              </button>
            </div>
          ))}
        </div>
      )}

      {/* Cancel pending delete on outside click */}
      {deletingId !== null && (
        <button
          className="fixed inset-0 z-10 cursor-default"
          onClick={() => setDeletingId(null)}
          aria-hidden="true"
        />
      )}
    </div>
  );
}
