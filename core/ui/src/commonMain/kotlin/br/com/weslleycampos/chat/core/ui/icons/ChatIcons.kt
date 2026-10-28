package br.com.weslleycampos.chat.core.ui.icons

import br.com.weslleycampos.chat.core.ui.resources.CoreUiRes
import br.com.weslleycampos.chat.core.ui.resources.ic_back
import br.com.weslleycampos.chat.core.ui.resources.ic_close
import br.com.weslleycampos.chat.core.ui.resources.ic_menu
import br.com.weslleycampos.chat.core.ui.resources.ic_mic
import br.com.weslleycampos.chat.core.ui.resources.ic_mic_off
import br.com.weslleycampos.chat.core.ui.resources.ic_search
import br.com.weslleycampos.chat.core.ui.resources.ic_send
import br.com.weslleycampos.chat.core.ui.resources.ic_settings
import org.jetbrains.compose.resources.DrawableResource

/**
 * The prototype's icon catalogue (frames.md §1.7), for other modules: `CoreUiRes` stays internal,
 * so a feature draws an icon with `painterResource(ChatIcons.Menu)`. Each is stroked in black and
 * takes its colour from the tint, `LocalContentColor` inside the `ChatIconButton`s.
 */
object ChatIcons {
    /** The back chevron of History and the chat thread, 20. */
    val Back: DrawableResource get() = CoreUiRes.drawable.ic_back

    /** Voice mode's end "×", 22. */
    val Close: DrawableResource get() = CoreUiRes.drawable.ic_close

    /** Voice's History button, three lines with a short last one, 20. */
    val Menu: DrawableResource get() = CoreUiRes.drawable.ic_menu

    /** The composer's and Voice mode's microphone, 22. */
    val Mic: DrawableResource get() = CoreUiRes.drawable.ic_mic

    /** The muted microphone, 22. */
    val MicOff: DrawableResource get() = CoreUiRes.drawable.ic_mic_off

    /** History's search field, 16. */
    val Search: DrawableResource get() = CoreUiRes.drawable.ic_search

    /** The composer's send arrow, 20. */
    val Send: DrawableResource get() = CoreUiRes.drawable.ic_send

    /** The Settings button of Voice and History, three sliders, 20. */
    val Settings: DrawableResource get() = CoreUiRes.drawable.ic_settings

    /** Every icon above, in the order they are declared. */
    val all: List<DrawableResource>
        get() = listOf(Back, Close, Menu, Mic, MicOff, Search, Send, Settings)
}
