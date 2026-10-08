package com.chernuga.spge.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition

/**
 * Full-screen picker for a filter dimension.
 *
 * <p>Wear has no comfortable multi-column list, so this is a single scaling
 * column with the current choice marked. It scrolls to the selection on open so
 * a long roster (teachers, rooms) starts where the user left off rather than at
 * the top.
 */
@Composable
fun PickerDialog(
    names: List<String>,
    selected: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val state = rememberScalingLazyListState()
    val selectedIndex = remember(names, selected) { names.indexOf(selected) }

    // Bring the current selection into view on open.
    LaunchedEffect(selectedIndex) {
        if (selectedIndex > 0) {
            state.scrollToItem(selectedIndex + 1)
        }
    }

    // ScalingLazyColumn applies a negative-padding modifier that asserts on an
    // unbounded height, so it must receive the Scaffold's bounded content slot
    // directly. Wrapping it in a Box re-measures it without that bound and
    // crashes with "height should be bounded".
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = state) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.Background),
            state = state,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item(key = "cancel") {
                Chip(
                    label = { Text("Назад", fontSize = 11.sp) },
                    onClick = onDismiss,
                    colors = ChipDefaults.secondaryChipColors()
                )
            }

            items(count = names.size, key = { names[it] }) { i ->
                val name = names[i]
                val isSelected = i == selectedIndex
                Chip(
                    label = {
                        Text(
                            text = name,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold
                            else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = { onPick(name) },
                    colors = if (isSelected) ChipDefaults.primaryChipColors()
                    else ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}
