package com.cypressit.cyvault.feature.vault.addcard.data.remote

import com.cypressit.cyvault.feature.vault.addcard.data.remote.dto.YapilyAccountAuthRequestDto
import com.cypressit.cyvault.feature.vault.addcard.data.remote.dto.YapilyAccountAuthResponseDto
import com.cypressit.cyvault.feature.vault.addcard.data.remote.dto.YapilyAccountDto
import com.cypressit.cyvault.feature.vault.addcard.data.remote.dto.YapilyAccountsResponseDto
import com.cypressit.cyvault.feature.vault.addcard.data.remote.dto.YapilyInstitutionDto
import com.cypressit.cyvault.feature.vault.addcard.data.remote.dto.YapilyInstitutionsResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.io.IOException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class YapilyService(private val client: HttpClient) {

    @OptIn(ExperimentalEncodingApi::class)
    private val basicAuth: String
        get() {
            val credentials = "${YapilyConfig.APPLICATION_ID}:${YapilyConfig.APPLICATION_SECRET}"
            return "Basic ${Base64.encode(credentials.encodeToByteArray())}"
        }

    suspend fun getInstitutions(query: String, countryCodes: List<String>): List<YapilyInstitutionDto> {
        val response: YapilyInstitutionsResponseDto =
            client.get("${YapilyConfig.BASE_URL}/institutions") {
                header(HttpHeaders.Authorization, basicAuth)
                parameter("filter[q]", query)
                parameter("filter[countries]", countryCodes.joinToString(","))
                parameter("filter[features]", "ACCOUNT_BALANCE_DETAILS")
            }.body()
        return response.data
    }

    suspend fun createAccountAuthorisation(institutionId: String): YapilyAccountAuthResponseDto =
        client.post("${YapilyConfig.BASE_URL}/account-auth-requests") {
            header(HttpHeaders.Authorization, basicAuth)
            contentType(ContentType.Application.Json)
            setBody(
                YapilyAccountAuthRequestDto(
                    applicationUserId = "cyvault-user",
                    institutionId = institutionId,
                    callback = YapilyConfig.CALLBACK_URL,
                )
            )
        }.body()

    suspend fun getAccounts(consentToken: String): List<YapilyAccountDto> {
        val response: YapilyAccountsResponseDto =
            client.get("${YapilyConfig.BASE_URL}/accounts") {
                header(HttpHeaders.Authorization, basicAuth)
                header("consent", consentToken)
            }.body()
        return response.data
    }
}
