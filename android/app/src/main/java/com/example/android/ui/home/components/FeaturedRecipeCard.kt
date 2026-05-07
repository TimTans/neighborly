package com.example.android.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.android.data.model.RecipeSuggestion
import com.example.android.ui.theme.NeighborlyColors
import com.example.android.ui.theme.NeighborlyShapes

/**
 * Featured recipe widget on Home. Mirrors iOS HomeView.swift:216-333. The card
 * renders nothing if there is no recipe, no in-flight request, and no error —
 * which matches iOS, where the card is skipped on first paint until something
 * meaningful is available to show.
 */
@Composable
fun FeaturedRecipeCard(
    recipe: RecipeSuggestion?,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (RecipeSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val showLoadingOnly = recipe == null && error == null && isLoading
    val showError = recipe == null && error != null
    if (recipe == null && error == null && !isLoading) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = NeighborlyShapes.LargeCard,
        colors = CardDefaults.cardColors(containerColor = NeighborlyColors.Surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = NeighborlyColors.Orange
                    )
                    Text(
                        text = "RECIPE FOR YOU",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = NeighborlyColors.Orange
                    )
                }
                RefreshPill(
                    isLoading = isLoading,
                    onClick = onRefresh
                )
            }

            when {
                recipe != null -> RecipeBody(recipe = recipe, onOpen = { onOpen(recipe) })
                showError -> ErrorBody(message = error.orEmpty(), onRetry = onRetry)
                showLoadingOnly -> LoadingBody()
            }
        }
    }
}

@Composable
private fun RefreshPill(isLoading: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(NeighborlyShapes.Pill)
            .background(NeighborlyColors.OrangeSoft)
            .clickable(enabled = !isLoading) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = NeighborlyColors.Orange,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "Refresh recipe",
                modifier = Modifier.size(14.dp),
                tint = NeighborlyColors.Orange
            )
        }
        Text(
            text = if (isLoading) "Refreshing" else "New",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = NeighborlyColors.Orange
        )
    }
}

@Composable
private fun RecipeBody(recipe: RecipeSuggestion, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpen() }
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = recipe.title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = NeighborlyColors.TextPrimary
        )

        if (recipe.summary.isNotBlank()) {
            Text(
                text = recipe.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = NeighborlyColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        val highlights = recipe.whyItMatches.take(2)
        if (highlights.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                highlights.forEach { reason ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(NeighborlyColors.Orange)
                        )
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = NeighborlyColors.TextSecondary
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val totalMinutes = (recipe.prepMinutes ?: 0) + (recipe.cookMinutes ?: 0)
            Text(
                text = if (totalMinutes > 0) "$totalMinutes min total" else "View details",
                style = MaterialTheme.typography.labelMedium,
                color = NeighborlyColors.TextTertiary
            )
            Text(
                text = "Open recipe →",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = NeighborlyColors.Orange
            )
        }
    }
}

@Composable
private fun ErrorBody(message: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "We couldn't generate a recipe right now.",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = NeighborlyColors.TextPrimary
        )
        if (message.isNotBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = NeighborlyColors.TextSecondary
            )
        }
        TextButton(onClick = onRetry) {
            Text(
                text = "Try Again",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = NeighborlyColors.Orange
            )
        }
    }
}

@Composable
private fun LoadingBody() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = NeighborlyColors.Orange,
            strokeWidth = 2.dp
        )
        Text(
            text = "Generating a recipe that fits your current preferences.",
            style = MaterialTheme.typography.bodyMedium,
            color = NeighborlyColors.TextSecondary
        )
    }
}
