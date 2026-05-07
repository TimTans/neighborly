package com.example.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.android.data.model.RecipeSuggestion
import com.example.android.ui.theme.NeighborlyColors
import com.example.android.ui.theme.NeighborlyShapes
import com.example.android.ui.theme.NeighborlySpacing

/**
 * Detail screen for a [RecipeSuggestion]. Mirrors iOS `RecipeDetailView`:
 * - header card with title, summary, and prep / cook / servings pills
 * - numbered Ingredients, Steps, Why It Matches, Nutrition Notes sections
 *
 * Sections are skipped entirely when their list is empty, matching the iOS
 * fallback behavior — an empty "Steps" header would imply incomplete data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipe: RecipeSuggestion,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = NeighborlyColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Recipe",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = NeighborlyColors.TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NeighborlyColors.TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NeighborlyColors.Background
                )
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = NeighborlyColors.Background
        ) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = NeighborlySpacing.ScreenHorizontal,
                    end = NeighborlySpacing.ScreenHorizontal,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { HeaderCard(recipe = recipe) }
                if (recipe.ingredients.isNotEmpty()) {
                    item { Section(title = "Ingredients", items = recipe.ingredients) }
                }
                if (recipe.steps.isNotEmpty()) {
                    item { Section(title = "Steps", items = recipe.steps) }
                }
                if (recipe.whyItMatches.isNotEmpty()) {
                    item { Section(title = "Why It Matches", items = recipe.whyItMatches) }
                }
                if (recipe.nutritionNotes.isNotEmpty()) {
                    item { Section(title = "Nutrition Notes", items = recipe.nutritionNotes) }
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(recipe: RecipeSuggestion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = NeighborlyShapes.LargeCard,
        colors = CardDefaults.cardColors(containerColor = NeighborlyColors.Surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = recipe.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = NeighborlyColors.TextPrimary
            )
            if (recipe.summary.isNotBlank()) {
                Text(
                    text = recipe.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeighborlyColors.TextSecondary
                )
            }
            val pills = buildList {
                recipe.prepMinutes?.takeIf { it > 0 }?.let { add(Pill("$it min prep", NeighborlyColors.Green)) }
                recipe.cookMinutes?.takeIf { it > 0 }?.let { add(Pill("$it min cook", NeighborlyColors.Orange)) }
                recipe.servings?.takeIf { it > 0 }?.let {
                    val noun = if (it == 1) "serving" else "servings"
                    add(Pill("$it $noun", NeighborlyColors.Blue))
                }
            }
            if (pills.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pills.forEach { DetailPill(it) }
                }
            }
        }
    }
}

private data class Pill(val text: String, val color: Color)

@Composable
private fun DetailPill(pill: Pill) {
    Box(
        modifier = Modifier
            .clip(NeighborlyShapes.Pill)
            .background(pill.color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = pill.text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = pill.color
        )
    }
}

@Composable
private fun Section(title: String, items: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = NeighborlyShapes.LargeCard,
        colors = CardDefaults.cardColors(containerColor = NeighborlyColors.Surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = NeighborlyColors.TextPrimary
            )
            items.forEachIndexed { index, item ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(NeighborlyColors.GreenSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = NeighborlyColors.Green
                        )
                    }
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeighborlyColors.TextSecondary
                    )
                }
            }
        }
    }
}
