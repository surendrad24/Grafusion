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

    @GET("api/user/preferences")
    suspend fun userPreferences(@Header("Authorization") auth: String): UserPreferences

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

    @GET("api/datasources")
    suspend fun listDatasources(
        @Header("Authorization") auth: String,
    ): List<Datasource>

    @GET("api/datasources/uid/{uid}/health")
    suspend fun datasourceHealth(
        @Header("Authorization") auth: String,
        @Path("uid") uid: String,
    ): retrofit2.Response<DatasourceHealth>

    // ---- Admin: org users, teams, service accounts, orgs ----

    @GET("api/org/users")
    suspend fun orgUsers(
        @Header("Authorization") auth: String,
    ): retrofit2.Response<List<OrgUser>>

    @GET("api/teams/search")
    suspend fun teams(
        @Header("Authorization") auth: String,
        @Query("perpage") perPage: Int = 200,
    ): retrofit2.Response<TeamSearch>

    @GET("api/serviceaccounts/search")
    suspend fun serviceAccounts(
        @Header("Authorization") auth: String,
        @Query("perpage") perPage: Int = 200,
    ): retrofit2.Response<ServiceAccountSearch>

    @GET("api/orgs")
    suspend fun orgs(
        @Header("Authorization") auth: String,
    ): retrofit2.Response<List<GrafanaOrg>>

    // ---- Library panels + playlists ----

    @GET("api/library-elements")
    suspend fun libraryElements(
        @Header("Authorization") auth: String,
        @Query("kind") kind: Int = 1, // 1 = panels
        @Query("perPage") perPage: Int = 200,
    ): retrofit2.Response<LibraryElementsResponse>

    @GET("api/playlists")
    suspend fun playlists(
        @Header("Authorization") auth: String,
    ): retrofit2.Response<List<PlaylistSummary>>

    @GET("api/playlists/{uid}")
    suspend fun playlist(
        @Header("Authorization") auth: String,
        @Path("uid") uid: String,
    ): retrofit2.Response<PlaylistDetail>

    // ---- Reports (Grafana Enterprise) ----

    @GET("api/reports")
    suspend fun reports(
        @Header("Authorization") auth: String,
    ): retrofit2.Response<List<GrafanaReport>>

    @POST("api/reports/send-report/{id}")
    suspend fun sendReport(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
    ): retrofit2.Response<kotlinx.serialization.json.JsonObject>

    // ---- Snapshots ----

    @POST("api/snapshots")
    suspend fun createSnapshot(
        @Header("Authorization") auth: String,
        @Body body: kotlinx.serialization.json.JsonObject,
    ): retrofit2.Response<SnapshotResponse>

    @POST("api/annotations")
    suspend fun createAnnotation(
        @Header("Authorization") auth: String,
        @Body body: CreateAnnotationBody,
    ): CreateAnnotationResponse

    @GET("api/annotations")
    suspend fun listAnnotations(
        @Header("Authorization") auth: String,
        @Query("limit") limit: Int = 100,
        @Query("dashboardUID") dashboardUid: String? = null,
        @Query("type") type: String? = null,
        @Query("from") from: Long? = null,
        @Query("to") to: Long? = null,
    ): List<GrafanaAnnotation>

    @GET("api/alertmanager/grafana/api/v2/alerts")
    suspend fun grafanaAlerts(
        @Header("Authorization") auth: String,
        @Query("active") active: Boolean = true,
        @Query("silenced") silenced: Boolean = true,
        @Query("inhibited") inhibited: Boolean = true,
    ): List<AmAlert>

    @POST("api/alertmanager/grafana/api/v2/silences")
    suspend fun createSilence(
        @Header("Authorization") auth: String,
        @Body body: kotlinx.serialization.json.JsonObject,
    ): retrofit2.Response<kotlinx.serialization.json.JsonObject>

    @GET("api/alertmanager/grafana/api/v2/silences")
    suspend fun listSilences(
        @Header("Authorization") auth: String,
    ): List<AmSilence>

    @DELETE("api/alertmanager/grafana/api/v2/silence/{id}")
    suspend fun expireSilence(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
    ): retrofit2.Response<Unit>

    // Grafana Managed alert rules (Ruler API). Response keyed by folder/namespace name,
    // each value a list of rule groups. Read-only Viewer role is enough.
    @GET("api/ruler/grafana/api/v1/rules")
    suspend fun listGrafanaAlertRules(
        @Header("Authorization") auth: String,
    ): retrofit2.Response<Map<String, List<GrafanaRuleGroup>>>

    // Full Alertmanager config: receivers (contact points) + route tree. Readable with Viewer
    // + Alerting access, which is broader than /api/v1/provisioning/contact-points (that needs
    // the provisioning role). We parse just the shapes we actually render.
    @GET("api/alertmanager/grafana/config/api/v1/alerts")
    suspend fun getAlertmanagerConfig(
        @Header("Authorization") auth: String,
    ): retrofit2.Response<AlertmanagerConfigEnvelope>

    // ---- Grafana OnCall plugin (optional; 404 when the plugin isn't installed) ----

    @GET("api/plugins/grafana-oncall-app/resources/schedules/")
    suspend fun onCallSchedules(
        @Header("Authorization") auth: String,
    ): retrofit2.Response<OnCallPagedSchedules>

    @GET("api/plugins/grafana-oncall-app/resources/schedules/{id}/final_shifts/")
    suspend fun onCallFinalShifts(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
    ): retrofit2.Response<List<OnCallShift>>

    @GET("api/plugins/grafana-oncall-app/resources/alert_groups/")
    suspend fun onCallAlertGroups(
        @Header("Authorization") auth: String,
        @Query("state") state: String = "firing",
    ): retrofit2.Response<OnCallPagedAlertGroups>

    @POST("api/plugins/grafana-oncall-app/resources/alert_groups/{id}/acknowledge/")
    suspend fun onCallAcknowledge(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
    ): retrofit2.Response<kotlinx.serialization.json.JsonObject>

    @POST("api/plugins/grafana-oncall-app/resources/alert_groups/{id}/resolve/")
    suspend fun onCallResolve(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
    ): retrofit2.Response<kotlinx.serialization.json.JsonObject>
}

