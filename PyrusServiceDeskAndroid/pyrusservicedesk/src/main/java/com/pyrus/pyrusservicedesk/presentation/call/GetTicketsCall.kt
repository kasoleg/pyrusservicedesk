package com.pyrus.pyrusservicedesk.presentation.call

import kotlinx.coroutines.CoroutineScope
import com.pyrus.pyrusservicedesk.sdk.RequestFactory
import com.pyrus.pyrusservicedesk.sdk.data.TicketShortDescription
import com.pyrus.pyrusservicedesk.sdk.response.ResponseCallback
import com.pyrus.pyrusservicedesk.sdk.response.ResponseError

/**
 * Adapter for obtaining list of available tickets.
 * @param scope coroutine scope for executing request.
 */
internal class GetTicketsCall(
        scope: CoroutineScope,
        private val requests: RequestFactory)
    : BaseCall<List<TicketShortDescription>>(scope) {

    override suspend fun run(): CallResult<List<TicketShortDescription>> {
        var tickets: List<TicketShortDescription>? = null
        var error: ResponseError? = null
        requests.getTicketsRequest().execute(
            object: ResponseCallback<List<TicketShortDescription>> {
                override fun onSuccess(data: List<TicketShortDescription>) {
                    tickets = data
                }

                override fun onFailure(responseError: ResponseError) {
                    error = responseError
                }

            }
        )
        return CallResult(tickets, error)
    }
}