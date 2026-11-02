package com.cypressit.cyvault.feature.vault.di

import com.cypressit.cyvault.feature.vault.card.domain.repository.CardRepository
import com.cypressit.cyvault.feature.vault.fake.FakeCardRepository
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class UserSessionTest {

    private fun koin(): Koin = koinApplication {
        modules(
            module {
                scope(UserSession.qualifier) {
                    scoped<CardRepository> { FakeCardRepository() }
                }
            },
        )
    }.koin

    @Test
    fun withinASession_everyScreenGetsTheSameRepository() {
        val koin = koin()

        val home = UserSession.scope(koin).get<CardRepository>()
        val cards = UserSession.scope(koin).get<CardRepository>()

        assertSame(home, cards)
    }

    @Test
    fun afterSignOut_theNextSessionGetsANewRepository() {
        val koin = koin()
        val first = UserSession.scope(koin).get<CardRepository>()

        UserSession.close(koin)
        val next = UserSession.scope(koin).get<CardRepository>()

        assertNotSame(first, next)
    }

    @Test
    fun closingWithoutASession_doesNothing() {
        UserSession.close(koin())
    }
}
