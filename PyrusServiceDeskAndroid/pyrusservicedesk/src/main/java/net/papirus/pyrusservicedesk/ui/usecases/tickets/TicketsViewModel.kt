package net.papirus.pyrusservicedesk.ui.usecases.tickets

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Transformations
import net.papirus.pyrusservicedesk.PyrusServiceDesk
import net.papirus.pyrusservicedesk.repository.data.Ticket
import net.papirus.pyrusservicedesk.repository.updates.UpdateBase
import net.papirus.pyrusservicedesk.repository.updates.UpdateType
import net.papirus.pyrusservicedesk.ui.viewmodel.ConnectionViewModelBase

internal class TicketsViewModel(serviceDesk: PyrusServiceDesk)
    : ConnectionViewModelBase(serviceDesk) {

    private val isLoading = MediatorLiveData<Boolean>()
    private val tickets = MediatorLiveData<List<Ticket>>()
    private val request = MutableLiveData<Boolean>()

    init{
        isLoading.apply {
            addSource(request){
                value = true
            }
        }

        tickets.apply {
            addSource(
                Transformations.switchMap(request){
                    repository.getTickets()
                }
            ){
                isLoading.value = false
                tickets.value = it?.tickets
            }
        }
        if (isNetworkConnected.value == true)
            loadData()
        onInitialized()
    }

    override fun <T : UpdateBase> onUpdateReceived(update: T) {
        if (!update.hasError())
            loadData()
    }

    override fun getUpdateTypes(): Set<UpdateType> {
        return setOf(UpdateType.TicketCreated, UpdateType.TicketUpdated)
    }


    override fun loadData() {
        request.value = true
    }

    fun getIsLoadingLiveData(): LiveData<Boolean> = isLoading

    fun getTicketsLiveData(): LiveData<List<Ticket>> = tickets
}