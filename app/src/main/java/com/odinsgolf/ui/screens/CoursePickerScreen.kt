package com.odinsgolf.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.odinsgolf.data.CourseRepository
import com.odinsgolf.ui.components.rotaryScroll

@Composable
fun CoursePickerScreen(
    courses: List<CourseRepository.CourseSummary>,
    selectedFile: String,
    onSelect: (String) -> Unit,
) {
    val listState = rememberScalingLazyListState()
    Scaffold {
        ScalingLazyColumn(state = listState, modifier = Modifier.rotaryScroll(listState)) {
            item { ListHeader { Text("Course") } }
            items(courses) { c ->
                val isCurrent = c.file == selectedFile
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (isCurrent) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    onClick = { onSelect(c.file) },
                    label = { Text(c.name) },
                    secondaryLabel = { Text(if (isCurrent) "Selected ✓" else c.clubName) },
                )
            }
        }
    }
}
