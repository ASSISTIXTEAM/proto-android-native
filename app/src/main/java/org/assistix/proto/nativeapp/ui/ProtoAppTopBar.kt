package org.assistix.proto.nativeapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtoAppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    logoHeight: androidx.compose.ui.unit.Dp = 38.dp,
    onLogoClick: (() -> Unit)? = null,
    showSearch: Boolean = false,
    searchExpanded: Boolean = false,
    searchQuery: String = "",
    onSearchToggle: (() -> Unit)? = null,
    onSearchQueryChange: (String) -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    Column(modifier) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    ProtoLogo(
                        height = logoHeight,
                        contentDescription = if (onLogoClick != null) UiStrings.chats else UiStrings.appName,
                        modifier =
                            if (onLogoClick != null) {
                                Modifier.clickable(onClick = onLogoClick)
                            } else {
                                Modifier
                            },
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            },
            actions = {
                if (showSearch && onSearchToggle != null) {
                    IconButton(onClick = onSearchToggle) {
                        Icon(
                            if (searchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = UiStrings.searchNick,
                        )
                    }
                }
                actions()
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
        )
        ProtoTopBarDivider()
        AnimatedVisibility(
            visible = showSearch && searchExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text(UiStrings.searchNick) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                singleLine = true,
                shape = ProtoShapes.field,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.35f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.2f),
                    ),
            )
        }
    }
}
