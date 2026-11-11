package net.montycasa.roux.ui.common

import androidx.compose.ui.unit.dp

/**
 * Empty travel kept past the last stat column of a sideways table.
 *
 * Without it a table reaches its end exactly when its last column arrives, so the page's own swipe takes the very next
 * millimetre of a drag that was meant for the table. Sixty-four dp is short enough that a deliberate swipe still turns
 * the page and long enough that reading the last column costs nothing.
 */
internal val TABLE_EDGE_BUFFER = 64.dp
