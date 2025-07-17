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
    
    suspend fun updatePost(postId: String, request: UpdatePostRequest): Result<Post>
    
    suspend fun getLatestPost(postId: String): Result<Post>

    suspend fun getPostById(id: String): Flow<Post>
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
                    updatedAt = post.updatedAt
                )
            )
        )

        return updatePost(post.id, request)
    }
    
    override suspend fun updatePost(postId: String, request: UpdatePostRequest): Result<Post> {
        return when (val result = apiService.updatePost(postId, request)) {
            is Result.Success -> {
                val updatedPost = result.data?.posts?.firstOrNull()?.let { postDto ->
                    Post(
                        id = postDto.id,
                        slug = "", // Will be filled from original post
                        createdAt = "", // Will be filled from original post
                        title = postDto.title,
                        content = postDto.content ?: "", // Use empty string if response doesn't include it
                        featureImage = postDto.featureImage,
                        status = postDto.status,
                        publishedAt = postDto.publishedAt,
                        updatedAt = postDto.updatedAt,
                        url = postDto.url,
                        visibility = postDto.visibility,
                        excerpt = postDto.excerpt,
                        authors = postDto.authors.map { authorDto ->
                            com.ghostly.posts.models.Author(
                                id = authorDto.id,
                                name = authorDto.name,
                                profileImage = authorDto.profileImage,
                                slug = authorDto.slug
                            )
                        },
                        tags = postDto.tags.map { tagDto ->
                            com.ghostly.posts.models.Tag(
                                id = "temp_${System.currentTimeMillis()}", // Temporary ID for new tags
                                name = tagDto.name,
                                slug = tagDto.name.lowercase().replace(" ", "-")
                            )
                        }
                    )
                } ?: return Result.Error(-1, "No post data received")

                postDataSource.updatePost(updatedPost)
                Result.Success(updatedPost)
            }

            is Result.Error -> Result.Error(result.errorCode, result.message)
        }
    }
    
    override suspend fun getLatestPost(postId: String): Result<Post> {
        // For now, we'll use the existing post data
        // In a real implementation, you'd make a GET request to fetch the latest post
        return when (val result = apiService.getPosts(1, 1)) {
            is Result.Success -> {
                val posts = result.data?.posts?.takeIf { it.isNotEmpty() } ?: return Result.Error(-1, "No posts found")
                Result.Success(posts.first())
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

    override suspend fun getPostById(id: String): Flow<Post> {
        return postDao.getPostWithAuthorsAndTags(id).map {
            it.toPost()
        }
    }
}