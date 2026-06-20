/**
 * Main dashboard page.
 *
 * AC-014: Displays only items matching active filter criteria (category, content type,
 *         date range, and tag/suggestedCategory).
 * AC-015: Returns search results within 3 seconds (search is debounced 300ms; backend handles query).
 * AC-016: Grid/list toggle with persisted view preference.
 * AC-017: Inline category editing on item cards.
 * AC-067: Delete item with confirmation prompt — calls DELETE /api/items/{id} on confirmation.
 * AC-068: Items grouped by assigned category under labeled collapsible section headers.
 */
import { useState, useEffect } from 'react';
import { useItems, useCategories, useRecordVisit, useDeleteItem } from './use-items';
import { useViewPreference } from './use-view-preference';
import ItemCard from './ItemCard';
import CreateNoteModal from './CreateNoteModal';
import { useQuery } from '@tanstack/react-query';
import { apiRequest } from '../api/api-client';
import type { ItemResponse, ReminderResponse } from '../api/types';

const DEBOUNCE_DELAY_MS = 300;

/**
 * Returns a debounced copy of the value that only updates after `delay` ms
 * of no change. Used to avoid firing a search API call on every keystroke.
 */
function useDebounce<T>(value: T, delay: number): T {
  const [debouncedValue, setDebouncedValue] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedValue(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);

  return debouncedValue;
}

const CONTENT_TYPE_OPTIONS = [
  { value: '', label: 'All types' },
  { value: 'LINK', label: 'Links' },
  { value: 'NOTE', label: 'Notes' },
  { value: 'VIDEO', label: 'Videos' },
];

/** Date range filter options — filter items by createdAt (AC-014). */
const DATE_RANGE_OPTIONS = [
  { value: '', label: 'All time' },
  { value: 'today', label: 'Today' },
  { value: '7days', label: 'Last 7 days' },
  { value: '30days', label: 'Last 30 days' },
];

/**
 * Returns the earliest Date that satisfies the chosen date range option.
 * Items with createdAt before this threshold are excluded.
 * Returns null for "All time" (no threshold).
 */
function dateRangeThreshold(rangeValue: string): Date | null {
  if (!rangeValue) return null;
  const now = new Date();
  if (rangeValue === 'today') {
    return new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0, 0);
  }
  if (rangeValue === '7days') {
    const d = new Date(now);
    d.setDate(d.getDate() - 7);
    return d;
  }
  if (rangeValue === '30days') {
    const d = new Date(now);
    d.setDate(d.getDate() - 30);
    return d;
  }
  return null;
}

