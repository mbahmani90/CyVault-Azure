package com.cypressit.cyvault.feature.vault.card.data.remote

import com.cypressit.cyvault.feature.vault.card.data.remote.dto.CardResponseDto
import com.cypressit.cyvault.feature.vault.card.data.remote.dto.CreateCardRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class CardApiService(private val client: HttpClient) {

    companion object {
        private const val BASE_URL = "https://api.cyvault.com/v1/cards"
    }

    suspend fun getCards(): List<CardResponseDto> =
        client.get(BASE_URL).body()

    suspend fun createCard(request: CreateCardRequestDto): CardResponseDto =
        client.post(BASE_URL) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun deleteCard(id: String) {
        client.delete("$BASE_URL/$id")
    }
}
