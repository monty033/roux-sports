package net.montycasa.roux.data.nhl.dto

import kotlinx.serialization.Serializable

/**
 * A player's name, as the feed writes it: a default plus optional locale variants.
 *
 * Only [default] is read. The variants are Czech and Slovak spellings the league ships on the same object, and picking
 * one for an unrelated locale would be worse than showing the default.
 */
@Serializable
internal data class LocalizedNameDto(
    val default: String? = null,
)
