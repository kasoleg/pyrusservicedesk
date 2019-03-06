package net.papirus.pyrusservicedesk.repository.data

import com.google.gson.annotations.SerializedName

internal const val EMPTY_TICKET_ID = 0

internal data class Ticket(
        @SerializedName("ticket_id")
        val ticketId: Int? = null,
        @SerializedName("subject")
        val subject: String = "",
        @SerializedName("comments")
        val comments: List<Comment>? = null)