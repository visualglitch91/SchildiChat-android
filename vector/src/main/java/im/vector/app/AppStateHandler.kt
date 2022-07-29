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

package im.vector.app

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import arrow.core.Option
import arrow.core.getOrElse
import de.spiritcroc.matrixsdk.util.DbgUtil
import im.vector.app.core.di.ActiveSessionHolder
import im.vector.app.core.utils.BehaviorDataSource
import im.vector.app.features.analytics.AnalyticsTracker
import im.vector.app.features.analytics.plan.UserProperties
import im.vector.app.features.session.coroutineScope
import im.vector.app.features.ui.UiStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.matrix.android.sdk.api.extensions.tryOrNull
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.getRoom
import org.matrix.android.sdk.api.session.getRoomSummary
import org.matrix.android.sdk.api.session.room.model.RoomSummary
import org.matrix.android.sdk.api.session.sync.SyncRequestState
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class SelectSpaceFrom {
    // Initialized / uiStateRepository
    INIT,
    // Swiped in home pager
    SWIPE,
    // Persisted after swipe in home pager
    PERSIST_SWIPE,
    // Selected from non-pager UI
    SELECT,
}

/**
 * This class handles the global app state.
 * It requires to be added to ProcessLifecycleOwner.get().lifecycle
 */
// TODO Keep this class for now, will maybe be used fro Space
@Singleton
class AppStateHandler @Inject constructor(
        private val sessionDataSource: ActiveSessionDataSource,
        private val uiStateRepository: UiStateRepository,
        private val activeSessionHolder: ActiveSessionHolder,
        private val analyticsTracker: AnalyticsTracker
) : DefaultLifecycleObserver {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    //private val selectedSpaceDataSource = BehaviorDataSource<Option<RoomSummary>>(Option.empty())

    // SC: Call it different then the upstream one so we don't forget adding `first` to upstream's logic.
    private val selectedSpaceDataSourceSc = BehaviorDataSource<Option<Pair<RoomSummary?, SelectSpaceFrom>>>(Option.empty())

    //val selectedSpaceFlow = selectedSpaceDataSource.stream()

    val selectedSpaceFlow = selectedSpaceDataSourceSc.stream().map { it.map { it.first } }
    val selectedSpaceFlowIgnoreSwipe = selectedSpaceDataSourceSc.stream()
            .filter { it.getOrElse{ null }?.second != SelectSpaceFrom.SWIPE }

    private val spaceBackstack = ArrayDeque<String?>()

    fun getCurrentSpace(): RoomSummary? {
        return selectedSpaceDataSourceSc.currentValue?.orNull()?.first?.let { spaceSummary ->
            activeSessionHolder.getSafeActiveSession()?.roomService()?.getRoomSummary(spaceSummary.roomId)
        }
    }

    fun setCurrentSpace(spaceId: String?, session: Session? = null, persistNow: Boolean = false, isForwardNavigation: Boolean = true, from: SelectSpaceFrom = SelectSpaceFrom.SELECT) {
        val currentSpace = selectedSpaceDataSourceSc.currentValue?.orNull()?.first
        val uSession = session ?: activeSessionHolder.getSafeActiveSession() ?: return
        if (currentSpace != null && spaceId == currentSpace.roomId) return
        val spaceSum = spaceId?.let { uSession.getRoomSummary(spaceId) }

        if (DbgUtil.isDbgEnabled(DbgUtil.DBG_VIEW_PAGER) && from == SelectSpaceFrom.SELECT) {
            // We want a stack trace
            Timber.w(Exception("Home pager: setCurrentSpace/SELECT"))
        }

        if (isForwardNavigation && from in listOf(SelectSpaceFrom.SELECT, SelectSpaceFrom.INIT)) {
            spaceBackstack.addLast(currentSpace?.roomId)
        }

        if (persistNow) {
            uiStateRepository.storeSelectedSpace(spaceSum?.roomId, uSession.sessionId)
        }

        if (spaceSum == null) {
            //selectedSpaceDataSourceSc.post(Option.empty())
            selectedSpaceDataSourceSc.post(Option.just(Pair(null, from)))
        } else {
            selectedSpaceDataSourceSc.post(Option.just(Pair(spaceSum, from)))
        }

        if (spaceId != null) {
            uSession.coroutineScope.launch(Dispatchers.IO) {
                tryOrNull {
                    uSession.getRoom(spaceId)?.membershipService()?.loadRoomMembersIfNeeded()
                }
            }
        }
    }

    private fun observeActiveSession() {
        sessionDataSource.stream()
                .distinctUntilChanged()
                .onEach {
                    // sessionDataSource could already return a session while activeSession holder still returns null
                    it.orNull()?.let { session ->
                        setCurrentSpace(uiStateRepository.getSelectedSpace(session.sessionId), session, from = SelectSpaceFrom.INIT)
                        //observeSyncStatus(session)
                    }
                }
                .launchIn(coroutineScope)
    }

    /*
    private fun observeSyncStatus(session: Session) {
        session.syncService().getSyncRequestStateFlow()
                .filterIsInstance<SyncRequestState.IncrementalSyncDone>()
                .map { session.spaceService().getRootSpaceSummaries().size }
                .distinctUntilChanged()
                .onEach { spacesNumber ->
                    analyticsTracker.updateUserProperties(UserProperties(numSpaces = spacesNumber))
                }.launchIn(session.coroutineScope)
    }
    */

    fun getSpaceBackstack() = spaceBackstack

    fun safeActiveSpaceId(): String? {
        return selectedSpaceDataSourceSc.currentValue?.orNull()?.first?.roomId
    }

    override fun onResume(owner: LifecycleOwner) {
        observeActiveSession()
    }

    override fun onPause(owner: LifecycleOwner) {
        coroutineScope.coroutineContext.cancelChildren()
        val session = activeSessionHolder.getSafeActiveSession() ?: return
        uiStateRepository.storeSelectedSpace(selectedSpaceDataSourceSc.currentValue?.orNull()?.first?.roomId, session.sessionId)
    }

    fun persistSelectedSpace() {
        val currentValue = selectedSpaceDataSourceSc.currentValue?.orNull() ?: return
        val currentMethod = currentValue.first
        val uSession = activeSessionHolder.getSafeActiveSession() ?: return

        // We want to persist it, so we also want to remove the pendingSwipe status
        if (currentValue.second == SelectSpaceFrom.SWIPE) {
            selectedSpaceDataSourceSc.post(Option.just(Pair(currentMethod, SelectSpaceFrom.PERSIST_SWIPE)))
        }

        // Persist it across app restarts
        uiStateRepository.storeSelectedSpace(currentMethod?.roomId, uSession.sessionId)
    }
}
