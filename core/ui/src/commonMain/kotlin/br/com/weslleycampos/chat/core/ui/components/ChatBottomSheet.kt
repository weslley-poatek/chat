@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.weslleycampos.chat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.weslleycampos.chat.core.ui.ChatTheme

@Composable
fun ChatBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
    showDragHandle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ChatTheme.colors
    val shape = ChatTheme.shapes.sheet

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.topBorder(width = 1.dp, color = colors.border.primary, shape = shape),
        sheetState = sheetState,
        shape = shape,
        containerColor = colors.surface.primary,
        contentColor = colors.text.primary,
        tonalElevation = 0.dp,
        scrimColor = colors.surface.scrim,
        dragHandle = if (showDragHandle) {
            { ChatBottomSheetDragHandle() }
        } else {
            null
        },
    ) {
        ProvideTextStyle(ChatTheme.typography.bodyLarge) { content() }
    }
}

@Composable
private fun ChatBottomSheetDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = ChatTheme.spacing.small, bottom = ChatTheme.spacing.xxxSmall)
            .size(width = ChatTheme.sizes.xLarge, height = ChatTheme.sizes.xxxSmall)
            .background(color = ChatTheme.colors.surface.tertiary, shape = ChatTheme.shapes.pill),
    )
}

private fun Modifier.topBorder(width: Dp, color: Color, shape: CornerBasedShape) = drawWithContent {
    drawContent()
    val stroke = width.toPx()
    val radius = shape.topStart.toPx(shapeSize = size, density = this)
    clipRect(bottom = radius) {
        inset(inset = stroke / 2) {
            drawOutline(
                outline = shape.createOutline(size = size, layoutDirection = layoutDirection, density = this),
                color = color,
                style = Stroke(width = stroke),
            )
        }
    }
}

enum class ChatBottomSheetPreviewState {
    Hidden,
    PartiallyExpanded,
    Expanded,
}

data class ChatBottomSheetPreviewCase(val state: ChatBottomSheetPreviewState, val darkTheme: Boolean) {
    val name: String get() = "$state · ${if (darkTheme) "Dark" else "Light"}"
}

class ChatBottomSheetPreviewParameterProvider : PreviewParameterProvider<ChatBottomSheetPreviewCase> {
    override val values: Sequence<ChatBottomSheetPreviewCase> = sequence {
        for (dark in listOf(false, true)) {
            for (state in ChatBottomSheetPreviewState.entries) {
                yield(ChatBottomSheetPreviewCase(state = state, darkTheme = dark))
            }
        }
    }

    override fun getDisplayName(index: Int): String = values.elementAt(index).name
}

@Preview(name = "Chat bottom sheets", widthDp = 360, heightDp = 640)
@Composable
private fun ChatBottomSheetPreview(
    @PreviewParameter(ChatBottomSheetPreviewParameterProvider::class) case: ChatBottomSheetPreviewCase,
) {
    var visible by remember(case.state) { mutableStateOf(case.state != ChatBottomSheetPreviewState.Hidden) }
    val enabledValues = if (case.state == ChatBottomSheetPreviewState.Expanded) {
        setOf(SheetValue.Hidden, SheetValue.Expanded)
    } else {
        setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded)
    }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = enabledValues)
    ChatTheme(darkTheme = case.darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ChatTheme.colors.surface.primary,
            contentColor = ChatTheme.colors.text.primary,
        ) {
            Column(
                modifier = Modifier.padding(ChatTheme.spacing.xxLarge),
                verticalArrangement = Arrangement.spacedBy(ChatTheme.spacing.small),
            ) {
                Text(text = case.name, style = ChatTheme.typography.labelSmall, color = ChatTheme.colors.text.tertiary)
                ChatButton(onClick = { visible = true }) { Text("Open sheet") }
            }
        }
        if (visible) {
            ChatBottomSheet(onDismissRequest = { visible = false }, sheetState = sheetState) {
                Column(
                    modifier = Modifier.padding(ChatTheme.spacing.xxLarge).heightIn(min = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(ChatTheme.spacing.small),
                ) {
                    Text(text = "Model", style = ChatTheme.typography.headlineMedium)
                    Text(text = "Pick the model for this chat.", color = ChatTheme.colors.text.secondary)
                    ChatButton(onClick = { visible = false }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
            }
        }
    }
}
