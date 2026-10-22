@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.weslleycampos.chat.feature.chat.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.core.ui.components.ChatBottomSheet
import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import br.com.weslleycampos.chat.data.chat.ModelRepository
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.PersonaPresets
import br.com.weslleycampos.chat.feature.chat.Dot
import br.com.weslleycampos.chat.feature.chat.HairlineWidth
import br.com.weslleycampos.chat.feature.chat.palette
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.model_picker_all_models
import br.com.weslleycampos.chat.feature.chat.resources.model_picker_favorite
import br.com.weslleycampos.chat.feature.chat.resources.model_picker_favorites
import br.com.weslleycampos.chat.feature.chat.resources.model_picker_new_persona
import br.com.weslleycampos.chat.feature.chat.resources.model_picker_personas
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** The sheet's `max-height:82%`. */
private const val SheetMaxHeightFraction = 0.82f

/** A row's `min-height:52px`. */
private val RowMinHeight = 52.dp

/** A row's 8 px dot. */
private val RowDotSize = 8.dp

/** The mark's `width:16px`. */
private val MarkWidth = 16.dp

/** "+ new persona"'s `+`, in an 8 px column. */
private val NewPersonaGlyphWidth = 8.dp

/** The star's `font-size:13px`. */
private val StarFontSize = 13.sp

private const val Star = "★"
private const val EmptyStar = "☆"
private const val Plus = "+"

/**
 * The Model picker frame (frames.md §2.7) in a [ChatBottomSheet]: the grabber, the title, then
 * "favorites", "personas" with "+ new persona" (the chat sheet only) and "all models", scrolling
 * within 82 % of the height the sheet is offered. It shows while [open], slides up when it opens and
 * slides away when it closes, a pick included; a tap on the scrim, a drag down or Back asks
 * [onDismissRequest] to close it.
 *
 * A row's tap is [onPick] with the row's [ModelPickerRow.choice]; the star of an "all models" row
 * is a tap target of its own, [onToggleFavorite], which keeps the sheet open. The caller decides
 * what a pick does: the chat chooses it for the open conversation or keeps it for the new chat
 * (`ChatViewModel.onPick`), Settings' "Default model" (row 10) passes it to
 * `ModelRepository.setDefaultModel`. Either way the caller closes the sheet by setting [open] to
 * `false`. Favorites are `ModelRepository.setFavorite(model, model !in favorites)`.
 *
 * [headerAction] sits at the end of the header, opposite the title: row 7's compare button.
 * The mark and nothing else is in the accent, so wrap the sheet in the `ChatTheme` of what answers.
 */
@Composable
fun ModelPickerSheet(
    open: Boolean,
    state: ModelPickerUiState,
    onDismissRequest: () -> Unit,
    onPick: (ModelChoice) -> Unit,
    onToggleFavorite: (ChatModel) -> Unit,
    modifier: Modifier = Modifier,
    headerAction: @Composable () -> Unit = {},
) {
    // Expanded or hidden: the frame has no half-open sheet, so a long list opens whole.
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    var shown by remember { mutableStateOf(open) }
    LaunchedEffect(open) {
        if (open) {
            // Reopened while it was sliding away: back up. A sheet entering composition shows itself.
            if (shown && sheetState.isVisible) sheetState.show()
            shown = true
        } else if (shown) {
            sheetState.hide()
            shown = false
        }
    }
    if (!shown) return
    ChatBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier, sheetState = sheetState) {
        ModelPickerContent(
            state = state,
            onPick = onPick,
            onToggleFavorite = onToggleFavorite,
            modifier = Modifier.maxHeightFraction(SheetMaxHeightFraction),
            headerAction = headerAction,
        )
    }
}

/**
 * What [ModelPickerSheet] shows under its grabber: the header and the scrolling list. Give it a
 * bounded height; the list takes what the header leaves and scrolls.
 */
@Composable
fun ModelPickerContent(
    state: ModelPickerUiState,
    onPick: (ModelChoice) -> Unit,
    onToggleFavorite: (ChatModel) -> Unit,
    modifier: Modifier = Modifier,
    headerAction: @Composable () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Header(title = stringResource(state.variant.title), action = headerAction)
        Column(
            modifier = Modifier
                .weight(weight = 1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel(
                text = stringResource(FeatureChatRes.string.model_picker_favorites),
                top = ChatTheme.spacing.xSmall,
            )
            Rows(rows = state.favorites, onPick = onPick, onToggleFavorite = onToggleFavorite)
            if (state.showsPersonas) {
                SectionLabel(
                    text = stringResource(FeatureChatRes.string.model_picker_personas),
                    top = ChatTheme.spacing.xLarge,
                )
                Rows(rows = state.personas, onPick = onPick, onToggleFavorite = onToggleFavorite)
                NewPersonaRow()
            }
            SectionLabel(
                text = stringResource(FeatureChatRes.string.model_picker_all_models),
                top = ChatTheme.spacing.xLarge,
            )
            Rows(rows = state.allModels, onPick = onPick, onToggleFavorite = onToggleFavorite)
            Spacer(modifier = Modifier.height(ChatTheme.spacing.small))
        }
    }
}

