/**
 * Item card component for both grid and list views.
 *
 * AC-017: Inline editing of title, summary, and category assignment — edits are
 *         saved without navigating to a separate detail page.
 * AC-024: Displays a reminder badge indicator when dueWithin24Hours is true.
 */
import { useState, useRef } from 'react';
import type { ItemResponse, CategoryResponse, ReminderResponse } from '../api/types';
import { useUpdateItemCategory, useUpdateItemTitle } from './use-items';

interface ItemCardProps {
  item: ItemResponse;
  categories: CategoryResponse[];
  reminders: ReminderResponse[];
  viewMode: 'grid' | 'list';
  onVisit: (itemId: number) => void;
}

export default function ItemCard({
  item,
  categories,
  reminders,
  viewMode,
  onVisit,
}: ItemCardProps) {
  // Inline edit state for title and summary (AC-017)
  const [isEditingTitle, setIsEditingTitle] = useState(false);
  const [isEditingSummary, setIsEditingSummary] = useState(false);
  const [isEditingCategory, setIsEditingCategory] = useState(false);
  const [editTitle, setEditTitle] = useState(item.title ?? '');
  const [editSummary, setEditSummary] = useState(item.summary ?? '');
  const titleInputRef = useRef<HTMLInputElement>(null);
  const summaryInputRef = useRef<HTMLTextAreaElement>(null);

  const updateCategory = useUpdateItemCategory();
  const updateTitle = useUpdateItemTitle();

  const itemReminders = reminders.filter((r) => r.itemId === item.id && r.status !== 'DISMISSED');
  const hasDueSoonReminder = itemReminders.some((r) => r.dueWithin24Hours);
  const categoryName = categories.find((c) => c.id === item.categoryId)?.name;

  function handleCategoryChange(event: React.ChangeEvent<HTMLSelectElement>) {
    const value = event.target.value;
    const categoryId = value === '' ? null : parseInt(value, 10);
    updateCategory.mutate({ itemId: item.id, categoryId });
    setIsEditingCategory(false);
  }

  function handleTitleClick() {
    if (item.url) {
      onVisit(item.id);
      window.open(item.url, '_blank', 'noopener,noreferrer');
    }
  }

  function startEditTitle(event: React.MouseEvent) {
    event.preventDefault();
    event.stopPropagation();
    setEditTitle(item.title ?? '');
    setIsEditingTitle(true);
    setTimeout(() => titleInputRef.current?.focus(), 0);
  }

  function saveTitle() {
    const trimmed = editTitle.trim();
    if (trimmed && trimmed !== item.title) {
      updateTitle.mutate({ itemId: item.id, title: trimmed });
    }
    setIsEditingTitle(false);
  }

  function startEditSummary(event: React.MouseEvent) {
    event.preventDefault();
    event.stopPropagation();
    setEditSummary(item.summary ?? '');
    setIsEditingSummary(true);
    setTimeout(() => summaryInputRef.current?.focus(), 0);
  }

  function saveSummary() {
    const trimmed = editSummary.trim();
    if (trimmed !== (item.summary ?? '')) {
      updateTitle.mutate({ itemId: item.id, summary: trimmed });
    }
    setIsEditingSummary(false);
  }

  const typeLabel =
    item.itemType === 'NOTE'
      ? 'Note'
      : item.itemType === 'VIDEO'
      ? 'Video'
      : 'Link';

  const typeColor =
    item.itemType === 'NOTE'
      ? 'bg-primary/20 text-dark'
      : item.itemType === 'VIDEO'
      ? 'bg-purple-100 text-purple-700'
      : 'bg-primary/10 text-dark';

  if (viewMode === 'list') {
    return (
      <div className="bg-white border border-gray-200 rounded-lg px-4 py-3 flex items-start gap-4 hover:border-gray-300 transition-colors">
        {/* Favicon / icon */}
        <div className="flex-shrink-0 w-8 h-8 mt-0.5">
          {item.faviconUrl?.startsWith('https://') ? (
            <img
              src={item.faviconUrl}
              alt=""
              className="w-8 h-8 rounded"
              onError={(e) => {
                (e.target as HTMLImageElement).style.display = 'none';
              }}
            />
          ) : (
            <div className="w-8 h-8 bg-gray-100 rounded flex items-center justify-center">
              <span className="material-symbols-outlined" style={{fontSize:'16px'}}>
                {item.itemType === 'NOTE' ? 'note' : item.itemType === 'VIDEO' ? 'play_circle' : 'link'}
              </span>
            </div>
          )}
        </div>

        {/* Content */}
        <div className="flex-1 min-w-0">
          <div className="flex items-start justify-between gap-2">
            <div className="flex items-center gap-2 flex-wrap">
              {hasDueSoonReminder && (
                <span
                  title="Reminder due soon"
                  className="inline-flex items-center text-orange-500 text-xs font-medium"
                  aria-label="Reminder due within 24 hours"
                >
                  <span className="material-symbols-outlined" style={{fontSize:'14px'}}>notifications</span>
                </span>
              )}
              {item.pinned && (
                <span className="text-xs bg-secondary/30 text-dark px-1.5 py-0.5 rounded" title="Pinned">
                  <span className="material-symbols-outlined" style={{fontSize:'12px'}}>push_pin</span>
                </span>
              )}
              {/* Inline title editing — AC-017 */}
              {isEditingTitle ? (
                <input
                  ref={titleInputRef}
                  type="text"
                  value={editTitle}
                  onChange={(e) => setEditTitle(e.target.value)}
                  onBlur={saveTitle}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') saveTitle();
                    if (e.key === 'Escape') setIsEditingTitle(false);
                  }}
                  className="text-sm font-medium text-gray-900 border-b border-primary focus:outline-none w-full"
                  aria-label="Edit title"
                />
              ) : (
                <div className="flex items-center gap-1">
                  <button
                    onClick={handleTitleClick}
                    className="text-sm font-medium text-gray-900 hover:text-primary-dark text-left"
                    aria-label={`Open ${item.title ?? 'item'}`}
                  >
                    {item.title ?? item.url ?? 'Untitled'}
                  </button>
                  <button
                    onClick={startEditTitle}
                    className="text-gray-300 hover:text-gray-500 text-xs"
                    aria-label="Edit title inline"
                    title="Edit title"
                  >
                    <span className="material-symbols-outlined" style={{fontSize:'14px'}}>edit</span>
                  </button>
                </div>
              )}
            </div>

            <span className={`text-xs font-medium px-1.5 py-0.5 rounded flex-shrink-0 ${typeColor}`}>
              {typeLabel}
            </span>
          </div>

          {/* Inline summary editing — AC-017 */}
          {isEditingSummary ? (
            <textarea
              ref={summaryInputRef}
              value={editSummary}
              onChange={(e) => setEditSummary(e.target.value)}
              onBlur={saveSummary}
              onKeyDown={(e) => {
                if (e.key === 'Escape') setIsEditingSummary(false);
              }}
              rows={2}
              className="text-xs text-gray-500 mt-1 w-full border border-primary/60 rounded p-1 focus:outline-none resize-none"
              aria-label="Edit summary"
            />
          ) : item.summary ? (
            <div className="flex items-start gap-1 mt-1">
              <p className="text-xs text-gray-500 line-clamp-2">{item.summary}</p>
              <button
                onClick={startEditSummary}
                className="text-gray-300 hover:text-gray-500 text-xs flex-shrink-0"
                aria-label="Edit summary inline"
                title="Edit summary"
              >
                <span className="material-symbols-outlined" style={{fontSize:'14px'}}>edit</span>
              </button>
            </div>
          ) : null}

          {item.noteBody && (
            <p className="text-xs text-gray-600 mt-1 line-clamp-2 font-mono">{item.noteBody}</p>
          )}

          <div className="flex items-center gap-3 mt-1.5">
            {/* Category inline edit (AC-017) */}
            {isEditingCategory ? (
              <select
                autoFocus
                defaultValue={item.categoryId?.toString() ?? ''}
                onChange={handleCategoryChange}
                onBlur={() => setIsEditingCategory(false)}
                className="text-xs border border-gray-300 rounded px-1.5 py-0.5 focus:outline-none focus:ring-1 focus:ring-primary"
                aria-label="Select category"
              >
                <option value="">Uncategorized</option>
                {categories.map((cat) => (
                  <option key={cat.id} value={cat.id.toString()}>
                    {cat.name}
                  </option>
                ))}
              </select>
            ) : (
              <button
                onClick={() => setIsEditingCategory(true)}
                className="text-xs text-gray-400 hover:text-primary-dark transition-colors"
                title="Change category"
                aria-label={`Category: ${categoryName ?? 'Uncategorized'}. Click to change.`}
              >
                {categoryName ?? 'Uncategorized'}
              </button>
            )}

            <span className="text-xs text-gray-300">·</span>
            <span className="text-xs text-gray-400">
              {new Date(item.createdAt).toLocaleDateString()}
            </span>
          </div>
        </div>
      </div>
    );
  }

  // Grid view
  return (
    <div className="bg-white border border-gray-200 rounded-xl p-4 flex flex-col gap-2 hover:border-gray-300 transition-colors">
      {/* Header row */}
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2">
          {item.faviconUrl?.startsWith('https://') ? (
            <img
              src={item.faviconUrl}
              alt=""
              className="w-5 h-5 rounded flex-shrink-0"
              onError={(e) => {
                (e.target as HTMLImageElement).style.display = 'none';
              }}
            />
          ) : (
            <span className="material-symbols-outlined flex-shrink-0" style={{fontSize:'18px'}}>
              {item.itemType === 'NOTE' ? 'note' : item.itemType === 'VIDEO' ? 'play_circle' : 'link'}
            </span>
          )}
          {hasDueSoonReminder && (
            <span
              title="Reminder due soon"
              className="text-orange-500"
              aria-label="Reminder due within 24 hours"
            >
              <span className="material-symbols-outlined" style={{fontSize:'16px'}}>notifications</span>
            </span>
          )}
          {item.pinned && (
            <span className="text-xs bg-secondary/30 text-dark px-1.5 py-0.5 rounded" title="Pinned">
              <span className="material-symbols-outlined" style={{fontSize:'12px'}}>push_pin</span>
            </span>
          )}
        </div>
        <span className={`text-xs font-medium px-1.5 py-0.5 rounded flex-shrink-0 ${typeColor}`}>
          {typeLabel}
        </span>
      </div>

      {/* Title — inline editable (AC-017) */}
      {isEditingTitle ? (
        <input
          ref={titleInputRef}
          type="text"
          value={editTitle}
          onChange={(e) => setEditTitle(e.target.value)}
          onBlur={saveTitle}
          onKeyDown={(e) => {
            if (e.key === 'Enter') saveTitle();
            if (e.key === 'Escape') setIsEditingTitle(false);
          }}
          className="text-sm font-medium text-gray-900 border-b border-primary focus:outline-none w-full"
          aria-label="Edit title"
        />
      ) : (
        <div className="flex items-start gap-1">
          <button
            onClick={handleTitleClick}
            className="text-sm font-medium text-gray-900 hover:text-primary-dark text-left line-clamp-2 flex-1"
            aria-label={`Open ${item.title ?? 'item'}`}
          >
            {item.title ?? item.url ?? 'Untitled'}
          </button>
          <button
            onClick={startEditTitle}
            className="text-gray-300 hover:text-gray-500 flex-shrink-0"
            aria-label="Edit title inline"
            title="Edit title"
          >
            <span className="material-symbols-outlined" style={{fontSize:'14px'}}>edit</span>
          </button>
        </div>
      )}

      {/* Summary — inline editable (AC-017) */}
      {isEditingSummary ? (
        <textarea
          ref={summaryInputRef}
          value={editSummary}
          onChange={(e) => setEditSummary(e.target.value)}
          onBlur={saveSummary}
          onKeyDown={(e) => {
            if (e.key === 'Escape') setIsEditingSummary(false);
          }}
          rows={3}
          className="text-xs text-gray-500 w-full border border-primary/60 rounded p-1 focus:outline-none resize-none"
          aria-label="Edit summary"
        />
      ) : item.summary ? (
        <div className="flex items-start gap-1">
          <p className="text-xs text-gray-500 line-clamp-3 flex-1">{item.summary}</p>
          <button
            onClick={startEditSummary}
            className="text-gray-300 hover:text-gray-500 flex-shrink-0"
            aria-label="Edit summary inline"
            title="Edit summary"
          >
            <span className="material-symbols-outlined" style={{fontSize:'14px'}}>edit</span>
          </button>
        </div>
      ) : null}

      {item.noteBody && !item.summary && (
        <p className="text-xs text-gray-600 line-clamp-3 font-mono">{item.noteBody}</p>
      )}

      {/* Footer */}
      <div className="mt-auto pt-2 flex items-center justify-between">
        {/* Category inline edit (AC-017) */}
        {isEditingCategory ? (
          <select
            autoFocus
            defaultValue={item.categoryId?.toString() ?? ''}
            onChange={handleCategoryChange}
            onBlur={() => setIsEditingCategory(false)}
            className="text-xs border border-gray-300 rounded px-1.5 py-0.5 focus:outline-none focus:ring-1 focus:ring-primary"
            aria-label="Select category"
          >
            <option value="">Uncategorized</option>
            {categories.map((cat) => (
              <option key={cat.id} value={cat.id.toString()}>
                {cat.name}
              </option>
            ))}
          </select>
        ) : (
          <button
            onClick={() => setIsEditingCategory(true)}
            className="text-xs text-gray-400 hover:text-primary-dark transition-colors truncate max-w-[60%]"
            title="Change category"
            aria-label={`Category: ${categoryName ?? 'Uncategorized'}. Click to change.`}
          >
            {categoryName ?? 'Uncategorized'}
          </button>
        )}

        <span className="text-xs text-gray-400 flex-shrink-0">
          {new Date(item.createdAt).toLocaleDateString()}
        </span>
      </div>
    </div>
  );
}
