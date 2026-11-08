package com.editorshub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.editorshub.ui.theme.AppMotion

@Composable
fun PanelSection(title: String, expanded: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(expanded) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = tween(AppMotion.Fast),
        label = "panel-arrow-$title"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .animateContentSize(tween(AppMotion.Normal, easing = AppMotion.Emphasized))
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                Icons.Default.KeyboardArrowDown, null,
                modifier = Modifier.size(18.dp).rotate(arrowRotation)
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(animationSpec = tween(AppMotion.Normal, easing = AppMotion.Emphasized)) +
                fadeIn(animationSpec = tween(AppMotion.Fast)),
            exit = shrinkVertically(animationSpec = tween(AppMotion.Fast, easing = AppMotion.Standard)) +
                fadeOut(animationSpec = tween(AppMotion.Fast))
        ) {
            Column { content() }
        }
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onKeyframe: (() -> Unit)? = null,
    keyed: Boolean = false,
    suffix: String = ""
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            Text("%.2f%s".format(value, suffix), fontSize = 11.sp)
            if (onKeyframe != null) {
                IconButton(onClick = onKeyframe, modifier = Modifier.size(26.dp)) {
                    Text("◆", color = if (keyed) Color(0xFFFBBF24) else MaterialTheme.colorScheme.outline,
                        fontSize = 13.sp)
                }
            }
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth().height(26.dp)
        )
    }
}

@Composable
fun LabeledSelect(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(options.firstOrNull { it.first == value }?.second ?: value, fontSize = 11.sp)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (key, text) ->
                    DropdownMenuItem(text = { Text(text, fontSize = 11.sp) }, onClick = {
                        onChange(key); expanded = false
                    })
                }
            }
        }
    }
}

@Composable
fun ColorSwatchRow(label: String, color: String, onChange: (String) -> Unit) {
    val presets = listOf("#FFFFFFFF", "#FF000000", "#FF8B7BFF", "#FF22D3EE", "#FFF472B6", "#FFFB7185", "#FF4ADE80", "#FFFBBF24")
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            presets.forEach { c ->
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            runCatching { Color(android.graphics.Color.parseColor(c)) }.getOrDefault(Color.Gray),
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (c.equals(color, true)) MaterialTheme.colorScheme.primary else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onChange(c) }
                )
            }
        }
        OutlinedTextField(
            value = color, onValueChange = onChange, singleLine = true,
            modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
        )
    }
}

@Composable
fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { o ->
            FilterChip(
                selected = o == selected,
                onClick = { onSelect(o) },
                label = { Text(o, fontSize = 10.sp) }
            )
        }
    }
}