/** The title, 16 px 600, and the compare slot; as tall as row 7's 30 px compare button. */
@Composable
private fun Header(title: String, action: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ChatTheme.spacing.xxLarge,
                end = ChatTheme.spacing.xxLarge,
                top = ChatTheme.spacing.xSmall,
                bottom = ChatTheme.spacing.small,
            )
            .heightIn(min = ChatTheme.sizes.large),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = ChatTheme.typography.headlineMedium,
            color = ChatTheme.colors.text.primary,
        )
        action()
    }
}

/** "favorites", "personas", "all models": mono 11 `--fg3`. */
@Composable
private fun SectionLabel(text: String, top: Dp) {
    Text(
        text = text,
        modifier = Modifier
            .padding(
                start = ChatTheme.spacing.xxLarge,
                end = ChatTheme.spacing.xxLarge,
                top = top,
                bottom = ChatTheme.spacing.xxxSmall,
            )
            .semantics { heading() },
        style = ChatTheme.typography.labelSmall,
        color = ChatTheme.colors.text.tertiary,
    )
}

@Composable
private fun Rows(rows: List<ModelPickerRow>, onPick: (ModelChoice) -> Unit, onToggleFavorite: (ChatModel) -> Unit) {
    for (row in rows) {
        key(row.key) {
            PickerRow(
                row = row,
                onPick = { onPick(row.choice) },
                onToggleFavorite = { onToggleFavorite(row.choice.model) },
            )
        }
    }
}

/**
 * A model or persona row: the dot, the name over its meta, the star on "all models" rows, and the
 * mark. The row's button lies under its content rather than around it, so the star is a sibling
 * tap target, not a button nested in the row's: a tap on the star toggles the favorite, anywhere
 * else picks. `--bg2` while pressed, the prototype's hover.
 */
@Composable
private fun PickerRow(row: ModelPickerRow, onPick: () -> Unit, onToggleFavorite: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val label = "${row.name}, ${row.meta}"
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = RowMinHeight)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(if (pressed) ChatTheme.colors.surface.secondary else Color.Transparent)
                .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onPick)
                .semantics {
                    contentDescription = label
                    selected = row.mark == ModelPickerMark.Active
                },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RowMinHeight)
                .padding(horizontal = ChatTheme.spacing.xxLarge, vertical = ChatTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(size = RowDotSize, color = row.palette.colors(ChatTheme.colors.isDark).accent.primary)
            Spacer(modifier = Modifier.width(ChatTheme.spacing.large))
            Column(modifier = Modifier.weight(1f).clearAndSetSemantics {}) {
                Text(text = row.name, style = ChatTheme.typography.bodyLarge, color = ChatTheme.colors.text.primary)
                Text(
                    text = row.meta,
                    style = ChatTheme.typography.labelSmall,
                    color = ChatTheme.colors.text.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val favorite = row.favorite
            if (favorite == null) {
                Spacer(modifier = Modifier.width(ChatTheme.spacing.large))
            } else {
                // 6 + 44 + 6 puts the star where the prototype's 12 + 32 + 12 does, with a 44 dp target.
                Spacer(modifier = Modifier.width(ChatTheme.spacing.xSmall))
                StarButton(favorite = favorite, onToggle = onToggleFavorite)
                Spacer(modifier = Modifier.width(ChatTheme.spacing.xSmall))
            }
            Text(
                text = row.mark?.glyph.orEmpty(),
                modifier = Modifier.width(MarkWidth).clearAndSetSemantics {},
                style = ChatTheme.typography.labelMedium,
                color = ChatTheme.colors.accent.primary,
                textAlign = TextAlign.End,
            )
        }
    }
}

/** "★" / "☆", mono 13 `--fg3`: a favorite toggle with a 44 dp target that picks nothing. */
@Composable
private fun StarButton(favorite: Boolean, onToggle: () -> Unit) {
    val description = stringResource(FeatureChatRes.string.model_picker_favorite)
    Box(
        modifier = Modifier
            .size(ChatTheme.sizes.xxLarge)
            .toggleable(
                value = favorite,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (favorite) Star else EmptyStar,
            modifier = Modifier.clearAndSetSemantics {},
            style = ChatTheme.typography.labelMedium.copy(fontSize = StarFontSize),
            color = ChatTheme.colors.text.tertiary,
        )
    }
}

