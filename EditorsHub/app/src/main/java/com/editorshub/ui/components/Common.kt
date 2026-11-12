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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.editorshub.ui.theme.AppMotion
import com.editorshub.ui.theme.EditorPalette

@Composable
fun PanelSection(title: String, expanded: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(expanded) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = tween(AppMotion.Fast, easing = AppMotion.Emphasized),
        label = "panel-arrow-$title"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 0.dp)
            .animateContentSize(tween(AppMotion.Normal, easing = AppMotion.Emphasized))
            .background(EditorPalette.Surface, RoundedCornerShape(18.dp))
            .border(1.dp, EditorPalette.Outline, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, modifier = Modifier.weight(1f), fontSize = 10.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                letterSpacing = 1.sp, fontWeight = FontWeight.Medium,
                color = EditorPalette.TextMuted)
            Icon(
                Icons.Default.KeyboardArrowDown, null,
                modifier = Modifier.size(16.dp).rotate(arrowRotation),
                tint = EditorPalette.TextMuted
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(animationSpec = tween(AppMotion.Normal, easing = AppMotion.Emphasized)) +
                fadeIn(animationSpec = tween(AppMotion.Fast, easing = AppMotion.Emphasized)),
            exit = shrinkVertically(animationSpec = tween(AppMotion.Fast, easing = AppMotion.Emphasized)) +
                fadeOut(animationSpec = tween(AppMotion.Fast, easing = AppMotion.Emphasized))
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
            Text(label, fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                letterSpacing = 0.4.sp, color = EditorPalette.TextMuted,
                modifier = Modifier.weight(1f))
            Text("%.2f%s".format(value, suffix), fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.Medium, color = EditorPalette.TextPrimary)
            if (onKeyframe != null) {
                IconButton(
                    onClick = onKeyframe,
                    modifier = Modifier.size(26.dp)
                        .semantics { contentDescription = "$label keyframe" }
                ) {
                    Text("◆", color = if (keyed) EditorPalette.Green else EditorPalette.Outline,
                        fontSize = 13.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth().height(26.dp),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = EditorPalette.Purple,
                activeTrackColor = EditorPalette.Purple,
                inactiveTrackColor = EditorPalette.Outline
            )
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
        Text(label, fontSize = 10.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            letterSpacing = 1.sp, fontWeight = FontWeight.Medium,
            color = EditorPalette.TextMuted)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, EditorPalette.Outline),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = EditorPalette.TextPrimary
                )
            ) {
                Text(options.firstOrNull { it.first == value }?.second ?: value, fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(EditorPalette.Surface, RoundedCornerShape(10.dp))
            ) {
                options.forEach { (key, text) ->
                    DropdownMenuItem(text = { Text(text, fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) }, onClick = {
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
        Text(label, fontSize = 10.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            letterSpacing = 1.sp, fontWeight = FontWeight.Medium,
            color = EditorPalette.TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { c ->
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            runCatching { Color(android.graphics.Color.parseColor(c)) }.getOrDefault(androidx.compose.ui.graphics.Color(0xFF8A94A8)),
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.dp,
                            if (c.equals(color, true)) EditorPalette.Purple else EditorPalette.Outline,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onChange(c) }
                )
            }
        }
        OutlinedTextField(
            value = color, onValueChange = onChange, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        )
    }
}

@Composable
fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { o ->
            FilterChip(
                selected = o == selected,
                onClick = { onSelect(o) },
                label = { Text(o, fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = 1.sp) },
                shape = RoundedCornerShape(50),
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    labelColor = EditorPalette.TextMuted,
                    selectedContainerColor = EditorPalette.Purple.copy(alpha = 0.16f),
                    selectedLabelColor = EditorPalette.Purple
                )
            )
        }
    }
}
