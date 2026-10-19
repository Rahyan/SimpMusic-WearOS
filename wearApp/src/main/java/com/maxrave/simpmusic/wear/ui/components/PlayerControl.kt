package com.maxrave.simpmusic.wear.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme

/** Equal targets, icon sizes and foregrounds; selection lives on the tile, not the icon. */
@Composable
fun PlayerControl(icon: ImageVector, description: String, modifier: Modifier = Modifier,
    selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.heightIn(min = 52.dp).clip(shape)
        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .18f) else Color.White.copy(alpha = .06f))
        .border(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .7f) else Color.Transparent, shape)
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
        .semantics { if (selected) stateDescription = "On" }, contentAlignment = Alignment.Center) {
        val foreground = Color.White.copy(alpha = if (enabled) 1f else .35f)
        Icon(icon, description, Modifier.size(24.dp), tint = foreground)
    }
}