/**
 * "+ new persona": drawn where the frame has it but inert, since no persona editor exists, and in
 * `--fg3` rather than the frame's `--fg2` so it reads as unavailable. It says so to screen readers.
 */
@Composable
private fun NewPersonaRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ChatTheme.sizes.xxLarge)
            .padding(horizontal = ChatTheme.spacing.xxLarge)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                disabled()
            },
        horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = Plus,
            modifier = Modifier.width(NewPersonaGlyphWidth).clearAndSetSemantics {},
            style = ChatTheme.typography.labelMedium,
            color = ChatTheme.colors.text.tertiary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(FeatureChatRes.string.model_picker_new_persona),
            style = ChatTheme.typography.labelMedium,
            color = ChatTheme.colors.text.tertiary,
        )
    }
}

/** At most [fraction] of the height the parent offers, as CSS `max-height` in percent. */
private fun Modifier.maxHeightFraction(fraction: Float): Modifier = layout { measurable, constraints ->
    val maxHeight = if (constraints.hasBoundedHeight) {
        (constraints.maxHeight * fraction).roundToInt().coerceAtLeast(constraints.minHeight)
    } else {
        constraints.maxHeight
    }
    val placeable = measurable.measure(constraints.copy(maxHeight = maxHeight))
    layout(placeable.width, placeable.height) { placeable.place(x = 0, y = 0) }
}

private val PreviewFavorites = ModelRepository.DEFAULT_FAVORITE_IDS.map(ModelCatalogue::get)
private val PreviewReviewer = PersonaPresets.personas.first()

data class ModelPickerPreviewCase(
    val name: String,
    val state: ModelPickerUiState,
    val palette: ChatPalette,
    val darkTheme: Boolean,
) {
    val displayName: String get() = "$name · ${if (darkTheme) "Dark" else "Light"}"
}

/**
 * Every state of the Model picker frame, in light and dark, each in the accent of what answers:
 * the chat sheet on a model, on a persona and with no favorites, and Settings' default sheet.
 */
class ModelPickerPreviewParameterProvider : PreviewParameterProvider<ModelPickerPreviewCase> {
    private val gpt5 = ModelCatalogue["gpt5"]
    private val states = listOf(
        "Chat" to ModelChoice(ModelCatalogue.default),
        "Chat · persona" to ModelChoice(PreviewReviewer),
        "Chat · no favorites" to ModelChoice(gpt5),
    )

    private val cases = states.map { (name, active) ->
        val favorites = if (name == "Chat · no favorites") emptyList() else PreviewFavorites
        Triple(
            name,
            modelPickerUiState(
                variant = ModelPickerVariant.Chat,
                favorites = favorites,
                personas = PersonaPresets.personas,
                active = active,
            ),
            active.model.palette,
        )
    } + Triple(
        "Default",
        modelPickerUiState(
            variant = ModelPickerVariant.Default,
            favorites = PreviewFavorites,
            personas = PersonaPresets.personas,
            active = ModelChoice(gpt5),
        ),
        gpt5.palette,
    )

    override val values: Sequence<ModelPickerPreviewCase> = sequence {
        for (dark in listOf(false, true)) {
            for ((name, state, palette) in cases) {
                yield(ModelPickerPreviewCase(name = name, state = state, palette = palette, darkTheme = dark))
            }
        }
    }

    override fun getDisplayName(index: Int): String = values.elementAt(index).displayName
}

/**
 * The sheet as [ChatBottomSheet] draws it over the scrim, grabber included, tall enough to show
 * every row: a static preview cannot open the modal sheet itself.
 */
@Preview(name = "Model picker", widthDp = 402, heightDp = 1000)
@Composable
private fun ModelPickerPreview(
    @PreviewParameter(ModelPickerPreviewParameterProvider::class) case: ModelPickerPreviewCase,
) {
    ChatTheme(palette = case.palette, darkTheme = case.darkTheme) {
        val shape = ChatTheme.shapes.sheet
        Box(modifier = Modifier.fillMaxSize().background(ChatTheme.colors.surface.primary)) {
            Box(modifier = Modifier.matchParentSize().background(ChatTheme.colors.surface.scrim))
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(color = ChatTheme.colors.surface.primary, shape = shape)
                    .border(width = HairlineWidth, color = ChatTheme.colors.border.primary, shape = shape),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = ChatTheme.spacing.small, bottom = ChatTheme.spacing.xxxSmall)
                        .size(width = ChatTheme.sizes.xLarge, height = ChatTheme.sizes.xxxSmall)
                        .background(color = ChatTheme.colors.surface.tertiary, shape = ChatTheme.shapes.pill),
                )
                ModelPickerContent(state = case.state, onPick = {}, onToggleFavorite = {})
            }
        }
    }
}
