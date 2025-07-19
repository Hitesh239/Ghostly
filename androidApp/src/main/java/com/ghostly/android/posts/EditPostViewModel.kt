package com.ghostly.android.posts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostly.posts.data.PostRepository
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
    private val postRepository: PostRepository
) : ViewModel() {
    
    private val _post = MutableStateFlow<Post?>(null)
    val post: StateFlow<Post?> = _post.asStateFlow()
    
    private val _uiState = MutableStateFlow<EditPostUiState>(EditPostUiState.Idle)
    val uiState: StateFlow<EditPostUiState> = _uiState.asStateFlow()
    
    fun initializePost(post: Post) {
        println("EditPostViewModel: Initializing post with title: ${post.title}")
        _post.value = post
    }
    
    fun updateTitle(newTitle: String) {
        println("EditPostViewModel: Updating title from '${_post.value?.title}' to '$newTitle'")
        val currentPost = _post.value
        if (currentPost != null) {
            val updatedPost = currentPost.copy(title = newTitle)
            println("EditPostViewModel: Created updated post with title: ${updatedPost.title}")
            _post.value = updatedPost
            println("EditPostViewModel: State updated, new post title: ${_post.value?.title}")
        } else {
            println("EditPostViewModel: Current post is null, cannot update title")
        }
    }
    
    fun addTag(tagName: String) {
        val currentPost = _post.value ?: return
        println("EditPostViewModel: Adding tag '$tagName' to post with ${currentPost.tags.size} existing tags")
        
        val updatedTags = currentPost.tags.toMutableList()
        
        // Check if tag already exists
        if (!updatedTags.any { it.name.equals(tagName, ignoreCase = true) }) {
            val newTag = Tag(
                id = "temp_${System.currentTimeMillis()}", // Temporary ID for new tags
                name = tagName.trim(),
                slug = tagName.trim().lowercase().replace(" ", "-")
            )
            updatedTags.add(newTag)
            val updatedPost = currentPost.copy(tags = updatedTags)
            println("EditPostViewModel: Created updated post with ${updatedPost.tags.size} tags")
            _post.value = updatedPost
            println("EditPostViewModel: State updated, new tag count: ${_post.value?.tags?.size}")
        } else {
            println("EditPostViewModel: Tag '$tagName' already exists, skipping")
        }
    }
    
    fun removeTag(tag: Tag) {
        val currentPost = _post.value ?: return
        println("EditPostViewModel: Removing tag '${tag.name}' from post with ${currentPost.tags.size} tags")
        
        val updatedTags = currentPost.tags.toMutableList()
        updatedTags.remove(tag)
        val updatedPost = currentPost.copy(tags = updatedTags)
        println("EditPostViewModel: Created updated post with ${updatedPost.tags.size} tags")
        _post.value = updatedPost
        println("EditPostViewModel: State updated, new tag count: ${_post.value?.tags?.size}")
    }
    
    fun updateExcerpt(newExcerpt: String) {
        println("EditPostViewModel: Updating excerpt from '${_post.value?.excerpt}' to '$newExcerpt'")
        val currentPost = _post.value
        if (currentPost != null) {
            val updatedPost = currentPost.copy(excerpt = newExcerpt)
            println("EditPostViewModel: Created updated post with excerpt: ${updatedPost.excerpt}")
            _post.value = updatedPost
            println("EditPostViewModel: State updated, new post excerpt: ${_post.value?.excerpt}")
        } else {
            println("EditPostViewModel: Current post is null, cannot update excerpt")
        }
    }
    
    fun updateContent(newContent: String) {
        println("EditPostViewModel: Updating content")
        _post.value = _post.value?.copy(content = newContent)
    }
    
    fun getUpdatedPost(): Post? {
        return _post.value
    }
    
    fun savePost() {
        viewModelScope.launch {
            _uiState.value = EditPostUiState.Saving
            val post = _post.value ?: return@launch
            
            val result = postRepository.updatePost(post)
            _uiState.value = when (result) {
                is com.ghostly.network.models.Result.Success -> EditPostUiState.Success
                is com.ghostly.network.models.Result.Error -> EditPostUiState.Error(result.message ?: "Unknown error")
            }
        }
    }
} 