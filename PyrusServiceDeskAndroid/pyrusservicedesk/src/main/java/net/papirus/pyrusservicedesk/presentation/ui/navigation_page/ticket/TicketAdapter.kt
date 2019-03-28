package net.papirus.pyrusservicedesk.presentation.ui.navigation_page.ticket

import android.graphics.Canvas
import android.support.annotation.LayoutRes
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.support.v7.widget.helper.ItemTouchHelper.ACTION_STATE_SWIPE
import android.view.View.*
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.example.pyrusservicedesk.R
import com.squareup.picasso.Picasso
import net.papirus.pyrusservicedesk.presentation.ui.navigation_page.ticket.entries.*
import net.papirus.pyrusservicedesk.presentation.ui.view.CommentView
import net.papirus.pyrusservicedesk.presentation.ui.view.ContentType
import net.papirus.pyrusservicedesk.presentation.ui.view.Status
import net.papirus.pyrusservicedesk.presentation.ui.view.recyclerview.AdapterBase
import net.papirus.pyrusservicedesk.presentation.ui.view.recyclerview.ViewHolderBase
import net.papirus.pyrusservicedesk.sdk.data.Attachment
import net.papirus.pyrusservicedesk.sdk.repositories.general.getAvatarUrl
import net.papirus.pyrusservicedesk.utils.CIRCLE_TRANSFORMATION
import net.papirus.pyrusservicedesk.utils.ConfigUtils
import net.papirus.pyrusservicedesk.utils.canBePreviewed
import net.papirus.pyrusservicedesk.utils.getTimeText


private const val VIEW_TYPE_COMMENT_INBOUND = 0
private const val VIEW_TYPE_COMMENT_OUTBOUND = 1
private const val VIEW_TYPE_WELCOME_MESSAGE = 2
private const val VIEW_TYPE_DATE = 3

internal class TicketAdapter: AdapterBase<TicketEntry>() {

    override val itemTouchHelper: ItemTouchHelper? = ItemTouchHelper(TouchCallback())
    private var onDownloadedFileClickListener: ((Attachment) -> Unit)? = null
    private var recentInboundCommentPositionWithAvatar = 0

