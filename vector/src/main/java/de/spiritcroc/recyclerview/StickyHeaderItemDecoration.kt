package de.spiritcroc.recyclerview

/**
 * Source: https://gist.github.com/jonasbark/f1e1373705cfe8f6a7036763f7326f7c
 * Modified to
 * - make isHeader() abstract, so we don't need a predefined list of header ids
 * - not require EpoxyRecyclerView
 * - work with reverse layouts
 * - hide the currently overlaid header for a smoother animation without duplicate headers
 */

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.epoxy.EpoxyController
import org.matrix.android.sdk.api.extensions.orFalse

abstract class StickyHeaderItemDecoration(
    private val epoxyController: EpoxyController,
    private val reverse: Boolean = false
) : RecyclerView.ItemDecoration() {
    private var mStickyHeaderHeight: Int = 0

    private var lastHeaderPos: Int? = null

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)

        if (parent.childCount == 0) {
            return
        }
        val topChild = if (reverse) {
            parent.getChildAt(parent.childCount - 1) ?: return
        } else {
            parent.getChildAt(0) ?: return
        }

        val topChildPosition = parent.getChildAdapterPosition(topChild)
        if (topChildPosition == RecyclerView.NO_POSITION) {
            return
        }

        val headerPos = getHeaderPositionForItem(topChildPosition)
        if (headerPos != RecyclerView.NO_POSITION) {
            val currentHeader = getHeaderViewForItem(headerPos, parent)
            fixLayoutSize(parent, currentHeader)
            val contactPoint = currentHeader.bottom
            val childInContact = getChildInContact(parent, contactPoint, headerPos)

            if (childInContact != null && isHeader(parent.getChildAdapterPosition(childInContact))) {
                updateOverlaidHeaders(parent, headerPos)
                moveHeader(c, currentHeader, childInContact)
                return
            }

            // Un-hide views early, so we don't get flashing headers while scrolling
            val childBellow = getChildInContact(parent, currentHeader.top, headerPos)
            val overlaidHeaderPos: Int? = if (childBellow != childInContact &&
                    childBellow != null &&
                    isHeader(parent.getChildAdapterPosition(childBellow)) &&
                    contactPoint - childBellow.bottom < (childBellow.bottom - childBellow.top)/8
            ) {
                null
            } else {
                headerPos
            }

            updateOverlaidHeaders(parent, overlaidHeaderPos)
            drawHeader(c, currentHeader)
        } else {
            // Show hidden header again
            updateOverlaidHeaders(parent, null)
        }
    }

    private fun updateOverlaidHeaders(parent: RecyclerView, headerPos: Int?) {
        if (lastHeaderPos != headerPos) {
            // Show hidden header again
            lastHeaderPos?.let {
                updateOverlaidHeader(parent, it, false)
            }
            // Remember new hidden header
            lastHeaderPos = if (headerPos?.let { updateOverlaidHeader(parent, it, true) }.orFalse()) {
                headerPos
            } else {
                null
            }
        }
    }

    /**
     * Return true if successfully updated the view.
     * Note: this has some issues when invisible views get recycled, better override this in subclasses.
     */
    open fun updateOverlaidHeader(parent: RecyclerView, position: Int, isCurrentlyOverlaid: Boolean): Boolean {
        val view = parent.findViewHolderForAdapterPosition(position)?.itemView
        if (view != null) {
            view.isVisible = !isCurrentlyOverlaid
            return true
        }
        return false
    }

    open fun getHeaderViewForItem(headerPosition: Int, parent: RecyclerView): View {
        val viewHolder = epoxyController.adapter.onCreateViewHolder(
            parent,
            epoxyController.adapter.getItemViewType(headerPosition)
        )
        epoxyController.adapter.onBindViewHolder(viewHolder, headerPosition)
        return viewHolder.itemView
    }

    private fun drawHeader(c: Canvas, header: View) {
        c.save()
        c.translate(0f, 0f)
        header.draw(c)
        c.restore()
    }

    private fun moveHeader(c: Canvas, currentHeader: View, nextHeader: View) {
        c.save()
        c.translate(0f, (nextHeader.top - currentHeader.height).toFloat())
        currentHeader.draw(c)
        c.restore()
    }

    abstract fun isHeader(itemPosition: Int): Boolean

    private fun getChildInContact(parent: RecyclerView, contactPoint: Int, currentHeaderPos: Int): View? {
        var childInContact: View? = null
        for (i in 0 until parent.childCount) {
            var heightTolerance = 0
            val child = parent.getChildAt(i)

            //measure height tolerance with child if child is another header
            if (currentHeaderPos != i) {
                val isChildHeader = isHeader(parent.getChildAdapterPosition(child))
                if (isChildHeader) {
                    heightTolerance = mStickyHeaderHeight - child.height
                }
            }

            //add heightTolerance if child top be in display area
            val childBottomPosition = if (child.top > 0) {
                child.bottom + heightTolerance
            } else {
                child.bottom
            }

            if (childBottomPosition > contactPoint) {
                if (child.top <= contactPoint) {
                    // This child overlaps the contactPoint
                    childInContact = child
                    break
                }
            }
        }
        return childInContact
    }


    /**
     * This method gets called by [StickyHeaderItemDecoration] to fetch the position of the header item in the adapter
     * that is used for (represents) item at specified position.
     * @param itemPosition int. Adapter's position of the item for which to do the search of the position of the header item.
     * @return int. Position of the header item in the adapter.
     */
    private fun getHeaderPositionForItem(itemPosition: Int): Int {
        var tempPosition = itemPosition
        var headerPosition = RecyclerView.NO_POSITION
        val directionAdd = if (reverse) 1 else -1
        do {
            if (isHeader(tempPosition)) {
                headerPosition = tempPosition
                break
            }
            tempPosition += directionAdd
        } while (tempPosition >= -1 && tempPosition < epoxyController.adapter.itemCount)
        return headerPosition
    }

    /**
     * Properly measures and layouts the top sticky header.
     * @param parent ViewGroup: RecyclerView in this case.
     */
    private fun fixLayoutSize(parent: ViewGroup, view: View) {

        // Specs for parent (RecyclerView)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.UNSPECIFIED)

        // Specs for children (headers)
        val childWidthSpec =
            ViewGroup.getChildMeasureSpec(widthSpec, parent.paddingLeft + parent.paddingRight, view.layoutParams.width)
        val childHeightSpec = ViewGroup.getChildMeasureSpec(
            heightSpec,
            parent.paddingTop + parent.paddingBottom,
            view.layoutParams.height
        )

        view.measure(childWidthSpec, childHeightSpec)

        mStickyHeaderHeight = view.measuredHeight

        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

}
