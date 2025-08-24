package com.ghostly.android.posts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostly.posts.data.PostRepository
import com.ghostly.posts.data.PostDataSource
import com.ghostly.posts.models.Post
import com.ghostly.posts.models.Tag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class EditPostUiState {
    object Idle : EditPostUiState()
    object Saving : EditPostUiState()
    object Success : EditPostUiState()
    data class Error(val message: String) : EditPostUiState()
}

class EditPostViewModel(
    private val postRepository: PostRepository, private val postDataSource: PostDataSource
) : ViewModel() {

    private val _post = MutableStateFlow<Post?>(null)
    val post: StateFlow<Post?> = _post.asStateFlow()

    private val _uiState = MutableStateFlow<EditPostUiState>(EditPostUiState.Idle)
    val uiState: StateFlow<EditPostUiState> = _uiState.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    fun initializePost(post: Post) {

        _post.value = post
    }

    fun updateTitle(newTitle: String) {
        val currentPost = _post.value
        if (currentPost != null) {
            val updatedPost = currentPost.copy(title = newTitle)
            _post.value = updatedPost
        }
    }

    fun addTag(tagName: String) {
        val currentPost = _post.value ?: return
        val updatedTags = currentPost.tags.toMutableList()

        // Check if tag already exists
        if (!updatedTags.any { it.name.equals(tagName, ignoreCase = true) }) {
            val newTag = Tag(
                name = tagName.trim(), slug = tagName.trim().lowercase().replace(" ", "-")
            )
            updatedTags.add(newTag)
            val updatedPost = currentPost.copy(tags = updatedTags)
            _post.value = updatedPost
        }
    }

    fun removeTag(tag: Tag) {
        val currentPost = _post.value ?: return
        val updatedTags = currentPost.tags.toMutableList()
        updatedTags.remove(tag)
        val updatedPost = currentPost.copy(tags = updatedTags)
        _post.value = updatedPost
    }

    fun savePost() {
        viewModelScope.launch {
            _uiState.value = EditPostUiState.Saving
            val post = _post.value ?: return@launch


            val result = postRepository.updatePost(post)

            _uiState.value = when (result) {
                is com.ghostly.network.models.Result.Success -> {


                    // Refresh the post data from server to get the latest tags
                    val refreshResult = postRepository.refreshPostFromServer(post.id)

                    when (refreshResult) {
                        is com.ghostly.network.models.Result.Success -> {
                            val refreshedPost = refreshResult.data
                            if (refreshedPost != null) {
                                _post.value = refreshedPost
                                // Update the local database with server data
                                postDataSource.updatePost(refreshedPost)
                            }
                        }
                        is com.ghostly.network.models.Result.Error -> {
                            // Still use the update result if refresh fails
                            val updatedPost = result.data
                            if (updatedPost != null) {
                                _post.value = updatedPost
                                postDataSource.updatePost(updatedPost)
                            }
                        }
                    }

                    EditPostUiState.Success
                }

                is com.ghostly.network.models.Result.Error -> {
                    EditPostUiState.Error(result.message ?: "Unknown error")
                }
            }
        }
    }

    fun setFeatureImageUrl(url: String) {
        val currentPost = _post.value ?: return
        _post.value = currentPost.copy(featureImage = url)
    }

    fun uploadImageAndSetFeature(bytes: ByteArray, fileName: String, mimeType: String) {
        viewModelScope.launch {
            _isUploading.value = true
            _uiState.value = EditPostUiState.Idle // Clear any previous error
            try {
                val result = try {
                    postRepository.uploadImage(fileName, bytes, mimeType)
                } catch (e: Exception) {
                    _uiState.value = EditPostUiState.Error(e.message ?: "Upload failed")
                    _isUploading.value = false
                    return@launch
                }
                if (result is com.ghostly.network.models.Result.Success) {
                    val url = result.data ?: return@launch
                    setFeatureImageUrl(url)
                } else if (result is com.ghostly.network.models.Result.Error) {
                    _uiState.value = EditPostUiState.Error(result.message ?: "Upload failed")
                }
            } finally {
                _isUploading.value = false
            }
        }
    }
} 