package com.quotacheck.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quotacheck.app.core.designsystem.PillShape

/**
 * A single connected control: [-] value [+], on one shared pill background. Replaces two
 * separate outlined buttons floating next to plain text, which read as unrelated controls.
 */
@Composable
fun Stepper(
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    decrementEnabled: Boolean = true,
    incrementEnabled: Boolean = true,
    tag: String = "stepper",
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, PillShape)
            .padding(horizontal = 4.dp)
            .semantics { testTag = tag },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onDecrement,
            enabled = decrementEnabled,
            modifier = Modifier.semantics { testTag = "$tag-minus" },
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = MaterialTheme.colorScheme.onSurface)
        }
        Box(
            modifier = Modifier.width(48.dp).semantics { contentDescription = value },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
        IconButton(
            onClick = onIncrement,
            enabled = incrementEnabled,
            modifier = Modifier.semantics { testTag = "$tag-plus" },
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Increase", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}