@Serializable
data class OnCallPagedSchedules(
    val results: List<OnCallSchedule> = emptyList(),
)

@Serializable
data class OnCallSchedule(
    val id: String,
    val name: String = "",
    @SerialName("type") val type: String? = null,
    @SerialName("on_call_now") val onCallNow: List<OnCallUser> = emptyList(),
)

@Serializable
data class OnCallUser(
    val pk: String? = null,
    val username: String? = null,
    val email: String? = null,
    val avatar: String? = null,
)

@Serializable
data class OnCallShift(
    @SerialName("shift_start") val shiftStart: String? = null,
    @SerialName("shift_end") val shiftEnd: String? = null,
    val users: List<OnCallUser> = emptyList(),
    @SerialName("is_gap") val isGap: Boolean = false,
    @SerialName("is_override") val isOverride: Boolean = false,
)

@Serializable
data class OnCallPagedAlertGroups(
    val results: List<OnCallAlertGroup> = emptyList(),
)

@Serializable
data class OnCallAlertGroup(
    val pk: String,
    @SerialName("alerts_count") val alertsCount: Int = 0,
    @SerialName("status") val status: Int = 0,
    val title: String? = null,
    @SerialName("render_for_web") val renderForWeb: kotlinx.serialization.json.JsonObject? = null,
    @SerialName("permalinks") val permalinks: OnCallPermalinks? = null,
    @SerialName("integration_name") val integrationName: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("resolved") val resolved: Boolean = false,
    @SerialName("acknowledged") val acknowledged: Boolean = false,
    @SerialName("silenced") val silenced: Boolean = false,
)

@Serializable
data class OnCallPermalinks(
    val web: String? = null,
)

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
data class AmSilence(
    val id: String,
    val status: AmSilenceStatus = AmSilenceStatus(),
    val startsAt: String? = null,
    val endsAt: String? = null,
    val updatedAt: String? = null,
    val createdBy: String? = null,
    val comment: String? = null,
    val matchers: List<AmMatcher> = emptyList(),
)

@Serializable
data class AmSilenceStatus(val state: String = "active")

