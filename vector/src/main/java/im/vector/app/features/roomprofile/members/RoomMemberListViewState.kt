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

package im.vector.app.features.roomprofile.members

import androidx.annotation.StringRes
import com.airbnb.mvrx.Async
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.Uninitialized
import im.vector.app.core.platform.GenericIdArgs
import im.vector.app.features.roomprofile.RoomProfileArgs
import im.vector.lib.strings.CommonStrings
import org.matrix.android.sdk.api.session.crypto.model.UserVerificationLevel
import org.matrix.android.sdk.api.session.events.model.Event
import org.matrix.android.sdk.api.session.room.model.RoomMemberSummary
import org.matrix.android.sdk.api.session.room.model.RoomSummary

data class RoomMemberListViewState(
        val roomId: String,
        val roomSummary: Async<RoomSummary> = Uninitialized,
        val roomMemberSummaries: Async<RoomMemberSummariesWithPower> = Uninitialized,
        val areAllMembersLoaded: Boolean = false,
        val ignoredUserIds: List<String> = emptyList(),
        val filter: String = "",
        val threePidInvites: Async<List<Event>> = Uninitialized,
        val trustLevelMap: Async<Map<String, UserVerificationLevel>> = Uninitialized,
        val actionsPermissions: ActionPermissions = ActionPermissions()
) : MavericksState {

    constructor(args: RoomProfileArgs) : this(roomId = args.roomId)

    constructor(args: GenericIdArgs) : this(roomId = args.id)
}

data class ActionPermissions(
        val canInvite: Boolean = false,
        val canRevokeThreePidInvite: Boolean = false
)

typealias RoomMemberSummaries = List<Pair<RoomMemberListCategories, List<RoomMemberSummary>>>
typealias RoomMemberSummariesWithPower = List<Pair<RoomMemberListCategories, List<RoomMemberListViewModel.RoomMemberSummaryWithPower>>>

enum class RoomMemberListCategories(@StringRes val titleRes: Int) {
    ADMIN(CommonStrings.room_member_power_level_admins),
    MODERATOR(CommonStrings.room_member_power_level_moderators),
    CUSTOM(CommonStrings.room_member_power_level_custom),
    INVITE(CommonStrings.room_member_power_level_invites),
    USER(CommonStrings.room_member_power_level_users),

    // Singular variants
    SG_ADMIN(CommonStrings.power_level_admin),
    SG_MODERATOR(CommonStrings.power_level_moderator),
    SG_CUSTOM(CommonStrings.power_level_custom_no_value),
    SG_USER(CommonStrings.power_level_default),
    // Header for unified members
    MEMBER(CommonStrings.room_member_power_level_users)
}
