package com.ghostly.posts.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdatePostRequest(
    val posts: List<UpdatePostBody>
)

@Serializable
data class UpdatePostBody(
    val id: String,
    val title: String,
    @SerialName("html")
    val content: String,
    val excerpt: String? = null,
    val tags: List<TagDto>? = null,
    val status: String? = null,
    @SerialName("author_id")
    val authorId: String? = null,
    @SerialName("feature_image")
    val featureImage: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    val visibility: String? = null,
    @SerialName("published_at")
    val publishedAt: String? = null,
    val url: String? = null,
    val slug: String? = null
)

@Serializable
data class TagDto(
    val id: String? = null,
    val name: String,
    val slug: String? = null
)

@Serializable
data class UpdatePostResponse(
    val posts: List<PostDto>
)

@Serializable
data class PostDto(
    val id: String,
    val title: String,
    @SerialName("html")
    val content: String,
    val excerpt: String?,
    @SerialName("feature_image")
    val featureImage: String?,
    val status: String,
    @SerialName("published_at")
    val publishedAt: String?,
    @SerialName("updated_at")
    val updatedAt: String?,
    val url: String,
    val visibility: String?,
    val authors: List<AuthorDto>,
    val tags: List<TagDto>
)

@Serializable
data class AuthorDto(
    val id: String,
    val name: String,
    @SerialName("profile_image")
    val profileImage: String?,
    val slug: String
) 