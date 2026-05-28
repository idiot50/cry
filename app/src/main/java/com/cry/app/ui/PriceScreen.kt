package com.cry.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cry.app.data.TickerData
import kotlinx.coroutines.delay
import java.text.DecimalFormat

private val Bg = Color(0xFF050505)
private val Faint = Color(0xFF1E1E1E)
private val Mute = Color(0xFF6E6E6E)
private val Neutral = Color(0xFFEDEDED)
private val Up = Color(0xFF6BE3A8)
private val Down = Color(0xFFE36B6B)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PriceScreen(
    pairs: List<String>,
    tickers: Map<String, TickerData>,
    busy: Boolean,
    addError: String?,
    streamError: String?,
    overlayRunning: Boolean,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearError: () -> Unit,
    onToggleOverlay: () -> Unit,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }

    val pairCount = pairs.size
    var lastSeenCount by rememberSaveable { mutableStateOf(pairCount) }
    LaunchedEffect(pairCount) {
        if (pairCount > lastSeenCount && showAdd) {
            showAdd = false
        }
        lastSeenCount = pairCount
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        if (pairs.isEmpty()) {
            Empty()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 32.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(pairs, key = { it }) { symbol ->
                    PriceRow(
                        ticker = tickers[symbol],
                        streamError = streamError,
                        onRemove = { onRemove(symbol) },
                    )
                }
            }
        }

        OverlayToggle(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 24.dp),
            enabled = overlayRunning,
            onClick = onToggleOverlay,
        )

        AddButton(
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            onClick = {
                onClearError()
                showAdd = true
            },
        )

        if (showAdd) {
            AddPairDialog(
                busy = busy,
                error = addError,
                onDismiss = {
                    showAdd = false
                    onClearError()
                },
                onConfirm = onAdd,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PriceRow(
    ticker: TickerData?,
    streamError: String?,
    onRemove: () -> Unit,
) {
    val direction = ticker?.direction ?: 0
    val tickColor = when {
        direction > 0 -> Up
        direction < 0 -> Down
        else -> Neutral
    }

    var pulse by remember { mutableStateOf(false) }
    LaunchedEffect(ticker?.lastUpdate) {
        if (ticker != null) {
            pulse = true
            delay(420)
            pulse = false
        }
    }
    val priceColor by animateColorAsState(
        targetValue = if (pulse) tickColor else Neutral,
        animationSpec = tween(durationMillis = if (pulse) 80 else 600),
        label = "priceColor",
    )

    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { },
                onLongClick = onRemove,
            )
            .padding(horizontal = 28.dp, vertical = 18.dp),
    ) {
        when {
            ticker != null -> {
                Text(
                    text = formatPrice(ticker.price),
                    color = priceColor,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.SansSerif,
                )
                Spacer(Modifier.height(2.dp))
                ChangeLine(ticker.priceChangePercent)
            }
            streamError != null -> {
                Text(
                    text = "no data",
                    color = Down,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.SansSerif,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = streamError,
                    color = Down,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                )
            }
            else -> {
                Text(
                    text = "...",
                    color = Mute,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.SansSerif,
                )
            }
        }
    }
}

@Composable
private fun ChangeLine(change: Double?) {
    val color = when {
        change == null -> Mute
        change > 0 -> Up
        change < 0 -> Down
        else -> Mute
    }
    val text = when {
        change == null -> " "
        change >= 0 -> "+" + DecimalFormat("0.00").format(change) + "%"
        else -> DecimalFormat("0.00").format(change) + "%"
    }
    Text(
        text = text,
        color = color,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.5.sp,
    )
}

@Composable
private fun OverlayToggle(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(if (enabled) Up else Faint, shape = CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "overlay",
            color = if (enabled) Neutral else Mute,
            fontSize = 11.sp,
            letterSpacing = 4.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun AddButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(44.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+",
            color = Mute,
            fontSize = 30.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.SansSerif,
        )
    }
}

@Composable
private fun Empty() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "tap  +",
            color = Mute,
            fontSize = 14.sp,
            letterSpacing = 6.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun AddPairDialog(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focus.requestFocus()
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Color(0xFF0C0C0C),
        titleContentColor = Neutral,
        textContentColor = Mute,
        title = null,
        text = {
            Column {
                Text(
                    text = "symbol",
                    color = Mute,
                    fontSize = 11.sp,
                    letterSpacing = 5.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.uppercase().filter { c -> c.isLetterOrDigit() } },
                    placeholder = {
                        Text(
                            "BTCUSDT",
                            color = Faint,
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Neutral,
                        unfocusedTextColor = Neutral,
                        focusedBorderColor = Neutral,
                        unfocusedBorderColor = Faint,
                        disabledBorderColor = Faint,
                        cursorColor = Neutral,
                    ),
                )
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = error,
                        color = Down,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isNotBlank() && !busy) {
                        keyboard?.hide()
                        onConfirm(text)
                    }
                },
                enabled = !busy && text.isNotBlank(),
            ) {
                Text(
                    text = if (busy) "..." else "add",
                    color = if (busy) Mute else Neutral,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 3.sp,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy,
            ) {
                Text(
                    text = "cancel",
                    color = Mute,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 3.sp,
                )
            }
        },
    )
}

