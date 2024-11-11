/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.app.features.home.room.detail

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.Spannable
import android.widget.EditText
import com.otaliastudios.autocomplete.Autocomplete
import com.otaliastudios.autocomplete.AutocompleteCallback
import com.otaliastudios.autocomplete.CharPolicy
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import im.vector.app.core.glide.GlideApp
import im.vector.app.core.glide.GlideRequests
import im.vector.app.features.autocomplete.command.AutocompleteCommandPresenter
import im.vector.app.features.autocomplete.command.CommandAutocompletePolicy
import im.vector.app.features.autocomplete.emoji.AutocompleteEmojiPresenter
import im.vector.app.features.autocomplete.member.AutocompleteMemberItem
import im.vector.app.features.autocomplete.member.AutocompleteMemberPresenter
import im.vector.app.features.autocomplete.room.AutocompleteRoomPresenter
import im.vector.app.features.command.Command
import im.vector.app.features.displayname.getBestName
import im.vector.app.features.home.AvatarRenderer
import im.vector.app.features.html.PillImageSpan
import im.vector.app.features.reactions.data.EmojiItem
import im.vector.app.features.themes.ThemeUtils
import io.element.android.wysiwyg.EditorEditText
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.permalinks.PermalinkService
import org.matrix.android.sdk.api.session.room.model.RoomSummary
import org.matrix.android.sdk.api.util.MatrixItem
import org.matrix.android.sdk.api.util.toEveryoneInRoomMatrixItem
import org.matrix.android.sdk.api.util.toMatrixItem
import org.matrix.android.sdk.api.util.toRoomAliasMatrixItem
import timber.log.Timber

