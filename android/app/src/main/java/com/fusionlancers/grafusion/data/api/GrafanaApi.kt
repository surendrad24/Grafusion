package com.fusionlancers.grafusion.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GrafanaApi {

    @POST("login")
    suspend fun login(@Body body: LoginBody): LoginResponse

    @GET("api/user")
    suspend fun currentUser(@Header("Authorization") auth: String): GrafanaUser

    @POST("api/auth/keys")
    suspend fun createLegacyApiKey(
        @Header("Authorization") auth: String,
        @Body body: CreateKeyBody,
    ): CreateKeyResponse

    @GET("api/search")
    suspend fun searchDashboards(
        @Header("Authorization") auth: String,
        @Query("type") type: String = "dash-db",
        @Query("limit") limit: Int = 1000,
    ): List<DashboardSummary>

    @GET("api/dashboards/uid/{uid}")
    suspend fun dashboardByUid(
        @Header("Authorization") auth: String,
        @Path("uid") uid: String,
    ): DashboardDetail

    @POST("api/ds/query")
    suspend fun queryDatasource(
        @Header("Authorization") auth: String,
        @Body body: kotlinx.serialization.json.JsonObject,
    ): kotlinx.serialization.json.JsonObject

    @POST("api/dashboards/db")
    suspend fun saveDashboard(
        @Header("Authorization") auth: String,
        @Body body: kotlinx.serialization.json.JsonObject,
    ): SaveDashboardResponse

    @POST("api/user/stars/dashboard/{id}")
    suspend fun starDashboard(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
    ): retrofit2.Response<Unit>

    @DELETE("api/user/stars/dashboard/{id}")
    suspend fun unstarDashboard(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
    ): retrofit2.Response<Unit>

    @GET("api/alertmanager/grafana/api/v2/alerts")
    suspend fun grafanaAlerts(
        @Header("Authorization") auth: String,
        @Query("active") active: Boolean = true,
        @Query("silenced") silenced: Boolean = true,
        @Query("inhibited") inhibited: Boolean = true,
    ): List<AmAlert>
}

@Serializable
data class AmAlert(
    val labels: Map<String, String> = emptyMap(),
    val annotations: Map<String, String> = emptyMap(),
    val startsAt: String? = null,
    val endsAt: String? = null,
    val generatorURL: String? = null,
    val fingerprint: String? = null,
    val status: AmStatus = AmStatus(),
)

@Serializable
data class AmStatus(
    val state: String = "active",
    val silencedBy: List<String> = emptyList(),
    val inhibitedBy: List<String> = emptyList(),
)

@Serializable
data class SaveDashboardResponse(
    val id: Long? = null,
    val uid: String? = null,
    val url: String? = null,
    val status: String? = null,
    val version: Int? = null,
    val slug: String? = null,
)

@Serializable
data class LoginBody(val user: String, val password: String)

@Serializable
data class LoginResponse(val message: String? = null)

@Serializable
data class GrafanaUser(
    val id: Long,
    val login: String,
    val email: String? = null,
    val name: String? = null,
)

@Serializable
data class CreateKeyBody(val name: String, val role: String = "Admin")

@Serializable
data class CreateKeyResponse(val id: Long, val name: String, val key: String)

@Serializable
data class DashboardSummary(
    val id: Long? = null,
    val uid: String,
    val title: String,
    val uri: String? = null,
    val url: String? = null,
    @SerialName("folderTitle") val folderTitle: String? = null,
    @SerialName("folderUid") val folderUid: String? = null,
    val tags: List<String> = emptyList(),
    val isStarred: Boolean = false,
)

@Serializable
data class DashboardDetail(
    val dashboard: kotlinx.serialization.json.JsonObject,
    val meta: kotlinx.serialization.json.JsonObject,
)
