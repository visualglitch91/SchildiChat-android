/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package im.vector.app.features.home.room.list.actions

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.airbnb.epoxy.TypedEpoxyController
import im.vector.app.R
import im.vector.app.core.epoxy.bottomSheetDividerItem
import im.vector.app.core.epoxy.bottomsheet.bottomSheetActionItem
import im.vector.app.core.epoxy.bottomsheet.bottomSheetRoomPreviewItem
import im.vector.app.core.epoxy.profiles.notifications.bottomSheetRadioButtonItem
import im.vector.app.core.epoxy.profiles.notifications.radioButtonItem
import im.vector.app.core.resources.ColorProvider
import im.vector.app.core.resources.StringProvider
import im.vector.app.features.home.AvatarRenderer
import im.vector.app.features.roomprofile.notifications.notificationOptions
import im.vector.app.features.roomprofile.notifications.notificationStateMapped
import im.vector.app.features.settings.VectorPreferences
import im.vector.lib.strings.CommonStrings
import org.matrix.android.sdk.api.session.room.notification.RoomNotificationState
import org.matrix.android.sdk.api.util.toMatrixItem
import javax.inject.Inject

/**
 * Epoxy controller for room list actions.
 */
class RoomListQuickActionsEpoxyController @Inject constructor(
        private val avatarRenderer: AvatarRenderer,
        private val colorProvider: ColorProvider,
        private val stringProvider: StringProvider,
        private val vectorPreferences: VectorPreferences
) : TypedEpoxyController<RoomListQuickActionViewState>() {

    var listener: Listener? = null

    override fun buildModels(state: RoomListQuickActionViewState) {
        val notificationViewState = state.notificationSettingsViewState
        val roomSummary = notificationViewState.roomSummary() ?: return
        val host = this
        // Preview, favorite, settings
        bottomSheetRoomPreviewItem {
            id("room_preview")
            avatarRenderer(host.avatarRenderer)
            matrixItem(roomSummary.toMatrixItem())
            stringProvider(host.stringProvider)
            colorProvider(host.colorProvider)
            izLowPriority(roomSummary.isLowPriority)
            izFavorite(roomSummary.isFavorite)
            settingsClickListener { host.listener?.didSelectMenuAction(RoomListQuickActionsSharedAction.Settings(roomSummary.roomId)) }
            favoriteClickListener { host.listener?.didSelectMenuAction(RoomListQuickActionsSharedAction.Favorite(roomSummary.roomId)) }
            lowPriorityClickListener { host.listener?.didSelectMenuAction(RoomListQuickActionsSharedAction.LowPriority(roomSummary.roomId)) }
        }

        if (vectorPreferences.labAllowMarkUnread()) {
            // Mark read/unread
            bottomSheetDividerItem {
                id("mark_unread_separator")
            }
            if (roomSummary.scIsUnread()) {
                RoomListQuickActionsSharedAction.MarkRead(roomSummary.roomId).toBottomSheetItem("action_mark_read")
            } else {
                RoomListQuickActionsSharedAction.MarkUnread(roomSummary.roomId).toBottomSheetItem("action_mark_unread")
            }
        }

        if (vectorPreferences.showOpenAnonymous()) {
            RoomListQuickActionsSharedAction.OpenAnonymous(roomSummary.roomId).toBottomSheetItem("action_open_anonymous")
        }

        if (vectorPreferences.loadRoomAtFirstUnread()) {
            // TODO can we check if position of roomSummary.readMarkerId is below or equal to
            // roomSummary.latestPreviewableOriginalContentEvent, and hide this otherwise?
            RoomListQuickActionsSharedAction.OpenAtBottom(roomSummary.roomId).toBottomSheetItem("action_open_at_bottom")
        }

        // Notifications
        bottomSheetDividerItem {
            id("notifications_separator")
        }

        notificationViewState.notificationOptions.forEach { notificationState ->
            val title = titleForNotificationState(notificationState)
            val icon = iconForNotificationState(notificationState)
            bottomSheetRadioButtonItem {
                id(notificationState.name)
                titleRes(title)
                iconRes(icon)
                selected(notificationViewState.notificationStateMapped() == notificationState)
                listener {
                    host.listener?.didSelectRoomNotificationState(notificationState)
                }
            }
        }

        RoomListQuickActionsSharedAction.Leave(roomSummary.roomId, showIcon = true).toBottomSheetItem()
    }

    @StringRes
    private fun titleForNotificationState(notificationState: RoomNotificationState): Int? = when (notificationState) {
        RoomNotificationState.ALL_MESSAGES       -> CommonStrings.room_settings_default // SC addition to allow this again
        RoomNotificationState.ALL_MESSAGES_NOISY -> CommonStrings.room_settings_all_messages
        RoomNotificationState.MENTIONS_ONLY -> CommonStrings.room_settings_mention_and_keyword_only
        RoomNotificationState.MUTE -> CommonStrings.room_settings_none
        else -> null
    }

    @DrawableRes
    private fun iconForNotificationState(notificationState: RoomNotificationState): Int? = when (notificationState) {
        // Yeah, ALL_MESSAGES and ALL_MESSAGES_NOISY is confusing, blame upstream.
        // RoomNotificationState.ALL_MESSAGES_NOISY = explicit push rule to notify for all
        // RoomNotificationState.ALL_MESSAGES = no explicit push rule, follow default
        // To follow desktops icons, we also need to exchange both icons...
        RoomNotificationState.ALL_MESSAGES       -> R.drawable.ic_room_actions_notifications_all_noisy // default
        RoomNotificationState.ALL_MESSAGES_NOISY -> R.drawable.ic_room_actions_notifications_all // actually all
        RoomNotificationState.MENTIONS_ONLY      -> R.drawable.ic_room_actions_notifications_mentions
        RoomNotificationState.MUTE               -> R.drawable.ic_room_actions_notifications_mutes
        else -> null
    }

    private fun RoomListQuickActionsSharedAction.Leave.toBottomSheetItem() {
        toBottomSheetItem("action_leave")
    }

    private fun RoomListQuickActionsSharedAction.toBottomSheetItem(id: String, selected: Boolean = false) {
        val host = this@RoomListQuickActionsEpoxyController
        return bottomSheetActionItem {
            id(id)
            selected(selected)
            if (iconResId != null) {
                iconRes(iconResId)
            } else {
                showIcon(false)
            }
            textRes(titleRes)
            destructive(this@toBottomSheetItem.destructive)
            listener { host.listener?.didSelectMenuAction(this@toBottomSheetItem) }
        }
    }

    interface Listener {
        fun didSelectMenuAction(quickAction: RoomListQuickActionsSharedAction)
        fun didSelectRoomNotificationState(roomNotificationState: RoomNotificationState)
    }
}