class AutoCompleter @AssistedInject constructor(
        @Assisted val roomId: String,
        @Assisted val isInThreadTimeline: Boolean,
        private val session: Session,
        private val avatarRenderer: AvatarRenderer,
        private val commandAutocompletePolicy: CommandAutocompletePolicy,
        autocompleteCommandPresenterFactory: AutocompleteCommandPresenter.Factory,
        private val autocompleteMemberPresenterFactory: AutocompleteMemberPresenter.Factory,
        private val autocompleteEmojiPresenterFactory: AutocompleteEmojiPresenter.Factory,
        private val autocompleteRoomPresenter: AutocompleteRoomPresenter,
        //private val autocompleteEmojiPresenter: AutocompleteEmojiPresenter,
) {

    private val permalinkService: PermalinkService
        get() = session.permalinkService()

    private lateinit var autocompleteMemberPresenter: AutocompleteMemberPresenter
    private lateinit var autocompleteEmojiPresenter: AutocompleteEmojiPresenter

    @AssistedFactory
    interface Factory {
        fun create(roomId: String, isInThreadTimeline: Boolean): AutoCompleter
    }

    interface Callback {
        fun onAutoCompleteCustomEmote() {}
    }

    private val autocompleteCommandPresenter: AutocompleteCommandPresenter by lazy {
        autocompleteCommandPresenterFactory.create(isInThreadTimeline)
    }

    private var editText: EditText? = null
    private var callback: Callback? = null

    fun enterSpecialMode() {
        commandAutocompletePolicy.enabled = false
    }

    fun exitSpecialMode() {
        commandAutocompletePolicy.enabled = true
    }

    private lateinit var glideRequests: GlideRequests
    private val autocompletes: MutableSet<Autocomplete<*>> = hashSetOf()

    fun setup(editText: EditText, callback: Callback? = null) {
        this.editText = editText
        this.callback = callback
        glideRequests = GlideApp.with(editText)
        val backgroundDrawable = ColorDrawable(ThemeUtils.getColor(editText.context, android.R.attr.colorBackground))
        setupCommands(backgroundDrawable, editText)
        setupMembers(backgroundDrawable, editText)
        setupEmojis(backgroundDrawable, editText)
        setupRooms(backgroundDrawable, editText)
    }

    fun setEnabled(isEnabled: Boolean) =
        autocompletes.forEach {
            if (!isEnabled) { it.dismissPopup() }
            it.setEnabled(isEnabled)
        }

    fun clear() {
        this.editText = null
        this.callback = null
        autocompleteEmojiPresenter.clear()
        autocompleteRoomPresenter.clear()
        autocompleteCommandPresenter.clear()
        autocompleteMemberPresenter.clear()
        autocompletes.forEach {
            it.setEnabled(false)
            it.dismissPopup()
        }
        autocompletes.clear()
    }

    private fun setupCommands(backgroundDrawable: Drawable, editText: EditText) {
        autocompletes += Autocomplete.on<Command>(editText)
                .with(commandAutocompletePolicy)
                .with(autocompleteCommandPresenter)
                .with(ELEVATION_DP)
                .with(backgroundDrawable)
                .with(object : AutocompleteCallback<Command> {
                    override fun onPopupItemClicked(editable: Editable, item: Command): Boolean {
                        if (editText is EditorEditText) {
                            editText.replaceTextSuggestion(item.command)
                        } else {
                            editable.clear()
                            editable
                                    .append(item.command)
                                    .append(" ")
                        }
                        return true
                    }

                    override fun onPopupVisibilityChanged(shown: Boolean) {
                    }
                })
                .build()
    }

    private fun setupMembers(backgroundDrawable: ColorDrawable, editText: EditText) {
        autocompleteMemberPresenter = autocompleteMemberPresenterFactory.create(roomId)
        autocompletes += Autocomplete.on<AutocompleteMemberItem>(editText)
                .with(CharPolicy(TRIGGER_AUTO_COMPLETE_MEMBERS, true))
                .with(autocompleteMemberPresenter)
                .with(ELEVATION_DP)
                .with(backgroundDrawable)
                .with(object : AutocompleteCallback<AutocompleteMemberItem> {
                    override fun onPopupItemClicked(editable: Editable, item: AutocompleteMemberItem): Boolean {
                        val matrixItem = when (item) {
                            is AutocompleteMemberItem.Header -> null // do nothing header is not clickable
                            is AutocompleteMemberItem.RoomMember -> item.roomMemberSummary.toMatrixItem()
                            is AutocompleteMemberItem.Everyone -> item.roomSummary.toEveryoneInRoomMatrixItem()
                        } ?: return false

                        insertMatrixItem(editText, editable, TRIGGER_AUTO_COMPLETE_MEMBERS, matrixItem)

                        return true
                    }

                    override fun onPopupVisibilityChanged(shown: Boolean) {
                    }
                })
                .build()
    }

    private fun setupRooms(backgroundDrawable: ColorDrawable, editText: EditText) {
        autocompletes += Autocomplete.on<RoomSummary>(editText)
                .with(CharPolicy(TRIGGER_AUTO_COMPLETE_ROOMS, true))
                .with(autocompleteRoomPresenter)
                .with(ELEVATION_DP)
                .with(backgroundDrawable)
                .with(object : AutocompleteCallback<RoomSummary> {
                    override fun onPopupItemClicked(editable: Editable, item: RoomSummary): Boolean {
                        insertMatrixItem(editText, editable, TRIGGER_AUTO_COMPLETE_ROOMS, item.toRoomAliasMatrixItem())
                        return true
                    }

                    override fun onPopupVisibilityChanged(shown: Boolean) {
                    }
                })
                .build()
    }

    private fun setupEmojis(backgroundDrawable: Drawable, editText: EditText) {
        autocompleteEmojiPresenter = autocompleteEmojiPresenterFactory.create(roomId)

        // Rich text editor is not yet supported
        if (editText is EditorEditText) return

        autocompletes += Autocomplete.on<EmojiItem>(editText)
                .with(CharPolicy(TRIGGER_AUTO_COMPLETE_EMOJIS, false))
                .with(autocompleteEmojiPresenter)
                .with(ELEVATION_DP)
                .with(backgroundDrawable)
                .with(object : AutocompleteCallback<EmojiItem> {
                    override fun onPopupItemClicked(editable: Editable, item: EmojiItem): Boolean {
                        // Infer that the last ":" before the current cursor position is the original popup trigger
                        var startIndex = editable.subSequence(0, editText.selectionStart).lastIndexOf(":")
                        if (startIndex == -1) {
                            startIndex = 0
                        }

                        // Detect next word separator
                        /*
                        var endIndex = editable.indexOf(" ", startIndex)
                        if (endIndex == -1) {
                            endIndex = editable.length
                        }
                         */
                        val endIndex = editText.selectionEnd

                        // Replace the word by its completion
                        editable.delete(startIndex, endIndex)
                        if (item.emoteImage != null) {
                            // Add emote html
                            val emote = ":${item.name}:"
                            editable.insert(startIndex, emote)

                            // Add span to make it look nice
                            val matrixItem = MatrixItem.EmoteItem(item.emoteImage.url, item.name, item.emoteImage)
                            val span = PillImageSpan(
                                    glideRequests,
                                    avatarRenderer,
                                    editText.context,
                                    matrixItem
                            )
                            span.bind(editText)

                            editable.setSpan(span, startIndex, startIndex + emote.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            callback?.onAutoCompleteCustomEmote()
                        } else {
                            editable.insert(startIndex, item.emoji)
                        }
                        return true
                    }

                    override fun onPopupVisibilityChanged(shown: Boolean) {
                    }
                })
                .build()
    }

    private fun insertMatrixItem(editText: EditText, editable: Editable, firstChar: Char, matrixItem: MatrixItem) =
            if (editText is EditorEditText) {
                insertMatrixItemIntoRichTextEditor(editText, matrixItem)
            } else {
                insertMatrixItemIntoEditable(editText, editable, firstChar, matrixItem)
            }

    private fun insertMatrixItemIntoRichTextEditor(editorEditText: EditorEditText, matrixItem: MatrixItem) {
        if (matrixItem is MatrixItem.EveryoneInRoomItem) {
            editorEditText.replaceTextSuggestion(matrixItem.displayName)
            // Note: not using editorEditText.insertAtRoomMentionAtSuggestion() since we want to keep the existing look and feel of the mention for @room.
            return
        }

        val permalink = permalinkService.createPermalink(matrixItem.id)

        if (permalink == null) {
            Timber.e(NullPointerException("Cannot autocomplete as permalink is null"))
            return
        }

        val linkText = when (matrixItem) {
            is MatrixItem.RoomAliasItem,
            is MatrixItem.RoomItem,
            is MatrixItem.SpaceItem,
            is MatrixItem.UserItem ->
                matrixItem.id
            is MatrixItem.EveryoneInRoomItem,
            is MatrixItem.EmoteItem,
            is MatrixItem.EventItem ->
                matrixItem.getBestName()
        }

        editorEditText.insertMentionAtSuggestion(url = permalink, text = linkText)
    }

    private fun insertMatrixItemIntoEditable(editText: EditText, editable: Editable, firstChar: Char, matrixItem: MatrixItem) {
        // Detect last firstChar and remove it
        var startIndex = editable.lastIndexOf(firstChar)
        if (startIndex == -1) {
            startIndex = 0
        }

        // Detect next word separator
        /*
        var endIndex = editable.indexOfAny(listOf(" ", "\n"), startIndex)
        if (endIndex == -1) {
            endIndex = editable.length
        }
         */
        var endIndex = editText.selectionEnd
        if (endIndex == -1) {
            endIndex = editable.length
        } else if (endIndex < startIndex) {
            val tmp = startIndex
            startIndex = endIndex
            endIndex = tmp
        }

        // Replace the word by its completion
        val displayName = matrixItem.getBestName()

        // Adding trailing space " " or ": " if the user started mention someone
        val displayNameSuffix =
                if (false && matrixItem is MatrixItem.UserItem) {
                    ": "
                } else {
                    " "
                }

        editable.replace(startIndex, endIndex, "$displayName$displayNameSuffix")

        // Add the span
        val span = PillImageSpan(
                glideRequests,
                avatarRenderer,
                editText.context,
                matrixItem
        )
        span.bind(editText)

        editable.setSpan(span, startIndex, startIndex + displayName.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    companion object {
        private const val ELEVATION_DP = 6f
        private const val TRIGGER_AUTO_COMPLETE_MEMBERS = '@'
        private const val TRIGGER_AUTO_COMPLETE_ROOMS = '#'
        private const val TRIGGER_AUTO_COMPLETE_EMOJIS = ':'
    }
}
