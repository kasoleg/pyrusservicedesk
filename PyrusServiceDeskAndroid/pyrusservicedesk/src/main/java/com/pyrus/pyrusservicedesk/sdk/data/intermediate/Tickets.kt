package com.pyrus.pyrusservicedesk.sdk.data.intermediate

import com.google.gson.annotations.SerializedName
import com.pyrus.pyrusservicedesk.sdk.data.TicketShortDescription

/**
 * Intermediate data for parsing list of TicketShortDescription object
 */
internal data class Tickets(
        @SerializedName("tickets")
        val tickets: List<TicketShortDescription> = emptyList())