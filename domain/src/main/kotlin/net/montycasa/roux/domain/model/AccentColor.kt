package net.montycasa.roux.domain.model

/**
 * The saved accent choice, in the order Settings shows it. The colours themselves belong to the theme; the domain
 * names only the choice, so the stored name can be read here without any Android type.
 */
enum class AccentColor {
    Orange,
    Blue,
    Teal,
    Green,
    Purple,
    Red,
    Gold,
    Pink,
    Cyan,
    ;

    companion object {
        /** The shipped orange: what a fresh install shows, and what an unreadable stored value falls back to. */
        val DEFAULT: AccentColor = Orange
    }
}
