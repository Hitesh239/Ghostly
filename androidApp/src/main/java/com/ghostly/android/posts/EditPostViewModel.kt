package com.ghostly.android.posts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostly.posts.models.Post
import com.ghostly.posts.models.Tag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditPostViewModel : ViewModel() {
    
    private val _post = MutableStateFlow<Post?>(null)
    val post: StateFlow<Post?> = _post.asStateFlow()
    
    fun initializePost(post: Post) {
        _post.value = post
    }
    
    fun updateTitle(newTitle: String) {
        _post.value = _post.value?.copy(title = newTitle)
    }
    
    fun addTag(tagName: String) {
        val currentPost = _post.value ?: return
        val updatedTags = currentPost.tags.toMutableList()
        
        // Check if tag already exists
        if (!updatedTags.any { it.name.equals(tagName, ignoreCase = true) }) {
            val newTag = Tag(
                id = "temp_${System.currentTimeMillis()}", // Temporary ID for new tags
                name = tagName.trim(),
                slug = tagName.trim().lowercase().replace(" ", "-")
            )
            updatedTags.add(newTag)
            _post.value = currentPost.copy(tags = updatedTags)
        }
    }
    
    fun removeTag(tag: Tag) {
        val currentPost = _post.value ?: return
        val updatedTags = currentPost.tags.toMutableList()
        updatedTags.remove(tag)
        _post.value = currentPost.copy(tags = updatedTags)
    }
    
    fun updateExcerpt(newExcerpt: String) {
        _post.value = _post.value?.copy(excerpt = newExcerpt)
    }
    
    fun updateContent(newContent: String) {
        _post.value = _post.value?.copy(content = newContent)
    }
    
    fun getUpdatedPost(): Post? {
        return _post.value
    }
} 