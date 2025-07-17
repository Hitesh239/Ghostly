package com.ghostly.posts.data

import com.ghostly.network.models.Result
import com.ghostly.posts.models.Post
import com.ghostly.posts.models.UpdatePostRequest
import com.ghostly.posts.models.UpdateRequestWrapper

interface EditPostUseCase {
    suspend operator fun invoke(
        postId: String,
        request: UpdateRequestWrapper,
    ): Result<Post>
    
    suspend fun editPostWithLatestData(
        post: Post
    ): Result<Post>
}

internal class EditPostUseCaseImpl(
    private val postRepository: PostRepository,
) : EditPostUseCase {
    override suspend fun invoke(
        postId: String,
        request: UpdateRequestWrapper,
    ): Result<Post> {
        val updatePostRequest = UpdatePostRequest(request.posts.filterIsInstance<com.ghostly.posts.models.UpdatePostBody>())
        return postRepository.updatePost(postId, updatePostRequest)
    }
    
    override suspend fun editPostWithLatestData(
        post: Post
    ): Result<Post> {
        // First, fetch the latest version of the post from the server
        // to get the most recent updated_at timestamp
        val latestPostResult = postRepository.getLatestPost(post.id)
        
        return when (latestPostResult) {
            is Result.Success -> {
                val latestPost = latestPostResult.data ?: return Result.Error(-1, "No post data received")
                // Create a new request with the latest updated_at timestamp
                val updatedRequest = UpdatePostRequest(
                    posts = listOf(
                        com.ghostly.posts.models.UpdatePostBody(
                            id = post.id,
                            title = post.title,
                            content = post.content,
                            excerpt = post.excerpt,
                            tags = post.tags.map { tag ->
                                com.ghostly.posts.models.TagDto(
                                    id = tag.id,
                                    name = tag.name,
                                    slug = tag.slug
                                )
                            },
                            status = post.status,
                            authorId = post.authors.firstOrNull()?.id,
                            featureImage = post.featureImage,
                            updatedAt = latestPost.updatedAt // Use the latest timestamp
                        )
                    )
                )
                
                // Now update with the latest timestamp
                postRepository.updatePost(post.id, updatedRequest)
            }
            is Result.Error -> latestPostResult
        }
    }
}