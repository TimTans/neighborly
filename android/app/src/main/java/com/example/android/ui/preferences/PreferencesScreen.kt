package com.example.android.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.android.data.repository.preferences.OptimizationPriority
import com.example.android.data.repository.preferences.PreferenceState
import com.example.android.data.repository.preferences.TransportMode
import com.example.android.ui.theme.NeighborlyColors
import com.example.android.ui.theme.NeighborlyShapes
import com.example.android.ui.theme.NeighborlySpacing
import com.example.android.viewmodel.shopper.ShopperViewModel

@Composable
fun PreferencesScreen(
    shopperViewModel: ShopperViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs = shopperViewModel.uiState.preferences

    Surface(modifier = modifier.fillMaxSize(), color = NeighborlyColors.Background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = NeighborlySpacing.ScreenHorizontal,
                    vertical = NeighborlySpacing.ScreenVertical
                ),
            verticalArrangement = Arrangement.spacedBy(NeighborlySpacing.CardGap)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onBack)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = NeighborlyColors.Blue
                )
                Text("Back", style = MaterialTheme.typography.bodyLarge, color = NeighborlyColors.Blue)
            }

            Text(
                text = "Your Preferences",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            PreferenceCard(title = "Prioritize") {
                PriorityDropdown(
                    selectedPriority = prefs.priority,
                    onSelect = shopperViewModel::updatePriority
                )
            }

            PreferenceCard(title = "Modes of Transportation") {
                TransportationModeRow(
                    prefs = prefs,
                    onToggleMode = shopperViewModel::toggleTransportMode
                )
            }

            PreferenceCard(title = "Trip Limits") {
                Text("Max Travel Distance: ${prefs.maxTravelDistanceMiles.toInt()} miles")
                Slider(
                    value = prefs.maxTravelDistanceMiles,
                    onValueChange = shopperViewModel::updateMaxTravelDistance,
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = NeighborlyColors.Blue,
                        activeTrackColor = NeighborlyColors.Blue,
                        inactiveTrackColor = NeighborlyColors.BlueSoft
                    )
                )

                Text("Max Stops: ${prefs.maxStops.toInt()}")
                Slider(
                    value = prefs.maxStops,
                    onValueChange = shopperViewModel::updateMaxStops,
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = NeighborlyColors.Green,
                        activeTrackColor = NeighborlyColors.Green,
                        inactiveTrackColor = NeighborlyColors.GreenSoft
                    )
                )
            }

            PreferenceCard(title = "Wellness") {
                ToggleRow(
                    title = "Enable wellness filters",
                    checked = prefs.wellnessEnabled,
                    onToggle = { shopperViewModel.updateWellnessEnabled(!prefs.wellnessEnabled) }
                )

                if (prefs.wellnessEnabled) {
                    WellnessField(
                        title = "Cholesterol limit",
                        value = prefs.cholesterolLimit,
                        placeholder = "100mg",
                        onValueChange = shopperViewModel::updateCholesterolLimit
                    )
                    WellnessField(
                        title = "Sodium limit",
                        value = prefs.sodiumLimit,
                        placeholder = "1000 mg/Day",
                        onValueChange = shopperViewModel::updateSodiumLimit
                    )
                    WellnessField(
                        title = "Sugar limit",
                        value = prefs.sugarLimit,
                        placeholder = "20g/day",
                        onValueChange = shopperViewModel::updateSugarLimit
                    )
                }
            }

            PreferenceCard(title = "Dietary Filters") {
                DietaryToggleSection(prefs, shopperViewModel)
            }

            PreferenceCard(title = "Avoid") {
                ToggleRow("Dairy", prefs.avoidDairy, shopperViewModel::toggleAvoidDairy)
                ToggleRow("Peanuts", prefs.avoidPeanuts, shopperViewModel::toggleAvoidPeanuts)
                ToggleRow("Shellfish", prefs.avoidShellfish, shopperViewModel::toggleAvoidShellfish)
                ToggleRow("Wheat", prefs.avoidWheat, shopperViewModel::toggleAvoidWheat)
            }
        }
    }
}

@Composable
private fun PreferenceCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = NeighborlyShapes.Card,
        colors = CardDefaults.cardColors(containerColor = NeighborlyColors.Surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(NeighborlySpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(NeighborlySpacing.CardGap),
            content = {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = NeighborlyColors.Green
                )
                content()
            }
        )
    }
}

@Composable
private fun PriorityDropdown(
    selectedPriority: OptimizationPriority,
    onSelect: (OptimizationPriority) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NeighborlyColors.FieldBackground, NeighborlyShapes.Medium)
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(selectedPriority.label, style = MaterialTheme.typography.bodyLarge)
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "Open prioritize menu",
                tint = NeighborlyColors.Blue
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            OptimizationPriority.values().forEach { priority ->
                DropdownMenuItem(onClick = {
                    expanded = false
                    onSelect(priority)
                }) {
                    Text(priority.label)
                }
            }
        }
    }
}

@Composable
private fun TransportationModeRow(
    prefs: PreferenceState,
    onToggleMode: (TransportMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TransportMode.values().forEach { mode ->
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onToggleMode(mode) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (prefs.enabledModes.contains(mode)) {
                        NeighborlyColors.BlueSoft
                    } else {
                        NeighborlyColors.FieldBackground
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = mode.emoji,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        textAlign = TextAlign.Center,
                        color = if (prefs.enabledModes.contains(mode)) {
                            NeighborlyColors.Blue
                        } else {
                            NeighborlyColors.TextPrimary
                        }
                    )
                    Switch(
                        checked = prefs.enabledModes.contains(mode),
                        onCheckedChange = { onToggleMode(mode) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = NeighborlyColors.Blue,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = NeighborlyColors.MutedControl
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NeighborlyColors.Green,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = NeighborlyColors.MutedControl
            )
        )
    }
}

@Composable
private fun DietaryToggleSection(prefs: PreferenceState, shopperViewModel: ShopperViewModel) {
    ToggleRow("Vegan", prefs.dietVegan, shopperViewModel::toggleDietVegan)
    ToggleRow("Gluten Free", prefs.dietGlutenFree, shopperViewModel::toggleDietGlutenFree)
    ToggleRow("Low Carb", prefs.dietLowCarb, shopperViewModel::toggleDietLowCarb)
    ToggleRow("Kosher", prefs.dietKosher, shopperViewModel::toggleDietKosher)
    ToggleRow("Halal", prefs.dietHalal, shopperViewModel::toggleDietHalal)
    ToggleRow("Keto", prefs.dietKeto, shopperViewModel::toggleDietKeto)
}

@Composable
private fun WellnessField(
    title: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeighborlyColors.Blue,
                unfocusedBorderColor = NeighborlyColors.Border,
                cursorColor = NeighborlyColors.Blue,
                focusedLabelColor = NeighborlyColors.Blue
            )
        )
    }
}
