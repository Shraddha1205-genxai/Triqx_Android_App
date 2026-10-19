package com.example.triqx.ui.priority

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.triqx.data.local.ObligationDao
import com.example.triqx.data.local.ObligationEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PriorityViewModel @Inject constructor(
    private val obligationDao: ObligationDao
) : ViewModel() {

    val activeObligations: StateFlow<List<ObligationEntity>> = obligationDao
        .getActiveObligations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allObligations: StateFlow<List<ObligationEntity>> = obligationDao
        .getAllObligations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCount: StateFlow<Int> = obligationDao
        .getActiveCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun markDone(id: Long) {
        viewModelScope.launch {
            obligationDao.updateStatus(id, "Done", System.currentTimeMillis())
        }
    }

    fun dismiss(id: Long) {
        viewModelScope.launch {
            obligationDao.updateStatus(id, "Dismissed", System.currentTimeMillis())
        }
    }

    fun snooze(id: Long, newDueTimestamp: Long) {
        viewModelScope.launch {
            val obligation = obligationDao.getById(id) ?: return@launch
            obligationDao.update(obligation.copy(dueTimestamp = newDueTimestamp))
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            obligationDao.deleteById(id)
        }
    }

    fun addObligation(obligation: ObligationEntity) {
        viewModelScope.launch {
            obligationDao.insert(obligation)
        }
    }
}
