package im.vector.app.features.themes

import android.content.Context
import android.graphics.Paint
import android.widget.TextView
import androidx.preference.PreferenceManager
import im.vector.app.features.home.room.detail.timeline.item.AnonymousReadReceipt
import org.matrix.android.sdk.api.session.room.send.SendState
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import timber.log.Timber
import javax.inject.Inject

/**
 * Util class for managing themes.
 */
class BubbleThemeUtils @Inject constructor(private val context: Context) {
    companion object {
        const val BUBBLE_STYLE_KEY = "BUBBLE_STYLE_KEY"
        const val BUBBLE_TIME_LOCATION_KEY = "BUBBLE_TIME_LOCATION_KEY"

        const val BUBBLE_STYLE_NONE = "none"
        const val BUBBLE_STYLE_ELEMENT = "element"
        const val BUBBLE_STYLE_START = "start"
        const val BUBBLE_STYLE_BOTH = "both"
        const val BUBBLE_TIME_TOP = "top"
        const val BUBBLE_TIME_BOTTOM = "bottom"

        fun getVisibleAnonymousReadReceipts(readReceipt: AnonymousReadReceipt?, sentByMe: Boolean): AnonymousReadReceipt {
            readReceipt ?: return AnonymousReadReceipt.NONE
            // TODO
            return if (sentByMe && (/*TODO setting?*/ true || readReceipt == AnonymousReadReceipt.PROCESSING)) {
                readReceipt
            } else {
                AnonymousReadReceipt.NONE
            }
        }

        fun anonymousReadReceiptForEvent(event: TimelineEvent): AnonymousReadReceipt {
            return if (event.root.sendState == SendState.SYNCED || event.root.sendState == SendState.SENT) {
                /*if (event.readByOther) {
                    AnonymousReadReceipt.READ
                } else {
                    AnonymousReadReceipt.SENT
                }*/
                AnonymousReadReceipt.NONE
            } else {
                AnonymousReadReceipt.PROCESSING
            }
        }
    }

    // Special case of BUBBLE_STYLE_BOTH, to allow non-bubble items align to the sender either way
    // (not meant for user setting, but internal use)
    //const val BUBBLE_STYLE_BOTH_HIDDEN = "both_hidden"
    // As above, so for single bubbles side
    //const val BUBBLE_STYLE_START_HIDDEN = "start_hidden"

    //private var mBubbleStyle: String = ""
    //private var mBubbleTimeLocation: String = ""

    fun getBubbleStyle(): String {
        val bubbleStyle = PreferenceManager.getDefaultSharedPreferences(context).getString(BUBBLE_STYLE_KEY, BUBBLE_STYLE_BOTH)!!
        if (bubbleStyle !in listOf(BUBBLE_STYLE_NONE, BUBBLE_STYLE_START, BUBBLE_STYLE_BOTH, BUBBLE_STYLE_ELEMENT)) {
            Timber.e("Ignoring invalid bubble style setting: $bubbleStyle")
            // Invalid setting, fallback to default
            return BUBBLE_STYLE_BOTH
        }
        return bubbleStyle
    }

    fun setBubbleStyle(value: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(BUBBLE_STYLE_KEY, value).apply()
    }

    /* SC-TODO
    fun drawsActualBubbles(bubbleStyle: String): Boolean {
        return bubbleStyle == BUBBLE_STYLE_START || bubbleStyle == BUBBLE_STYLE_BOTH
    }

    fun drawsDualSide(bubbleStyle: String): Boolean {
        return bubbleStyle == BUBBLE_STYLE_BOTH || bubbleStyle == BUBBLE_STYLE_BOTH_HIDDEN
    }
     */

    fun forceAlwaysShowTimestamps(bubbleStyle: String): Boolean {
        return isBubbleTimeLocationSettingAllowed(bubbleStyle)
    }

    fun isBubbleTimeLocationSettingAllowed(bubbleStyle: String): Boolean {
        return bubbleStyle == BUBBLE_STYLE_BOTH // SC-TODO? || bubbleStyle == BUBBLE_STYLE_BOTH_HIDDEN
    }

    fun forceAlwaysShowTimestamps(): Boolean {
        return forceAlwaysShowTimestamps(getBubbleStyle())
    }

    fun isBubbleTimeLocationSettingAllowed(): Boolean {
        return isBubbleTimeLocationSettingAllowed(getBubbleStyle())
    }
}

fun guessTextWidth(view: TextView): Float {
    return guessTextWidth(view, view.text)
}

fun guessTextWidth(view: TextView, text: CharSequence): Float {
    return guessTextWidth(view.textSize, text);
}

fun guessTextWidth(textSize: Float, text: CharSequence): Float {
    val paint = Paint()
    paint.textSize = textSize
    return paint.measureText(text.toString())
}
