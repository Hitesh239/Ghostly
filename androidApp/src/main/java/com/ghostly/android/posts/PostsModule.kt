package com.ghostly.android.posts

import androidx.paging.ExperimentalPagingApi
import com.ghostly.android.posts.EditPostViewModel
import io.ktor.client.plugins.logging.Logger
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import org.koin.androidx.viewmodel.dsl.viewModelOf

@OptIn(ExperimentalPagingApi::class)
val postsModule = module {
    viewModelOf(::PostDetailViewModel)
    viewModelOf(::PostsViewModel)
    viewModel { EditPostViewModel(get(), get(), get()) }
}