export default function DashboardPage() {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategoryId, setSelectedCategoryId] = useState<number | null>(null);
  const [selectedItemType, setSelectedItemType] = useState<string>('');
  // Date range filter — options: '', 'today', '7days', '30days' (AC-014)
  const [selectedDateRange, setSelectedDateRange] = useState<string>('');
  // Tag filter — matches against suggestedCategory (AI-assigned category tag) (AC-014)
  const [tagFilter, setTagFilter] = useState<string>('');
  const [currentPage, setCurrentPage] = useState(0);
  const [viewMode, setViewMode] = useViewPreference();
  const [showCreateNote, setShowCreateNote] = useState(false);
  // AC-068: tracks which category group sections are collapsed (by group key)
  const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(new Set());

  const debouncedQuery = useDebounce(searchQuery, DEBOUNCE_DELAY_MS);
  const debouncedTagFilter = useDebounce(tagFilter, DEBOUNCE_DELAY_MS);

  const { data: categoriesData } = useCategories();
  const categories = categoriesData ?? [];

  const { data: remindersData } = useQuery<ReminderResponse[]>({
    queryKey: ['reminders'],
    queryFn: () => apiRequest<ReminderResponse[]>('/api/reminders'),
  });
  const reminders = remindersData ?? [];

  const { data: itemsPage, isLoading, isError } = useItems({
    query: debouncedQuery || undefined,
    page: currentPage,
    pageSize: 20,
  });

  const recordVisit = useRecordVisit();
  const deleteItem = useDeleteItem();

  // Client-side filter by category, item type, date range, and tag (AC-014)
  const allItems = itemsPage?.content ?? [];
  const threshold = dateRangeThreshold(selectedDateRange);
  const filteredItems = allItems.filter((item) => {
    if (selectedCategoryId !== null && item.categoryId !== selectedCategoryId) return false;
    if (selectedItemType && item.itemType !== selectedItemType) return false;
    // Date range filter: compare item.createdAt against computed threshold
    if (threshold !== null && new Date(item.createdAt) < threshold) return false;
    // Tag filter: match against suggestedCategory (AI-assigned tag, case-insensitive substring)
    if (debouncedTagFilter.trim()) {
      const tag = item.suggestedCategory ?? '';
      if (!tag.toLowerCase().includes(debouncedTagFilter.trim().toLowerCase())) return false;
    }
    return true;
  });

  /**
   * AC-068: Group filtered items by categoryId.
   * Returns an ordered array of groups — categorized groups first (sorted by category sortOrder),
   * then the "Uncategorized" group last.
   */
  function buildCategoryGroups(items: ItemResponse[]): Array<{
    groupKey: string;
    label: string;
    color: string | null;
    groupItems: ItemResponse[];
  }> {
    const groupMap = new Map<string, { label: string; color: string | null; groupItems: ItemResponse[] }>();

    for (const item of items) {
      if (item.categoryId !== null) {
        const cat = categories.find((c) => c.id === item.categoryId);
        const key = `cat-${item.categoryId}`;
        if (!groupMap.has(key)) {
          groupMap.set(key, { label: cat?.name ?? `Category ${item.categoryId}`, color: cat?.color ?? null, groupItems: [] });
        }
        groupMap.get(key)!.groupItems.push(item);
      } else {
        const key = 'uncategorized';
        if (!groupMap.has(key)) {
          groupMap.set(key, { label: 'Uncategorized', color: null, groupItems: [] });
        }
        groupMap.get(key)!.groupItems.push(item);
      }
    }

    // Sort categorized groups by their category sortOrder, then append uncategorized at end
    const categorizedGroups = [...groupMap.entries()]
      .filter(([key]) => key !== 'uncategorized')
      .sort(([keyA], [keyB]) => {
        const idA = parseInt(keyA.replace('cat-', ''), 10);
        const idB = parseInt(keyB.replace('cat-', ''), 10);
        const sortA = categories.find((c) => c.id === idA)?.sortOrder ?? 0;
        const sortB = categories.find((c) => c.id === idB)?.sortOrder ?? 0;
        return sortA - sortB;
      })
      .map(([groupKey, value]) => ({ groupKey, ...value }));

    const uncategorizedEntry = groupMap.get('uncategorized');
    if (uncategorizedEntry) {
      categorizedGroups.push({ groupKey: 'uncategorized', ...uncategorizedEntry });
    }

    return categorizedGroups;
  }

  const categoryGroups = buildCategoryGroups(filteredItems);

  function toggleGroup(groupKey: string) {
    setCollapsedGroups((prev) => {
      const next = new Set(prev);
      if (next.has(groupKey)) {
        next.delete(groupKey);
      } else {
        next.add(groupKey);
      }
      return next;
    });
  }

  function handleSearchChange(event: React.ChangeEvent<HTMLInputElement>) {
    setSearchQuery(event.target.value);
    setCurrentPage(0);
  }

  function handleCategoryFilter(event: React.ChangeEvent<HTMLSelectElement>) {
    const value = event.target.value;
    setSelectedCategoryId(value === '' ? null : parseInt(value, 10));
    setCurrentPage(0);
  }

  function handleItemTypeFilter(event: React.ChangeEvent<HTMLSelectElement>) {
    setSelectedItemType(event.target.value);
    setCurrentPage(0);
  }

  function handleDateRangeFilter(event: React.ChangeEvent<HTMLSelectElement>) {
    setSelectedDateRange(event.target.value);
    setCurrentPage(0);
  }

  function handleTagFilterChange(event: React.ChangeEvent<HTMLInputElement>) {
    setTagFilter(event.target.value);
    setCurrentPage(0);
  }

  return (
    <div className="pb-20 sm:pb-6 space-y-5">
      {/* Header and actions */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <h1 className="text-xl font-semibold text-gray-900">Saved Items</h1>
        <button
          onClick={() => setShowCreateNote(true)}
          className="bg-primary hover:bg-primary-dark text-dark text-sm font-medium rounded-lg px-4 py-2 transition-colors"
          aria-label="Create a new note"
        >
          + New note
        </button>
      </div>

      {/* Search and filter controls */}
      <div className="flex flex-col sm:flex-row gap-3">
        {/* Search box — AC-015: results within 3 seconds (debounced 300ms + fast backend) */}
        <div className="flex-1">
          <label htmlFor="search-input" className="sr-only">
            Search saved items
          </label>
          <input
            id="search-input"
            type="search"
            value={searchQuery}
            onChange={handleSearchChange}
            placeholder="Search titles, summaries, notes..."
            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
            aria-label="Search saved items"
          />
        </div>

        {/* Category filter — AC-014 */}
        <div>
          <label htmlFor="category-filter" className="sr-only">
            Filter by category
          </label>
          <select
            id="category-filter"
            value={selectedCategoryId?.toString() ?? ''}
            onChange={handleCategoryFilter}
            className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
            aria-label="Filter by category"
          >
            <option value="">All categories</option>
            {categories.map((cat) => (
              <option key={cat.id} value={cat.id.toString()}>
                {cat.name}
              </option>
            ))}
          </select>
        </div>

        {/* Content type filter — AC-014 */}
        <div>
          <label htmlFor="type-filter" className="sr-only">
            Filter by content type
          </label>
          <select
            id="type-filter"
            value={selectedItemType}
            onChange={handleItemTypeFilter}
            className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
            aria-label="Filter by content type"
          >
            {CONTENT_TYPE_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>

        {/* Date saved filter — AC-014 */}
        <div>
          <label htmlFor="date-range-filter" className="sr-only">
            Filter by date saved
          </label>
          <select
            id="date-range-filter"
            value={selectedDateRange}
            onChange={handleDateRangeFilter}
            className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
            aria-label="Filter by date saved"
          >
            {DATE_RANGE_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>

        {/* Tag filter (matches suggestedCategory, AI-assigned) — AC-014 */}
        <div>
          <label htmlFor="tag-filter" className="sr-only">
            Filter by tag
          </label>
          <input
            id="tag-filter"
            type="text"
            value={tagFilter}
            onChange={handleTagFilterChange}
            placeholder="Filter by tag..."
            className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary w-36"
            aria-label="Filter by tag"
          />
        </div>

        {/* Grid/list toggle — AC-016 */}
        <div className="flex rounded-lg border border-gray-300 overflow-hidden" role="group" aria-label="View mode">
          <button
            onClick={() => setViewMode('grid')}
            className={`px-3 py-2 text-sm transition-colors ${
              viewMode === 'grid'
                ? 'bg-primary text-dark'
                : 'bg-white text-dark/60 hover:bg-gray-50'
            }`}
            aria-pressed={viewMode === 'grid'}
            aria-label="Grid view"
          >
            <span className="material-symbols-outlined" style={{fontSize:'16px'}}>grid_view</span> Grid
          </button>
          <button
            onClick={() => setViewMode('list')}
            className={`px-3 py-2 text-sm transition-colors ${
              viewMode === 'list'
                ? 'bg-primary text-dark'
                : 'bg-white text-dark/60 hover:bg-gray-50'
            }`}
            aria-pressed={viewMode === 'list'}
            aria-label="List view"
          >
            <span className="material-symbols-outlined" style={{fontSize:'16px'}}>view_list</span> List
          </button>
        </div>
      </div>

      {/* Active filter indicators */}
      {(selectedCategoryId !== null || selectedItemType || selectedDateRange || tagFilter.trim()) && (
        <div className="flex items-center gap-2 flex-wrap text-sm">
          <span className="text-gray-500">Filters:</span>
          {selectedCategoryId !== null && (
            <span className="bg-primary/20 text-dark px-2 py-0.5 rounded-full text-xs">
              {categories.find((c) => c.id === selectedCategoryId)?.name ?? 'Category'}
              <button
                onClick={() => setSelectedCategoryId(null)}
                className="ml-1 hover:text-dark"
                aria-label="Remove category filter"
              >
                &times;
              </button>
            </span>
          )}
          {selectedItemType && (
            <span className="bg-purple-100 text-purple-700 px-2 py-0.5 rounded-full text-xs">
              {CONTENT_TYPE_OPTIONS.find((o) => o.value === selectedItemType)?.label}
              <button
                onClick={() => setSelectedItemType('')}
                className="ml-1 hover:text-purple-900"
                aria-label="Remove type filter"
              >
                &times;
              </button>
            </span>
          )}
          {selectedDateRange && (
            <span className="bg-green-100 text-green-700 px-2 py-0.5 rounded-full text-xs">
              {DATE_RANGE_OPTIONS.find((o) => o.value === selectedDateRange)?.label}
              <button
                onClick={() => setSelectedDateRange('')}
                className="ml-1 hover:text-green-900"
                aria-label="Remove date range filter"
              >
                &times;
              </button>
            </span>
          )}
          {tagFilter.trim() && (
            <span className="bg-yellow-100 text-yellow-700 px-2 py-0.5 rounded-full text-xs">
              Tag: {tagFilter.trim()}
              <button
                onClick={() => setTagFilter('')}
                className="ml-1 hover:text-yellow-900"
                aria-label="Remove tag filter"
              >
                &times;
              </button>
            </span>
          )}
        </div>
      )}

      {/* Results */}
      {isLoading && (
        <div className="text-center py-12 text-gray-400">Loading...</div>
      )}

      {isError && (
        <div className="text-center py-12">
          <p className="text-red-600 text-sm">Failed to load items.</p>
          {!navigator.onLine && (
            <p className="text-gray-500 text-sm mt-1">
              You are offline. Showing cached data from your last visit.
            </p>
          )}
        </div>
      )}

      {!isLoading && !isError && filteredItems.length === 0 && (
        <div className="text-center py-12 text-gray-400">
          {debouncedQuery || selectedCategoryId !== null || selectedItemType || selectedDateRange || debouncedTagFilter.trim()
            ? 'No items match your search or filters.'
            : 'No saved items yet. Save a tab from the Chrome extension to get started.'}
        </div>
      )}

      {/* Item groups — AC-068: grouped by category under collapsible labeled section headers */}
      {!isLoading && filteredItems.length > 0 && (
        <div className="space-y-6">
          {categoryGroups.map(({ groupKey, label, color, groupItems }) => {
            const isCollapsed = collapsedGroups.has(groupKey);
            return (
              <section key={groupKey} aria-label={`Category group: ${label}`}>
                {/* Group header — collapsible (AC-068) */}
                <button
                  onClick={() => toggleGroup(groupKey)}
                  className="flex items-center gap-2 w-full text-left mb-3 group"
                  aria-expanded={!isCollapsed}
                  aria-controls={`group-items-${groupKey}`}
                >
                  {color && (
                    <span
                      className="inline-block w-2.5 h-2.5 rounded-full flex-shrink-0"
                      style={{ backgroundColor: color }}
                      aria-hidden="true"
                    />
                  )}
                  <span className="text-sm font-semibold text-dark/60 uppercase tracking-wide">
                    {label}
                  </span>
                  <span className="text-xs text-gray-400 font-normal normal-case tracking-normal">
                    ({groupItems.length})
                  </span>
                  <span
                    className="material-symbols-outlined text-dark/40 group-hover:text-dark/60 transition-colors ml-auto"
                    style={{ fontSize: '18px' }}
                    aria-hidden="true"
                  >
                    {isCollapsed ? 'expand_more' : 'expand_less'}
                  </span>
                </button>

                {/* Group items */}
                {!isCollapsed && (
                  <div
                    id={`group-items-${groupKey}`}
                    className={
                      viewMode === 'grid'
                        ? 'grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4'
                        : 'space-y-2'
                    }
                  >
                    {groupItems.map((item) => (
                      <ItemCard
                        key={item.id}
                        item={item}
                        categories={categories}
                        reminders={reminders}
                        viewMode={viewMode}
                        onVisit={(itemId) => recordVisit.mutate(itemId)}
                        onDelete={(itemId) => deleteItem.mutate(itemId)}
                      />
                    ))}
                  </div>
                )}
              </section>
            );
          })}
        </div>
      )}

      {/* Pagination */}
      {itemsPage && itemsPage.totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 pt-4">
          <button
            onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
            disabled={itemsPage.first}
            className="px-3 py-1.5 text-sm border border-gray-300 rounded-lg disabled:opacity-40 hover:bg-gray-50 transition-colors"
            aria-label="Previous page"
          >
            Previous
          </button>
          <span className="text-sm text-gray-500">
            Page {currentPage + 1} of {itemsPage.totalPages}
          </span>
          <button
            onClick={() => setCurrentPage((p) => p + 1)}
            disabled={itemsPage.last}
            className="px-3 py-1.5 text-sm border border-gray-300 rounded-lg disabled:opacity-40 hover:bg-gray-50 transition-colors"
            aria-label="Next page"
          >
            Next
          </button>
        </div>
      )}

      {/* Create note modal */}
      {showCreateNote && (
        <CreateNoteModal onClose={() => setShowCreateNote(false)} />
      )}
    </div>
  );
}
