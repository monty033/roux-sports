package net.montycasa.roux.ui

import android.content.Context
import coil3.ComponentRegistry
import coil3.ImageLoader

/**
 * An image loader with no fetchers, so artwork resolves to nothing.
 *
 * Every crest and team badge in the app is a network URL, so a test that lets Coil reach the network records whatever
 * the machine could reach that minute — a logo where the host answered, an abbreviation where it did not. The
 * references in `src/test/screenshots` were recorded without artwork, and this keeps a capture honest to them instead
 * of re-recording them to chase a host.
 *
 * `components(registry)` replaces Coil's default registry; the `components { }` factory function adds to it and leaves
 * all eleven default fetchers in place, which is why the empty registry is built separately here.
 */
fun networkFreeImageLoader(context: Context): ImageLoader =
    ImageLoader
        .Builder(context)
        .components(ComponentRegistry.Builder().build())
        .build()
