package com.tabvault.backend.items;

/**
 * Discriminator for saved item types.
 *
 * - LINK:  a saved URL (browser tab or shared link)
 * - NOTE:  a plain text note created from the extension popup or dashboard
 * - VIDEO: a saved video URL (e.g. YouTube link)
 */
public enum ItemType {
    LINK,
    NOTE,
    VIDEO
}
