package de.fampopprol.dhbwhorb.data.dualis.models

import kotlinx.serialization.Serializable

@Serializable
data class DualisDocument(
    val title: String,
    val date: String,
    val time: String,
    val downloadUrl: String
)
