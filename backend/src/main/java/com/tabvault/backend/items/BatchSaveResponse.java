package com.tabvault.backend.items;

/**
 * Response body for the batch-save endpoint.
 *
 * AC-005: The endpoint returns HTTP 202 immediately with the count of tabs enqueued.
 */
public record BatchSaveResponse(int tabsEnqueued) {}