    override fun getItemViewType(position: Int): Int {
        return with(itemsList[position]) {
            return@with when {
                type == Type.Date -> VIEW_TYPE_DATE
                type == Type.WelcomeMessage -> VIEW_TYPE_WELCOME_MESSAGE
                (this as CommentEntry).comment.isInbound -> VIEW_TYPE_COMMENT_OUTBOUND
                else -> VIEW_TYPE_COMMENT_INBOUND
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderBase<TicketEntry> {
        return when(viewType){
            VIEW_TYPE_COMMENT_INBOUND -> InboundCommentHolder(parent)
            VIEW_TYPE_COMMENT_OUTBOUND -> OutboundCommentHolder(parent)
            VIEW_TYPE_WELCOME_MESSAGE -> WelcomeMessageHolder(parent)
            else -> DateViewHolder(parent)
        } as ViewHolderBase<TicketEntry>
    }

    override fun onViewAttachedToWindow(holder: ViewHolderBase<TicketEntry>) {
        super.onViewAttachedToWindow(holder)
        holder.itemView.translationX = 0f
    }

    fun setOnDownloadedFileClickListener(listener: (attachment: Attachment) -> Unit) {
        onDownloadedFileClickListener = listener
    }

    private inner class InboundCommentHolder(parent: ViewGroup) :
        CommentHolder(parent, R.layout.psd_view_holder_comment_inbound) {

        override val comment: CommentView = itemView.findViewById(R.id.comment)
        override val creationTime: TextView = itemView.findViewById(R.id.creation_time)
        private val avatar = itemView.findViewById<ImageView>(R.id.avatar)
        private val authorName = itemView.findViewById<TextView>(R.id.author_name)

        override fun bindItem(item: CommentEntry) {
            super.bindItem(item)
            setAuthorNameVisibility(shouldShowAuthorName())
            with(shouldShowAuthorAvatar()) {
                setAuthorAvatarVisibility(this)
                if (this && shouldRedrawRecentCommentWithAvatar()) {
                    val toRedraw = recentInboundCommentPositionWithAvatar
                    itemView.post { notifyItemChanged(toRedraw) }
                    recentInboundCommentPositionWithAvatar = adapterPosition
                }
            }
        }

        private fun shouldRedrawRecentCommentWithAvatar(): Boolean =
            adapterPosition == itemsList.lastIndex && recentInboundCommentPositionWithAvatar != adapterPosition

        private fun setAuthorNameVisibility(visible: Boolean) {
            authorName.visibility = if (visible) VISIBLE else GONE
            if (visible) {
            }
            authorName.text = getItem().comment.author.name
        }

        private fun setAuthorAvatarVisibility(visible: Boolean) {
            avatar.visibility = if (visible) VISIBLE else INVISIBLE
            if (visible) {
                Picasso.get()
                    .load(getAvatarUrl(getItem().comment.author.avatarId))
                    .placeholder(ConfigUtils.getSupportAvatar(itemView.context))
                    .transform(CIRCLE_TRANSFORMATION)
                    .into(avatar)
            }
        }

        private fun shouldShowAuthorName(): Boolean {
            return adapterPosition == 0
                    || with(itemsList[adapterPosition - 1]) {
                when {
                    this.type != Type.Comment -> true
                    else -> getItem().comment.author != (this as CommentEntry).comment.author
                }
            }
        }

        private fun shouldShowAuthorAvatar(): Boolean {
            return with(itemsList.getOrNull(adapterPosition + 1)) {
                when {
                    this?.type != Type.Comment -> true
                    else -> getItem().comment.author != (this as CommentEntry).comment.author
                }
            }
        }
    }

    private inner class OutboundCommentHolder(parent: ViewGroup)
        : CommentHolder(parent, R.layout.psd_view_holder_comment_outbound){

        override val comment: CommentView = itemView.findViewById(R.id.comment)
        override val creationTime: TextView = itemView.findViewById(R.id.creation_time)
    }

    private abstract inner class CommentHolder(
            parent: ViewGroup,
            @LayoutRes layoutRes: Int)
        : ViewHolderBase<CommentEntry>(parent, layoutRes){

        abstract val comment: CommentView
        abstract val creationTime: TextView

        val onCommentClickListener = OnClickListener { getItem().onClickedCallback.onClicked(getItem()) }

        override fun bindItem(item: CommentEntry) {
            super.bindItem(item)
            comment.setOnClickListener(onCommentClickListener)
            comment.status = when {
                getItem().hasError() -> Status.Error
                getItem().comment.isLocal() -> Status.Processing
                else -> Status.Completed
            }
            comment.contentType =
                    if (item.comment.hasAttachments()) ContentType.Attachment else ContentType.Text
            when (comment.contentType){
                ContentType.Text -> bindTextView()
                ContentType.Attachment -> bindAttachmentView()
            }
            creationTime.text = getItem().comment.creationDate.getTimeText(itemView.context)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            getItem().uploadFileHooks?.unsubscribeFromProgress()
        }

        private fun bindTextView() {
            comment.setCommentText(getItem().comment.body)
        }

        private fun bindAttachmentView() {
            comment.setFileName(getItem().comment.attachments?.first()?.name ?: "")
            comment.setFileSize(getItem().comment.attachments?.first()?.bytesSize?.toFloat() ?: 0f)
            if (shouldHideFileButton()) {
                comment.isFileProgressVisible = false
                return
            }
            comment.isFileProgressVisible = true
            comment.fileProgressStatus = if (getItem().hasError()) Status.Error else Status.Completed
            comment.setOnProgressIconClickListener {
                when (comment.fileProgressStatus) {
                    Status.Processing -> getItem().uploadFileHooks?.cancelUploading()
                    Status.Completed -> onDownloadedFileClickListener?.invoke(getItem().comment.attachments!![0])
                    Status.Error -> comment.performClick()
                }
            }
            if (!getItem().hasError()) {
                getItem().uploadFileHooks?.subscribeOnProgress {
                    if (comment.fileProgressStatus != Status.Processing)
                        comment.fileProgressStatus = Status.Processing
                    comment.setProgress(it)
                }
            }
        }

        private fun shouldHideFileButton(): Boolean {
            return !getItem().comment.isLocal()
                    && !getItem().hasError()
                    && getItem().comment.attachments?.first()?.name?.canBePreviewed() == false
        }
    }

    private class WelcomeMessageHolder(parent: ViewGroup) :
        ViewHolderBase<WelcomeMessageEntry>(parent, R.layout.psd_view_holder_comment_inbound) {

        private val comment: CommentView = itemView.findViewById(R.id.comment)
        private val avatar = itemView.findViewById<ImageView>(R.id.avatar)
        private val authorName = itemView.findViewById<TextView>(R.id.author_name)

        override fun bindItem(item: WelcomeMessageEntry) {
            super.bindItem(item)
            authorName.visibility = GONE
            avatar.setImageDrawable(ConfigUtils.getSupportAvatar(itemView.context))
            comment.contentType = ContentType.Text
            comment.setCommentText(item.message)
        }
    }

    private class DateViewHolder(parent: ViewGroup)
        : ViewHolderBase<DateEntry>(parent, R.layout.psd_view_holder_date) {

        private val date = itemView.findViewById<TextView>(R.id.date)

        override fun bindItem(item: DateEntry) {
            super.bindItem(item)
            date.text = item.date
        }
    }

    private inner class TouchCallback : ItemTouchHelper.Callback() {

        override fun getMovementFlags(recyclerView: RecyclerView,
                                      viewHolder: RecyclerView.ViewHolder): Int {

            return makeFlag(ACTION_STATE_SWIPE,  ItemTouchHelper.LEFT)
        }

        override fun onMove(recyclerView: RecyclerView,
                            viewHolder: RecyclerView.ViewHolder,
                            target: RecyclerView.ViewHolder): Boolean {
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        }

        override fun getSwipeEscapeVelocity(defaultValue: Float): Float {
            return Float.MAX_VALUE
        }

        override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
            return Float.MAX_VALUE
        }

        override fun onChildDraw(c: Canvas,
                                 recyclerView: RecyclerView,
                                 viewHolder: RecyclerView.ViewHolder,
                                 dX: Float,
                                 dY: Float,
                                 actionState: Int,
                                 isCurrentlyActive: Boolean) {

            if (itemsList[viewHolder.adapterPosition].type != Type.Comment)
                return
            viewHolder as CommentHolder
            var x = dX
            if (x < -viewHolder.creationTime.width)
                x = -viewHolder.creationTime.width.toFloat()
            for (position in 0..(recyclerView.childCount - 1)) {
                recyclerView.findContainingViewHolder(recyclerView.getChildAt(position))?.let {
                    if (it.adapterPosition == - 1 || itemsList[it.adapterPosition].type != Type.Comment)
                        return@let
                    super.onChildDraw(
                            c,
                            recyclerView,
                            it,
                            x,
                            dY,
                            actionState,
                            false)
                }
            }
        }
    }
}