@Serializable
data class AmMatcher(
    val name: String = "",
    val value: String = "",
    val isRegex: Boolean = false,
    val isEqual: Boolean = true,
)

// ---- Grafana Managed alert rule (Ruler API) shapes. Grafana wraps its own fields under
// grafana_alert; Prometheus / Loki data source rules use `alert` or `record` at the top level
// so we keep both optional and let the UI degrade gracefully.

@Serializable
data class GrafanaRuleGroup(
    val name: String = "",
    val interval: String? = null,
    val rules: List<GrafanaRule> = emptyList(),
)

@Serializable
data class GrafanaRule(
    @SerialName("grafana_alert") val grafanaAlert: GrafanaAlertDef? = null,
    val alert: String? = null,
    val record: String? = null,
    val expr: String? = null,
    val `for`: String? = null,
    val labels: Map<String, String> = emptyMap(),
    val annotations: Map<String, String> = emptyMap(),
)

// ---- Alertmanager config shapes. The full config is huge and heterogeneous; we deliberately
// only model the fields our UI reads, using JsonObject for the settings blob so integration
// types we haven't seen yet still deserialize.

@Serializable
data class AlertmanagerConfigEnvelope(
    @SerialName("alertmanager_config") val config: AlertmanagerConfig = AlertmanagerConfig(),
)

@Serializable
data class AlertmanagerConfig(
    val route: AmRoute? = null,
    val receivers: List<AmReceiver> = emptyList(),
    @SerialName("mute_time_intervals") val muteTimeIntervals: kotlinx.serialization.json.JsonArray? = null,
)

@Serializable
data class AmReceiver(
    val name: String = "",
    @SerialName("grafana_managed_receiver_configs")
    val grafanaConfigs: List<AmGrafanaReceiverConfig> = emptyList(),
)

@Serializable
data class AmGrafanaReceiverConfig(
    val uid: String? = null,
    val name: String = "",
    val type: String = "",
    @SerialName("disableResolveMessage") val disableResolveMessage: Boolean = false,
    val settings: kotlinx.serialization.json.JsonObject? = null,
    @SerialName("secureFields") val secureFields: Map<String, Boolean> = emptyMap(),
)

@Serializable
data class AmRoute(
    val receiver: String? = null,
    @SerialName("group_by") val groupBy: List<String> = emptyList(),
    val matchers: List<String> = emptyList(),
    @SerialName("object_matchers") val objectMatchers: List<List<String>> = emptyList(),
    @SerialName("mute_time_intervals") val muteTimeIntervals: List<String> = emptyList(),
    @SerialName("group_wait") val groupWait: String? = null,
    @SerialName("group_interval") val groupInterval: String? = null,
    @SerialName("repeat_interval") val repeatInterval: String? = null,
    @SerialName("continue") val cont: Boolean = false,
    val routes: List<AmRoute> = emptyList(),
)

