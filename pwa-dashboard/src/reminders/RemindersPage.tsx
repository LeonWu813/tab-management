/**
 * Reminders management page.
 *
 * Displays all active reminders with a due-soon badge (AC-024).
 * Allows creating manual reminders (AC-021).
 * Allows updating, snoozing, or dismissing reminders (AC-023).
 */
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { z } from 'zod';
import { apiRequest, ApiError } from '../api/api-client';
import type { ReminderResponse } from '../api/types';

const createReminderSchema = z.object({
  itemId: z.string().min(1, 'Item ID is required'),
  dueDate: z.string().min(1, 'Due date is required'),
  label: z.string().optional(),
});

type CreateReminderForm = z.infer<typeof createReminderSchema>;

export default function RemindersPage() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [formValues, setFormValues] = useState<CreateReminderForm>({
    itemId: '',
    dueDate: '',
    label: '',
  });
  const [formErrors, setFormErrors] = useState<Partial<Record<keyof CreateReminderForm, string>>>(
    {},
  );
  const [serverError, setServerError] = useState<string | null>(null);

  const { data: reminders = [], isLoading } = useQuery<ReminderResponse[]>({
    queryKey: ['reminders'],
    queryFn: () => apiRequest<ReminderResponse[]>('/api/reminders'),
  });

  const createReminder = useMutation({
    mutationFn: (values: { itemId: number; dueDate: string; label?: string }) =>
      apiRequest<ReminderResponse>('/api/reminders', {
        method: 'POST',
        body: JSON.stringify(values),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reminders'] });
      setShowForm(false);
      setFormValues({ itemId: '', dueDate: '', label: '' });
      setFormErrors({});
      setServerError(null);
    },
    onError: (error) => {
      if (error instanceof ApiError && error.field) {
        setFormErrors({ [error.field as keyof CreateReminderForm]: error.message });
      } else if (error instanceof ApiError) {
        setServerError(error.message);
      } else {
        setServerError('Failed to create reminder.');
      }
    },
  });

  const updateReminder = useMutation({
    mutationFn: ({
      id,
      dismissed,
      dueDate,
      label,
    }: {
      id: number;
      dismissed?: boolean;
      dueDate?: string;
      label?: string;
    }) =>
      apiRequest<ReminderResponse>(`/api/reminders/${id}`, {
        method: 'PATCH',
        body: JSON.stringify({ dismissed, dueDate, label }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reminders'] });
    },
    onError: (error) => {
      if (error instanceof ApiError) {
        setServerError(error.message);
      }
    },
  });

  function handleFormChange(field: keyof CreateReminderForm, value: string) {
    setFormValues((prev) => ({ ...prev, [field]: value }));
    setFormErrors((prev) => ({ ...prev, [field]: undefined }));
    setServerError(null);
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const parseResult = createReminderSchema.safeParse(formValues);
    if (!parseResult.success) {
      const errors: Partial<Record<keyof CreateReminderForm, string>> = {};
      for (const issue of parseResult.error.issues) {
        errors[issue.path[0] as keyof CreateReminderForm] = issue.message;
      }
      setFormErrors(errors);
      return;
    }

    const itemIdNum = parseInt(formValues.itemId, 10);
    if (isNaN(itemIdNum)) {
      setFormErrors({ itemId: 'Item ID must be a number' });
      return;
    }

    createReminder.mutate({
      itemId: itemIdNum,
      dueDate: formValues.dueDate,
      label: formValues.label || undefined,
    });
  }

  const activeReminders = reminders.filter((r) => r.status !== 'DISMISSED');
  const dueSoonReminders = activeReminders.filter((r) => r.dueWithin24Hours);
  const otherReminders = activeReminders.filter((r) => !r.dueWithin24Hours);

  function formatDueDate(dateStr: string): string {
    const date = new Date(dateStr);
    const today = new Date();
    const tomorrow = new Date(today);
    tomorrow.setDate(today.getDate() + 1);

    if (dateStr === today.toISOString().split('T')[0]) return 'Today';
    if (dateStr === tomorrow.toISOString().split('T')[0]) return 'Tomorrow';
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  }

  function ReminderRow({ reminder }: { reminder: ReminderResponse }) {
    const [editingDate, setEditingDate] = useState(false);
    const [newDate, setNewDate] = useState(reminder.dueDate);

    return (
      <div
        className={`bg-white border rounded-lg px-4 py-3 flex flex-col sm:flex-row sm:items-center gap-3 ${
          reminder.dueWithin24Hours ? 'border-highlight/40 bg-highlight/10' : 'border-gray-200'
        }`}
      >
        <div className="flex-1">
          <div className="flex items-center gap-2">
            {reminder.dueWithin24Hours && (
              <span className="material-symbols-outlined text-highlight" style={{fontSize:'16px'}} title="Due within 24 hours" aria-label="Due soon">
                schedule
              </span>
            )}
            <span className="text-sm font-medium text-gray-900">{reminder.label}</span>
            {reminder.status === 'PENDING_CONFIRMATION' && (
              <span className="text-xs bg-yellow-100 text-yellow-700 px-1.5 py-0.5 rounded">
                Pending confirmation
              </span>
            )}
            {reminder.status === 'PENDING' && (
              <span className="text-xs bg-gray-100 text-gray-600 px-1.5 py-0.5 rounded">
                Staleness reminder
              </span>
            )}
          </div>
          <div className="flex items-center gap-3 mt-1">
            {editingDate ? (
              <div className="flex items-center gap-2">
                <input
                  type="date"
                  value={newDate}
                  onChange={(e) => setNewDate(e.target.value)}
                  className="border border-gray-300 rounded px-2 py-0.5 text-xs focus:outline-none focus:ring-1 focus:ring-primary"
                  min={new Date().toISOString().split('T')[0]}
                  aria-label="New due date"
                />
                <button
                  onClick={() => {
                    if (newDate !== reminder.dueDate) {
                      updateReminder.mutate({ id: reminder.id, dueDate: newDate });
                    }
                    setEditingDate(false);
                  }}
                  className="text-xs text-primary-dark hover:text-dark"
                >
                  Save
                </button>
                <button
                  onClick={() => {
                    setNewDate(reminder.dueDate);
                    setEditingDate(false);
                  }}
                  className="text-xs text-gray-400 hover:text-gray-600"
                >
                  Cancel
                </button>
              </div>
            ) : (
              <button
                onClick={() => setEditingDate(true)}
                className="text-xs text-gray-500 hover:text-primary-dark transition-colors"
                aria-label={`Due date: ${formatDueDate(reminder.dueDate)}. Click to change.`}
              >
                Due {formatDueDate(reminder.dueDate)}
              </button>
            )}
            <span className="text-xs text-gray-300">·</span>
            <span className="text-xs text-gray-400">Item #{reminder.itemId}</span>
          </div>
        </div>

        <div className="flex items-center gap-2 flex-shrink-0">
          {reminder.status === 'PENDING_CONFIRMATION' && (
            <button
              onClick={() => updateReminder.mutate({ id: reminder.id })}
              className="text-xs bg-primary/20 text-dark hover:bg-primary/40 rounded px-2.5 py-1 font-medium transition-colors"
              aria-label={`Confirm reminder for ${reminder.label}`}
            >
              Confirm
            </button>
          )}
          <button
            onClick={() => updateReminder.mutate({ id: reminder.id, dismissed: true })}
            className="text-xs text-gray-400 hover:text-red-600 hover:bg-red-50 rounded px-2.5 py-1 transition-colors"
            aria-label={`Dismiss reminder for ${reminder.label}`}
          >
            Dismiss
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="pb-20 sm:pb-6 space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-gray-900">Reminders</h1>
        <button
          onClick={() => setShowForm((s) => !s)}
          className="bg-primary hover:bg-primary-dark text-dark text-sm font-medium rounded-lg px-4 py-2 transition-colors"
        >
          {showForm ? 'Cancel' : '+ New reminder'}
        </button>
      </div>

      {/* Create reminder form */}
      {showForm && (
        <form
          onSubmit={handleSubmit}
          className="bg-white border border-gray-200 rounded-xl p-5 space-y-4"
        >
          <h2 className="text-sm font-semibold text-gray-800">New reminder</h2>

          {serverError && (
            <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg px-3 py-2 text-sm">
              {serverError}
            </div>
          )}

          <div className="flex flex-col sm:flex-row gap-4">
            <div className="flex-1">
              <label htmlFor="rem-item-id" className="block text-xs font-medium text-gray-700 mb-1">
                Item ID <span className="text-red-500">*</span>
              </label>
              <input
                id="rem-item-id"
                type="number"
                value={formValues.itemId}
                onChange={(e) => handleFormChange('itemId', e.target.value)}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                placeholder="e.g. 42"
                min={1}
              />
              {formErrors.itemId && (
                <p className="text-red-600 text-xs mt-1">{formErrors.itemId}</p>
              )}
            </div>

            <div className="flex-1">
              <label htmlFor="rem-due-date" className="block text-xs font-medium text-gray-700 mb-1">
                Due date <span className="text-red-500">*</span>
              </label>
              <input
                id="rem-due-date"
                type="date"
                value={formValues.dueDate}
                onChange={(e) => handleFormChange('dueDate', e.target.value)}
                min={new Date().toISOString().split('T')[0]}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              />
              {formErrors.dueDate && (
                <p className="text-red-600 text-xs mt-1">{formErrors.dueDate}</p>
              )}
            </div>

            <div className="flex-1">
              <label htmlFor="rem-label" className="block text-xs font-medium text-gray-700 mb-1">
                Label <span className="text-gray-400">(optional)</span>
              </label>
              <input
                id="rem-label"
                type="text"
                value={formValues.label}
                onChange={(e) => handleFormChange('label', e.target.value)}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                placeholder="Reminder label"
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
              disabled={createReminder.isPending}
              className="bg-primary hover:bg-primary-dark disabled:bg-primary/60 text-dark font-medium rounded-lg px-4 py-2 text-sm transition-colors"
            >
              {createReminder.isPending ? 'Creating...' : 'Create reminder'}
            </button>
          </div>
        </form>
      )}

      {isLoading && (
        <div className="text-center py-8 text-gray-400">Loading reminders...</div>
      )}

      {!isLoading && activeReminders.length === 0 && (
        <div className="text-center py-12 text-gray-400">
          No active reminders. Create one to stay on top of important saved items.
        </div>
      )}

      {/* Due soon section — AC-024 badge indicator */}
      {dueSoonReminders.length > 0 && (
        <div className="space-y-2">
          <h2 className="text-sm font-semibold text-highlight flex items-center gap-1.5">
            <span className="material-symbols-outlined text-highlight" style={{fontSize:'16px'}}>schedule</span>
            Due soon ({dueSoonReminders.length})
          </h2>
          {dueSoonReminders.map((r) => (
            <ReminderRow key={r.id} reminder={r} />
          ))}
        </div>
      )}

      {/* Other reminders */}
      {otherReminders.length > 0 && (
        <div className="space-y-2">
          {dueSoonReminders.length > 0 && (
            <h2 className="text-sm font-semibold text-gray-600">Upcoming</h2>
          )}
          {otherReminders.map((r) => (
            <ReminderRow key={r.id} reminder={r} />
          ))}
        </div>
      )}
    </div>
  );
}
