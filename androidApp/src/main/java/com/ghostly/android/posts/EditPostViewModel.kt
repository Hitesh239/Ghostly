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
    private val postRepository: PostRepository,
    private val postDataSource: PostDataSource
) : ViewModel() {
    
    private val _post = MutableStateFlow<Post?>(null)
    val post: StateFlow<Post?> = _post.asStateFlow()
    
    private val _uiState = MutableStateFlow<EditPostUiState>(EditPostUiState.Idle)
    val uiState: StateFlow<EditPostUiState> = _uiState.asStateFlow()
    
    fun initializePost(post: Post) {
        Log.d("EditPostViewModel", "Initializing post with title: ${post.title}")
        _post.value = post
    }
    
    fun updateTitle(newTitle: String) {
        Log.d("EditPostViewModel", "Updating title from '${_post.value?.title}' to '$newTitle'")
        val currentPost = _post.value
        if (currentPost != null) {
            val updatedPost = currentPost.copy(title = newTitle)
            Log.d("EditPostViewModel", "Created updated post with title: ${updatedPost.title}")
            _post.value = updatedPost
            Log.d("EditPostViewModel", "State updated, new post title: ${_post.value?.title}")
        } else {
            Log.e("EditPostViewModel", "Current post is null, cannot update title")
        }
    }
    
    fun addTag(tagName: String) {
        val currentPost = _post.value ?: return
        Log.d("EditPostViewModel", "Adding tag '$tagName' to post with ${currentPost.tags.size} existing tags")
        
        val updatedTags = currentPost.tags.toMutableList()
        
        // Check if tag already exists
        if (!updatedTags.any { it.name.equals(tagName, ignoreCase = true) }) {
            val newTag = Tag(
                name = tagName.trim(),
                slug = tagName.trim().lowercase().replace(" ", "-")
            )
            updatedTags.add(newTag)
            val updatedPost = currentPost.copy(tags = updatedTags)
            Log.d("EditPostViewModel", "Created updated post with ${updatedPost.tags.size} tags")
            _post.value = updatedPost
            Log.d("EditPostViewModel", "State updated, new tag count: ${_post.value?.tags?.size}")
        } else {
            Log.d("EditPostViewModel", "Tag '$tagName' already exists, skipping")
        }
    }
    
    fun removeTag(tag: Tag) {
        val currentPost = _post.value ?: return
        Log.d("EditPostViewModel", "Removing tag '${tag.name}' from post with ${currentPost.tags.size} tags")
        
        val updatedTags = currentPost.tags.toMutableList()
        updatedTags.remove(tag)
        val updatedPost = currentPost.copy(tags = updatedTags)
        Log.d("EditPostViewModel", "Created updated post with ${updatedPost.tags.size} tags")
        _post.value = updatedPost
        Log.d("EditPostViewModel", "State updated, new tag count: ${_post.value?.tags?.size}")
    }
    
    fun updateExcerpt(newExcerpt: String) {
        Log.d("EditPostViewModel", "Updating excerpt from '${_post.value?.excerpt}' to '$newExcerpt'")
        val currentPost = _post.value
        if (currentPost != null) {
            val updatedPost = currentPost.copy(excerpt = newExcerpt)
            Log.d("EditPostViewModel", "Created updated post with excerpt: ${updatedPost.excerpt}")
            _post.value = updatedPost
            Log.d("EditPostViewModel", "State updated, new post excerpt: ${_post.value?.excerpt}")
        } else {
            Log.e("EditPostViewModel", "Current post is null, cannot update excerpt")
        }
    }
    
    fun updateContent(newContent: String) {
        Log.d("EditPostViewModel", "Updating content")
        _post.value = _post.value?.copy(content = newContent)
    }
    
    fun getUpdatedPost(): Post? {
        return _post.value
    }
    
    fun savePost() {
        viewModelScope.launch {
            _uiState.value = EditPostUiState.Saving
            val post = _post.value ?: return@launch
            
            Log.d("EditPostViewModel", "Sending post update to server ${post.tags}")
            val result = postRepository.updatePost(post)
            
            _uiState.value = when (result) {
                is com.ghostly.network.models.Result.Success -> {
                    Log.d("EditPostViewModel", "Server update successful")
                    
                    // Refresh the post data from server to get the latest tags
                    Log.d("EditPostViewModel", "Refreshing post data from server")
                    val refreshResult = postRepository.refreshPostFromServer(post.id)
                    
                    when (refreshResult) {
                        is com.ghostly.network.models.Result.Success -> {
                            val refreshedPost = refreshResult.data
                            if (refreshedPost != null) {
                                Log.d("EditPostViewModel", "Server refresh successful, updating local state with ${refreshedPost.tags} tags")
                                _post.value = refreshedPost
                                
                                // Update the local database with server data
                                postDataSource.updatePost(refreshedPost)
                                Log.d("EditPostViewModel", "Local database updated with server data")
                            } else {
                                Log.e("EditPostViewModel", "Server returned null post data")
                            }
                        }
                        is com.ghostly.network.models.Result.Error -> {
                            Log.e("EditPostViewModel", "Failed to refresh post from server: ${refreshResult.message}")
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
                    Log.e("EditPostViewModel", "Server update failed: ${result.message}")
                    EditPostUiState.Error(result.message ?: "Unknown error")
                }
            }
        }
    }
} 