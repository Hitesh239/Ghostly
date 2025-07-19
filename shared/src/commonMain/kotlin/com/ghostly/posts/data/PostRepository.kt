package com.ghostly.posts.data

import androidx.paging.ExperimentalPagingApi
import app.cash.paging.Pager
import app.cash.paging.PagingConfig
import app.cash.paging.PagingData
import com.ghostly.database.dao.PostDao
import com.ghostly.database.entities.PostWithAuthorsAndTags
import com.ghostly.mappers.toPost
import com.ghostly.network.ApiService
import com.ghostly.network.models.Result
import com.ghostly.posts.models.Post
import com.ghostly.posts.models.PostsResponse
import com.ghostly.posts.models.UpdatePostRequest
import com.ghostly.posts.models.UpdateRequestWrapper
import com.ghostly.posts.models.PostDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

interface PostRepository {
    suspend fun getOnePost(): Result<PostsResponse>

    fun getPosts(
        pageSize: Int,
        prefetchDistance: Int = pageSize,
    ): Flow<PagingData<PostWithAuthorsAndTags>>

    suspend fun publishPost(
        postId: String,
        requestWrapper: UpdateRequestWrapper,
    ): Result<Post>

    suspend fun updatePost(post: Post): Result<Post>

    suspend fun getPostById(id: String): Flow<Post?>
}

@OptIn(ExperimentalPagingApi::class)
class PostRepositoryImpl(
    private val apiService: ApiService,
    private val postDao: PostDao,
    private val postRemoteMediator: PostRemoteMediator,
    private val postDataSource: PostDataSource,
) : PostRepository {
    override suspend fun getOnePost(): Result<PostsResponse> {
        return apiService.getPosts(1, 1)
    }

    override suspend fun publishPost(
        postId: String,
        requestWrapper: UpdateRequestWrapper,
    ): Result<Post> {
        return when (val result = apiService.publishPost(postId, requestWrapper)) {
            is Result.Success -> {
                val posts = result.data?.posts?.takeIf { it.isNotEmpty() } ?: return Result.Error(
                    -1,
                    "Something went wrong"
                )
                postDataSource.updatePost(posts.first())
                Result.Success(posts.first())
            }

            is Result.Error -> Result.Error(result.errorCode, result.message)
        }
    }

    override suspend fun updatePost(post: Post): Result<Post> {
        // First, fetch the current post data to get the updated_at field
        val currentPostResult = apiService.getPostById(post.id)
        if (currentPostResult is Result.Error) {
            return Result.Error(currentPostResult.errorCode, currentPostResult.message)
        }

        val currentPost = (currentPostResult as? Result.Success)?.data?.posts?.firstOrNull() as? PostDto
        if (currentPost == null) {
            return Result.Error(-1, "Could not fetch current post data")
        }

        val request = UpdatePostRequest(
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
                    updatedAt = currentPost.updatedAt // Include the current updated_at field from the fetched post
                )
            )
        )

        return when (val result = apiService.updatePost(post.id, request)) {
            is Result.Success -> {
                val updatedPost = result.data?.posts?.firstOrNull()?.let { postDto ->
                    Post(
                        id = postDto.id,
                        slug = post.slug, // Keep original slug as it's not in response
                        createdAt = post.createdAt, // Keep original as it's not in response
                        title = postDto.title,
                        content = postDto.content ?: post.content, // Use original content if not in response
                        featureImage = postDto.featureImage,
                        status = postDto.status,
                        publishedAt = postDto.publishedAt,
                        updatedAt = postDto.updatedAt,
                        url = postDto.url,
                        visibility = postDto.visibility,
                        excerpt = postDto.excerpt,
                        authors = postDto.authors?.map { authorDto ->
                            com.ghostly.posts.models.Author(
                                id = authorDto.id,
                                name = authorDto.name,
                                profileImage = authorDto.profileImage,
                                slug = authorDto.slug
                            )
                        } ?: post.authors, // Use original authors if not in response
                        tags = postDto.tags?.map { tagDto ->
                            com.ghostly.posts.models.Tag(
                                id = "temp_${System.currentTimeMillis()}", // Temporary ID for new tags
                                name = tagDto.name,
                                slug = tagDto.name.lowercase().replace(" ", "-")
                            )
                        } ?: post.tags // Use original tags if not in response
                    )
                } ?: return Result.Error(-1, "No post data received")

                postDataSource.updatePost(updatedPost)
                Result.Success(updatedPost)
            }

            is Result.Error -> Result.Error(result.errorCode, result.message)
        }
    }

    override fun getPosts(
        pageSize: Int,
        prefetchDistance: Int,
    ): Flow<PagingData<PostWithAuthorsAndTags>> {
        val pagingDataSource = { postDao.getAllPostsWithAuthorsAndTags() }

        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                initialLoadSize = pageSize,
                prefetchDistance = prefetchDistance
            ),
            pagingSourceFactory = pagingDataSource,
            remoteMediator = postRemoteMediator
        ).flow
    }

    override suspend fun getPostById(id: String): Flow<Post?> {
        return postDao.getPostWithAuthorsAndTags(id).map { postWithAuthorsAndTags ->
            postWithAuthorsAndTags?.toPost()
        }
    }
}