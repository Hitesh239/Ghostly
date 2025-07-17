package com.ghostly.android.posts.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.accompanist.flowlayout.FlowRow
import com.ghostly.android.R
import com.ghostly.android.posts.EditPostViewModel
import com.ghostly.posts.models.Post
import com.ghostly.posts.models.Tag
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPostScreen(
    navController: NavController,
    post: Post,
    viewModel: EditPostViewModel = koinViewModel()
) {
    val currentPost by viewModel.post.collectAsState()
    var tagInput by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    
    // Initialize the ViewModel with the post data
    LaunchedEffect(post) {
        viewModel.initializePost(post)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.edit_post),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.navigateUp()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            // TODO: Implement save functionality
                            navController.navigateUp()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(R.string.save)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .padding(bottom = 48.dp)
        ) {
            // Title field
            OutlinedTextField(
                value = currentPost?.title ?: "",
                onValueChange = { viewModel.updateTitle(it) },
                label = { Text(stringResource(R.string.title)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                singleLine = false,
                textStyle = MaterialTheme.typography.titleLarge
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Tags section
            TagsSection(
                tags = currentPost?.tags ?: emptyList(),
                tagInput = tagInput,
                onTagInputChange = { tagInput = it },
                onAddTag = { 
                    viewModel.addTag(it)
                    tagInput = ""
                },
                onRemoveTag = { viewModel.removeTag(it) },
                focusRequester = focusRequester
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Excerpt field
            OutlinedTextField(
                value = currentPost?.excerpt ?: "",
                onValueChange = { viewModel.updateExcerpt(it) },
                label = { Text(stringResource(R.string.excerpt)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5,
                singleLine = false
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Content field - placeholder for WebView editor
            OutlinedTextField(
                value = currentPost?.content ?: "",
                onValueChange = { viewModel.updateContent(it) },
                label = { Text(stringResource(R.string.content)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                maxLines = Int.MAX_VALUE,
                singleLine = false
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Placeholder text for WebView editor
            Text(
                text = stringResource(R.string.webview_editor_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TagsSection(
    tags: List<Tag>,
    tagInput: String,
    onTagInputChange: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (Tag) -> Unit,
    focusRequester: FocusRequester
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.tags),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // Tags display
        if (tags.isNotEmpty()) {
            FlowRow(
                mainAxisSpacing = 8.dp,
                crossAxisSpacing = 8.dp,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                tags.forEach { tag ->
                    AssistChip(
                        onClick = { /* optional edit tag */ },
                        label = { Text(tag.name) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_remove_tag),
                                modifier = Modifier.clickable {
                                    onRemoveTag(tag)
                                }
                            )
                        }
                    )
                }
            }
        }
        
        // Tag input
        OutlinedTextField(
            value = tagInput,
            onValueChange = onTagInputChange,
            label = { Text(stringResource(R.string.add_tag)) },
            placeholder = { Text(stringResource(R.string.press_enter_to_add)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .focusRequester(focusRequester),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = {
                    if (tagInput.isNotBlank()) {
                        onAddTag(tagInput.trim())
                    }
                }
            )
        )
    }
} 