@Serializable
data class GrafanaAlertDef(
    val uid: String? = null,
    val title: String = "",
    val condition: String? = null,
    @SerialName("no_data_state") val noDataState: String? = null,
    @SerialName("exec_err_state") val execErrState: String? = null,
    // The `data` field carries the full query model tree; we keep it as JsonArray so the UI
    // can decide whether to render a preview without us having to model every datasource type.
    val data: kotlinx.serialization.json.JsonArray? = null,
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
data class UserPreferences(
    val theme: String? = null,
    val homeDashboardUID: String? = null,
    val timezone: String? = null,
    val weekStart: String? = null,
    val language: String? = null,
)

@Serializable
data class CreateAnnotationBody(
    val dashboardUID: String? = null,
    val panelId: Long? = null,
    val time: Long,
    val timeEnd: Long? = null,
    val tags: List<String> = emptyList(),
    val text: String,
)

@Serializable
data class CreateAnnotationResponse(
    val id: Long = 0,
    val message: String? = null,
)

@Serializable
data class GrafanaAnnotation(
    val id: Long = 0,
    val alertId: Long = 0,
    val dashboardId: Long = 0,
    val dashboardUID: String? = null,
    val panelId: Long = 0,
    val time: Long = 0,
    val timeEnd: Long = 0,
    val text: String = "",
    val tags: List<String> = emptyList(),
    val login: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val newState: String? = null,
    val prevState: String? = null,
    val alertName: String? = null,
)

@Serializable
data class Datasource(
    val id: Long = 0,
    val uid: String,
    val name: String,
    val type: String,
    val url: String = "",
    val isDefault: Boolean = false,
)

@Serializable
data class DatasourceHealth(
    val status: String = "UNKNOWN",
    val message: String = "",
)

@Serializable
data class OrgUser(
    val userId: Long = 0,
    val login: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "",
    val avatarUrl: String? = null,
    val lastSeenAtAge: String? = null,
    val isDisabled: Boolean = false,
)

@Serializable
data class TeamSearch(
    val teams: List<GrafanaTeam> = emptyList(),
    val totalCount: Int = 0,
)

@Serializable
data class GrafanaTeam(
    val id: Long = 0,
    val orgId: Long = 0,
    val name: String = "",
    val email: String = "",
    val memberCount: Int = 0,
    val avatarUrl: String? = null,
)

@Serializable
data class ServiceAccountSearch(
    val serviceAccounts: List<GrafanaServiceAccount> = emptyList(),
    val totalCount: Int = 0,
)

@Serializable
data class GrafanaServiceAccount(
    val id: Long = 0,
    val name: String = "",
    val login: String = "",
    val orgId: Long = 0,
    val role: String = "",
    val tokens: Int = 0,
    val isDisabled: Boolean = false,
    val avatarUrl: String? = null,
)

@Serializable
data class GrafanaOrg(
    val id: Long = 0,
    val name: String = "",
)

@Serializable
data class LibraryElementsResponse(
    val result: LibraryElementsResult = LibraryElementsResult(),
)

@Serializable
data class LibraryElementsResult(
    val elements: List<LibraryElement> = emptyList(),
    val totalCount: Int = 0,
    val page: Int = 1,
    val perPage: Int = 100,
)

@Serializable
data class LibraryElement(
    val id: Long = 0,
    val uid: String = "",
    val name: String = "",
    val kind: Int = 1,
    val type: String = "",
    val description: String = "",
    val folderUid: String? = null,
    val meta: LibraryElementMeta = LibraryElementMeta(),
)

@Serializable
data class LibraryElementMeta(
    val folderName: String? = null,
    val connectedDashboards: Int = 0,
    val updated: String? = null,
    val createdBy: LibraryElementUser = LibraryElementUser(),
    val updatedBy: LibraryElementUser = LibraryElementUser(),
)

@Serializable
data class LibraryElementUser(
    val id: Long = 0,
    val name: String = "",
    val avatarUrl: String? = null,
)

@Serializable
data class PlaylistSummary(
    val id: Long = 0,
    val uid: String,
    val name: String,
    val interval: String = "",
)

@Serializable
data class PlaylistDetail(
    val uid: String,
    val name: String,
    val interval: String = "",
    val items: List<PlaylistItem> = emptyList(),
)

@Serializable
data class PlaylistItem(
    val type: String = "",
    val value: String = "",
    val title: String = "",
)

@Serializable
data class GrafanaReport(
    val id: Long = 0,
    val name: String = "",
    val state: String = "",
    val recipients: String = "",
    val subject: String = "",
    val message: String = "",
    val schedule: ReportSchedule = ReportSchedule(),
    val dashboards: List<ReportDashboardRef> = emptyList(),
)

@Serializable
data class ReportSchedule(
    val frequency: String = "",
    val startDate: String? = null,
    val endDate: String? = null,
    val timeZone: String? = null,
)

@Serializable
data class ReportDashboardRef(
    val dashboard: ReportDashboardRefInner? = null,
)

@Serializable
data class ReportDashboardRefInner(
    val uid: String? = null,
    val name: String? = null,
)

@Serializable
data class SnapshotResponse(
    val key: String = "",
    val deleteKey: String = "",
    val url: String = "",
    val deleteUrl: String = "",
    val id: Long = 0,
)

@Serializable
data class DashboardDetail(
    val dashboard: kotlinx.serialization.json.JsonObject,
    val meta: kotlinx.serialization.json.JsonObject,
)
