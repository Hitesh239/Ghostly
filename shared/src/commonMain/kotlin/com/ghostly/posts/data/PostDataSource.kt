package com.ghostly.posts.data

import com.ghostly.database.dao.AuthorDao
import com.ghostly.database.dao.PostAuthorCrossRefDao
import com.ghostly.database.dao.PostDao
import com.ghostly.database.dao.PostTagCrossRefDao
import com.ghostly.database.dao.TagDao
import com.ghostly.database.entities.AuthorEntity
import com.ghostly.database.entities.PostAuthorCrossRef
import com.ghostly.database.entities.PostTagCrossRef
import com.ghostly.database.entities.TagEntity
import com.ghostly.mappers.toPostEntity
import com.ghostly.posts.models.Post

interface PostDataSource {
    suspend fun insertPosts(posts: List<Post>)
    suspend fun updatePost(post: Post)
}

class LocalPostDataSource(
    private val postDao: PostDao,
    private val authorDao: AuthorDao,
    private val tagDao: TagDao,
    private val postAuthorCrossRefDao: PostAuthorCrossRefDao,
    private val postTagCrossRefDao: PostTagCrossRefDao,
) : PostDataSource {
    override suspend fun insertPosts(posts: List<Post>) {
        // Clear all existing data first to ensure clean state
        postDao.clearAll()
        authorDao.clearAll()
        tagDao.clearAll()
        postAuthorCrossRefDao.clearAll()
        postTagCrossRefDao.clearAll()
        
        // Insert posts
        posts.map { it.toPostEntity() }.let {
            postDao.insertPosts(it)
        }
        
        // Insert authors
        authorDao.insertAuthors(posts.flatMap {
            it.authors.map { author ->
                AuthorEntity(
                    author.id, author.name, author.slug, author.profileImage
                )
            }
        })
        
        // Insert tags
        tagDao.insertTags(posts.flatMap {
            it.tags.map { tag ->
                TagEntity(
                    tag.id, tag.name, tag.slug
                )
            }
        })
        
        // Insert author relationships
        postAuthorCrossRefDao.insertPostAuthorCrossRef(posts.flatMap { post ->
            post.authors.map { author ->
                PostAuthorCrossRef(
                    post.id, author.id
                )
            }
        })
        
        // Insert tag relationships
        postTagCrossRefDao.insertPostTagCrossRef(posts.flatMap { post ->
            post.tags.map { tag ->
                PostTagCrossRef(
                    post.id, tag.id
                )
            }
        })
    }

    override suspend fun updatePost(post: Post) {
        postDao.updatePost(post.toPostEntity())
        
        // Update authors
        authorDao.insertAuthors(
            post.authors.map { author ->
                AuthorEntity(
                    author.id, author.name, author.slug, author.profileImage
                )
            }
        )
        
        // Update tags - first clear old relationships, then insert new ones
        tagDao.insertTags(post.tags.map { tag ->
            TagEntity(
                tag.id, tag.name, tag.slug
            )
        })
        
        // Clear old post-tag relationships and insert new ones
        postTagCrossRefDao.clearPostTagCrossRefs(post.id)
        postTagCrossRefDao.insertPostTagCrossRef(
            post.tags.map { tag ->
                PostTagCrossRef(
                    post.id, tag.id
                )
            }
        )
        
        // Update author relationships
        postAuthorCrossRefDao.insertPostAuthorCrossRef(
            post.authors.map { author ->
                PostAuthorCrossRef(
                    post.id, author.id
                )
            }
        )
    }
}