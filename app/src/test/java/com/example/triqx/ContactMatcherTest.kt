package com.example.triqx

import com.example.triqx.data.local.ContactEntity
import com.example.triqx.utils.ContactMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactMatcherTest {

    private val raj = ContactEntity(
        id = 1, displayName = "Raj", phoneNumbers = listOf("+91 98765 43210"),
        emails = listOf("raj@acme.com"), lookupKey = "0r12-ABC%2F"
    )
    private val rajesh = ContactEntity(id = 2, displayName = "Rajesh Kumar")
    private val priya = ContactEntity(id = 3, displayName = "प्रिया शर्मा", officialName = "Priya Sharma")
    private val all = listOf(raj, rajesh, priya)

    private fun m(
        title: String?, text: String? = null, senders: List<String> = emptyList(),
        emails: List<String> = emptyList(), uris: List<String> = emptyList()
    ) = ContactMatcher.findBestMatch(all, title, text, senders, emails, uris)?.contact?.id

    @Test fun exactTitle() = assertEquals(1, m("Raj"))
    @Test fun shortNameDoesNotMatchLongerName() = assertEquals(2, m("Rajesh Kumar"))
    @Test fun noSubstringFalsePositives() { assertNull(m("Rajkot Traders")); assertNull(m("Suraj")) }
    @Test fun bodyMentionIgnored() = assertNull(m("Amit", "tell Raj I said hi, mail raj2@x.com"))
    @Test fun groupSenderPrefix() = assertEquals(1, m("Family Group", "Raj: dinner at 8?"))
    @Test fun titleSegment() = assertEquals(1, m("Project X: Raj"))
    @Test fun whatsappCounterStripped() = assertEquals(1, m("Raj (3 messages)"))
    @Test fun messagingStyleSender() = assertEquals(1, m("Office", senders = listOf("Raj")))
    @Test fun phoneFormatsVary() {
        assertEquals(1, m("9876543210")); assertEquals(1, m("+91-98765-43210")); assertEquals(1, m("098765 43210"))
    }
    @Test fun differentPhoneNoMatch() = assertNull(m("+91 98765 43211"))
    @Test fun emailExactOnly() {
        assertEquals(1, m("Weekly report", emails = listOf("RAJ@acme.com"))); assertNull(m("x", emails = listOf("raj@acme.co")))
    }
    @Test fun lookupUriExactSegment() {
        assertEquals(1, m("Unknown", uris = listOf("content://com.android.contacts/contacts/lookup/0r12-ABC%2F/55")))
        assertNull(m("Unknown", uris = listOf("content://com.android.contacts/contacts/lookup/0r12-ABC%2FXYZ/55")))
    }
    @Test fun devanagariAndOfficialName() { assertEquals(3, m("प्रिया शर्मा")); assertEquals(3, m("priya sharma")) }
    @Test fun strongIdentifierBeatsName() = assertEquals(1, m("Rajesh Kumar", emails = listOf("raj@acme.com")))
    @Test fun nullsAndEmpty() { assertNull(m(null, null)); assertNull(ContactMatcher.findBestMatch(emptyList(), "Raj", null)) }
}
