package br.com.weslleycampos.chat.core.ui.icons

import br.com.weslleycampos.chat.core.ui.resources.CoreUiRes
import br.com.weslleycampos.chat.core.ui.resources.ic_add
import br.com.weslleycampos.chat.core.ui.resources.ic_back
import br.com.weslleycampos.chat.core.ui.resources.ic_close
import br.com.weslleycampos.chat.core.ui.resources.ic_memory
import br.com.weslleycampos.chat.core.ui.resources.ic_menu
import br.com.weslleycampos.chat.core.ui.resources.ic_mic
import br.com.weslleycampos.chat.core.ui.resources.ic_mic_off
import br.com.weslleycampos.chat.core.ui.resources.ic_new_chat
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
    /** The composer's attach "+", 22. */
    val Add: DrawableResource get() = CoreUiRes.drawable.ic_add

    /** The back chevron of History, Memory, Settings and Voice, 20. */
    val Back: DrawableResource get() = CoreUiRes.drawable.ic_back

    /** Voice mode's end "×", 22. */
    val Close: DrawableResource get() = CoreUiRes.drawable.ic_close

    /** History's Memory button, 20. */
    val Memory: DrawableResource get() = CoreUiRes.drawable.ic_memory

    /** Chat's History button, three lines with a short last one, 20. */
    val Menu: DrawableResource get() = CoreUiRes.drawable.ic_menu

    /** The composer's and Voice mode's microphone, 22. */
    val Mic: DrawableResource get() = CoreUiRes.drawable.ic_mic

    /** The muted microphone, 22. */
    val MicOff: DrawableResource get() = CoreUiRes.drawable.ic_mic_off

    /** Chat's New chat button, a rounded square with a plus, 20. */
    val NewChat: DrawableResource get() = CoreUiRes.drawable.ic_new_chat

    /** History's search field, 16. */
    val Search: DrawableResource get() = CoreUiRes.drawable.ic_search

    /** The composer's send arrow, 20. */
    val Send: DrawableResource get() = CoreUiRes.drawable.ic_send

    /** History's Settings button, three sliders, 20. */
    val Settings: DrawableResource get() = CoreUiRes.drawable.ic_settings

    /** Every icon above, in the order they are declared. */
    val all: List<DrawableResource>
        get() = listOf(Add, Back, Close, Memory, Menu, Mic, MicOff, NewChat, Search, Send, Settings)
}
