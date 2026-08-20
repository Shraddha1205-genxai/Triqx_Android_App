package com.example.triqx.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.triqx.data.local.ContactDao
import com.example.triqx.data.local.ContactEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactViewModel @Inject constructor(
    private val contactDao: ContactDao
) : ViewModel() {

    val priorityContacts = contactDao.getAllContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getContactById(id: Int) = contactDao.getContactById(id)

    fun addContact(
        displayName: String,
        officialName: String? = null,
        phoneNumbers: List<String> = emptyList(),
        emails: List<String> = emptyList(),
        about: String? = null,
        lookupKey: String? = null
    ) {
        viewModelScope.launch {
            contactDao.insertContact(
                ContactEntity(
                    displayName = displayName.trim(),
                    officialName = officialName?.trim()?.takeIf { it.isNotEmpty() },
                    phoneNumbers = phoneNumbers.map { it.trim() }.filter { it.isNotEmpty() },
                    emails = emails.map { it.trim() }.filter { it.isNotEmpty() },
                    about = about?.trim()?.takeIf { it.isNotEmpty() },
                    lookupKey = lookupKey
                )
            )
        }
    }

    fun updateContact(contact: ContactEntity) {
        viewModelScope.launch {
            contactDao.updateContact(
                contact.copy(
                    displayName = contact.displayName.trim(),
                    officialName = contact.officialName?.trim()?.takeIf { it.isNotEmpty() },
                    phoneNumbers = contact.phoneNumbers.map { it.trim() }.filter { it.isNotEmpty() },
                    emails = contact.emails.map { it.trim() }.filter { it.isNotEmpty() },
                    about = contact.about?.trim()?.takeIf { it.isNotEmpty() }
                )
            )
        }
    }

    fun removeContact(contact: ContactEntity) {
        viewModelScope.launch {
            contactDao.deleteContact(contact)
        }
    }
}
