package br.com.weslleycampos.chat.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.chat_empty_meta
import br.com.weslleycampos.chat.feature.chat.resources.chat_empty_title
import br.com.weslleycampos.chat.feature.chat.resources.chat_recent
import br.com.weslleycampos.chat.feature.chat.resources.chat_tip_message
import br.com.weslleycampos.chat.feature.chat.resources.chat_tip_mic
import br.com.weslleycampos.chat.feature.chat.resources.chat_tip_model
import br.com.weslleycampos.chat.feature.chat.resources.chat_tip_type
import org.jetbrains.compose.resources.stringResource

/** The empty state's 9 px model dot. */
private val ModelDotSize = 9.dp

/** The tips' `line-height:1.9`. */
private val TipLineHeight = 1.9.em

/** The empty state (frame lines 120–140), centred in the thread and scrolling once the keyboard leaves it too short. */
@Composable
internal fun ChatEmptyState(
    model: ChatModel,
    recent: List<RecentConversationUi>,
    onOpenRecent: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                // The prototype's `padding:0 24px`.
                .padding(horizontal = ChatTheme.spacing.xxxLarge + ChatTheme.spacing.xxxSmall),
            verticalArrangement = Arrangement.spacedBy(ChatTheme.spacing.xxxLarge, Alignment.CenterVertically),
        ) {
            ModelHeader(model = model)
            Tips()
            if (recent.isNotEmpty()) Recent(recent = recent, onOpenRecent = onOpenRecent)
        }
    }
}

@Composable
private fun ModelHeader(model: ChatModel) {
    Column {
        Text(
            text = stringResource(FeatureChatRes.string.chat_empty_title),
            style = ChatTheme.typography.labelMedium,
            color = ChatTheme.colors.text.tertiary,
        )
        Row(
            modifier = Modifier.padding(top = ChatTheme.spacing.xxSmall),
            horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(size = ModelDotSize)
            Text(
                text = model.name,
                modifier = Modifier.semantics { heading() },
                style = ChatTheme.typography.displayMedium,
                color = ChatTheme.colors.text.primary,
            )
        }
        Text(
            text = stringResource(
                FeatureChatRes.string.chat_empty_meta,
                model.provider,
                model.contextWindow,
                model.cost,
            ),
            modifier = Modifier.padding(top = ChatTheme.spacing.xxSmall),
            style = ChatTheme.typography.labelMedium,
            color = ChatTheme.colors.text.secondary,
        )
    }
}

@Composable
private fun Tips() {
    val tips = listOf(
        stringResource(FeatureChatRes.string.chat_tip_type),
        stringResource(FeatureChatRes.string.chat_tip_mic),
        stringResource(FeatureChatRes.string.chat_tip_model),
        stringResource(FeatureChatRes.string.chat_tip_message),
    )
    val emphasis = ChatTheme.colors.text.secondary
    Column(verticalArrangement = Arrangement.spacedBy(ChatTheme.spacing.xxxSmall)) {
        for (tip in tips) {
            val text = remember(tip, emphasis) {
                buildAnnotatedString {
                    for ((run, emphasised) in tipRuns(tip)) {
                        if (emphasised) {
                            withStyle(SpanStyle(color = emphasis)) { append(run) }
                        } else {
                            append(run)
                        }
                    }
                }
            }
            Text(
                text = text,
                style = ChatTheme.typography.labelMedium.copy(lineHeight = TipLineHeight),
                color = ChatTheme.colors.text.tertiary,
            )
        }
    }
}

@Composable
private fun Recent(recent: List<RecentConversationUi>, onOpenRecent: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ChatTheme.spacing.xSmall)) {
        Text(
            text = stringResource(FeatureChatRes.string.chat_recent),
            style = ChatTheme.typography.labelSmall,
            color = ChatTheme.colors.text.tertiary,
        )
        for (conversation in recent) {
            RecentRow(conversation = conversation, onClick = { onOpenRecent(conversation.id) })
        }
    }
}

/** A "recent" row: the title, in the accent while pressed, and its time, over a hairline. */
@Composable
private fun RecentRow(conversation: RecentConversationUi, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The prototype's rows are 8 + 20 + 8; 44 keeps them a comfortable touch target.
            .heightIn(min = ChatTheme.sizes.xxLarge)
            .topHairline(ChatTheme.colors.border.primary)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(vertical = ChatTheme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = conversation.title,
            modifier = Modifier.weight(1f),
            style = ChatTheme.typography.bodyMedium,
            color = if (pressed) ChatTheme.colors.accent.primary else ChatTheme.colors.text.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = conversation.time,
            style = ChatTheme.typography.labelSmall,
            color = ChatTheme.colors.text.tertiary,
        )
    }
}
