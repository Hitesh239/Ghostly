package com.ghostly.android.posts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostly.android.utils.getCurrentTimeFormatted
import com.ghostly.network.models.Result
import com.ghostly.posts.data.EditPostUseCase
import com.ghostly.posts.data.GetPostsUseCase
import com.ghostly.posts.models.Filter
import com.ghostly.posts.models.Post
import com.ghostly.posts.models.PostUiMessage
import com.ghostly.posts.models.UpdateRequestWrapper

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PostDetailViewModel(
    private val editPostUseCase: EditPostUseCase,
    private val getPostsUseCase: GetPostsUseCase,
) : ViewModel() {

    private val _post = MutableStateFlow<Post?>(null)
    private val currentPost: StateFlow<Post?> = _post

    private val _toastMessage = MutableStateFlow<PostUiMessage?>(null)
    val toastMessage = _toastMessage

    fun observePost(post: Post): StateFlow<Post?> {
        viewModelScope.launch {
            getPostsUseCase.getPostById(post.id).collectLatest { dbPost ->
                // If post is not found in database, use the passed post
                _post.emit(dbPost ?: post)
            }
        }
        return currentPost
    }

    suspend fun changePostStatus(post: Post, status: Filter) {
        val currentPostValue = currentPost.value ?: return
        val updatedPost = currentPostValue.copy(
            status = status.key,
            updatedAt = getCurrentTimeFormatted()
        )
        val response = editPostUseCase(
            updatedPost.id,
            UpdateRequestWrapper(
                listOf(
                    com.ghostly.posts.models.UpdatePostBody(
                        id = updatedPost.id,
                        title = updatedPost.title,
                        content = updatedPost.content,
                        excerpt = updatedPost.excerpt,
                        tags = updatedPost.tags.map { tag ->
                            com.ghostly.posts.models.TagDto(
                                id = tag.id,
                                name = tag.name,
                                slug = tag.slug
                            )
                        },
                        status = updatedPost.status,
                        authorId = updatedPost.authors.firstOrNull()?.id,
                        featureImage = updatedPost.featureImage,
                        updatedAt = updatedPost.updatedAt,
                        visibility = updatedPost.visibility,
                        publishedAt = updatedPost.publishedAt,
                        url = updatedPost.url,
                        slug = updatedPost.slug
                    )
                )
            )
        )
        when (response) {
            is Result.Success -> {
                _toastMessage.value = if (status is Filter.Published) {
                    PostUiMessage.PublishingSuccessful
                } else {
                    PostUiMessage.UnpublishingSuccessful
                }
            }

            is Result.Error -> {
                if (response.errorCode == 401) {
                    _toastMessage.value = if (status is Filter.Published) {
                        PostUiMessage.PublishingFailedUnauthorized
                    } else {
                        PostUiMessage.UnpublishingFailedUnauthorized
                    }
                } else {
                    _toastMessage.value = if (status is Filter.Published) {
                         PostUiMessage.PublishingFailed
                    } else {
                        PostUiMessage.UnpublishingFailed
                    }
                }
            }
        }
    }
}