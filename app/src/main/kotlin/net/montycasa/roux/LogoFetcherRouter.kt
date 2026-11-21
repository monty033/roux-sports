package net.montycasa.roux

import coil3.ImageLoader
import coil3.Uri
import coil3.fetch.Fetcher
import coil3.request.Options
import net.montycasa.roux.data.net.EspnHttpClientFactory

/** True when a logo address is on ESPN's CDN host, which the ESPN client owns. Anything else belongs to the NHL client. */
internal fun isEspnLogo(data: Any?): Boolean {
    val text = data?.toString() ?: return false
    return runCatching { java.net.URI(text).host }.getOrNull()?.lowercase() == EspnHttpClientFactory.LOGO_HOST
}

/**
 * Sends each logo to the client that owns its host. A host neither client allows is handed to the NHL client, which
 * refuses it, so an unrecognized host is still blocked rather than fetched.
 */
internal class LogoFetcherRouter(
    private val nhl: Fetcher.Factory<Uri>,
    private val espn: Fetcher.Factory<Uri>,
) : Fetcher.Factory<Uri> {
    override fun create(
        data: Uri,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher? = (if (isEspnLogo(data)) espn else nhl).create(data, options, imageLoader)
}
