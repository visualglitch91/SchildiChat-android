package im.vector.app.features.home.room.detail.timeline.url

import android.view.View
import androidx.core.view.isVisible
import im.vector.app.features.home.room.detail.timeline.TimelineEventController
import im.vector.app.features.home.room.detail.timeline.view.TimelineMessageLayoutRenderer
import im.vector.app.features.media.ImageContentRenderer

interface AbstractPreviewUrlView: TimelineMessageLayoutRenderer {
    var isVisible: Boolean
        get() = (this as View).isVisible
        set(value) { (this as View).isVisible = value }

    var delegate: TimelineEventController.PreviewUrlCallback?
    fun render(newState: PreviewUrlUiState,
               imageContentRenderer: ImageContentRenderer,
               force: Boolean = false)
}
