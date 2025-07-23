// commonMain
package com.ghostly.posts.data

import app.cash.paging.ExperimentalPagingApi
import app.cash.paging.LoadType
import app.cash.paging.PagingState
import app.cash.paging.RemoteMediator
import com.ghostly.database.dao.PostDao
import com.ghostly.database.dao.RemoteKeysDao
import com.ghostly.database.entities.PostWithAuthorsAndTags
import com.ghostly.database.entities.RemoteKeys
import com.ghostly.network.ApiService
import com.ghostly.network.models.Result
import kotlinx.datetime.Clock
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@ExperimentalPagingApi
class PostRemoteMediator(
    private val apiService: ApiService,
    private val postDao: PostDao,
    private val remoteKeysDao: RemoteKeysDao,
    private val postDataSource: PostDataSource,
) : RemoteMediator<Int, PostWithAuthorsAndTags>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, PostWithAuthorsAndTags>,
    ): MediatorResult {
        val page = when (loadType) {
            LoadType.REFRESH -> {
                val remoteKeys = getRemoteKeyClosestToCurrentPosition(state)
                remoteKeys?.nextKey?.minus(1) ?: 1
            }

            LoadType.PREPEND -> {
                val remoteKeys = getRemoteKeyForFirstItem(state)
                val prevKey = remoteKeys?.prevKey
                prevKey
                    ?: return MediatorResult.Success(endOfPaginationReached = remoteKeys != null)
            }

            LoadType.APPEND -> {
                val remoteKeys = getRemoteKeyForLastItem(state)
                val nextKey = remoteKeys?.nextKey
                nextKey
                    ?: return MediatorResult.Success(endOfPaginationReached = remoteKeys != null)
            }
        }

        return try {
            println("PostRemoteMediator: Loading data for page $page, loadType: $loadType")
            when (val postResult = apiService.getPosts(page, state.config.pageSize)) {
                is Result.Success -> {
                    println("PostRemoteMediator: API call successful")
                    // Clear remote keys for refresh operations
                    if (loadType == LoadType.REFRESH) {
                        println("PostRemoteMediator: Clearing remote keys for refresh")
                        remoteKeysDao.clearRemoteKeys()
                    }
                    
                    val posts = postResult.data?.posts ?: emptyList()
                    println("PostRemoteMediator: Received ${posts.size} posts from API")

                    val endOfPaginationReached = posts.isEmpty()

                    val prevKey = if (page > 1) page - 1 else null
                    val nextKey = if (endOfPaginationReached) null else page + 1
                    val remoteKeys = posts.map {
                        RemoteKeys(
                            postId = it.id, prevKey = prevKey, currentPage = page, nextKey = nextKey
                        )
                    }

                    // Use PostDataSource.refreshPosts for intelligent updates
                    println("PostRemoteMediator: Inserting ${posts.size} posts into database")
                    if (loadType == LoadType.REFRESH) {
                        // For refresh, clear remote keys but use intelligent post updates
                        remoteKeysDao.clearRemoteKeys()
                        postDataSource.refreshPosts(posts)
                    } else {
                        // For append/prepend, just add new posts
                        postDataSource.insertPosts(posts, clearFirst = false)
                    }
                    remoteKeysDao.insertAll(remoteKeys)
                    println("PostRemoteMediator: Successfully inserted posts and remote keys")
                    MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
                }

                is Result.Error -> {
                    println("PostRemoteMediator: API call failed: ${postResult.errorCode} - ${postResult.message}")
                    MediatorResult.Error(RuntimeException("${postResult.errorCode}: ${postResult.message}"))
                }
            }

        } catch (e: Exception) {
            println("PostRemoteMediator: Exception during load: ${e.message}")
            MediatorResult.Error(e)
        }
    }

    override suspend fun initialize(): InitializeAction {
        val cacheTimeout = 1.toDuration(DurationUnit.MINUTES).inWholeMilliseconds // Reduced to 1 minute for testing

        val shouldSkip = Clock.System.now().toEpochMilliseconds() - (remoteKeysDao.getCreationTime()
                ?: 0) < cacheTimeout
        
        println("PostRemoteMediator: Cache timeout: $cacheTimeout ms")
        println("PostRemoteMediator: Last creation time: ${remoteKeysDao.getCreationTime()}")
        println("PostRemoteMediator: Current time: ${Clock.System.now().toEpochMilliseconds()}")
        println("PostRemoteMediator: Should skip initial refresh: $shouldSkip")
        
        return if (shouldSkip) {
            println("PostRemoteMediator: SKIPPING initial refresh")
            InitializeAction.SKIP_INITIAL_REFRESH
        } else {
            println("PostRemoteMediator: LAUNCHING initial refresh")
            InitializeAction.LAUNCH_INITIAL_REFRESH
        }
    }

    private suspend fun getRemoteKeyClosestToCurrentPosition(state: PagingState<Int, PostWithAuthorsAndTags>): RemoteKeys? {
        return state.anchorPosition?.let { position ->
            state.closestItemToPosition(position)?.post?.id?.let { id ->
                remoteKeysDao.getRemoteKeyByPostId(id)
            }
        }
    }

    private suspend fun getRemoteKeyForFirstItem(state: PagingState<Int, PostWithAuthorsAndTags>): RemoteKeys? {
        return state.pages.firstOrNull {
            it.data.isNotEmpty()
        }?.data?.firstOrNull()?.let { postWrapper ->
            remoteKeysDao.getRemoteKeyByPostId(postWrapper.post.id)
        }
    }

    private suspend fun getRemoteKeyForLastItem(state: PagingState<Int, PostWithAuthorsAndTags>): RemoteKeys? {
        return state.pages.lastOrNull {
            it.data.isNotEmpty()
        }?.data?.lastOrNull()?.let { postWrapper ->
            remoteKeysDao.getRemoteKeyByPostId(postWrapper.post.id)
        }
    }
}
