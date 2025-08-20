package com.ghostly.network.models

import kotlinx.serialization.Serializable

@Serializable
data class Meta(
    val pagination: Pagination? = null,
)

@Serializable
data class Pagination(
    val page: Int = 0,
    val limit: Int = 0,
    val pages: Int = 0,
    val total: Int = 0,
    val next: Int? = null,
    val prev: Int? = null,
)

@Serializable
data class ImageUploadResponse(
    val images: List<UploadedImage>
)

@Serializable
data class UploadedImage(
    val url: String,
)
