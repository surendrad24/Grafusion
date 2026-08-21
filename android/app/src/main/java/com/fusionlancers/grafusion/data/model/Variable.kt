package com.fusionlancers.grafusion.data.model

/**
 * A dashboard template variable parsed from dashboard.templating.list.
 * We support the common types: custom, constant, textbox, interval, datasource,
 * and query (resolved via VariableResolver against Prometheus/Loki label-values).
 */
data class Variable(
    val name: String,
    val label: String?,
    val type: String,
    val current: List<String>,
    val options: List<VariableOption>,
    val multi: Boolean,
    val includeAll: Boolean,
    val allValue: String?,
    val hide: Int,
    /** Datasource type ("prometheus"/"loki") for `type=query`; null otherwise. */
    val datasourceType: String? = null,
    /** Datasource UID used to route the label-values request. */
    val datasourceUid: String? = null,
    /** Raw query expression, e.g. `label_values(container_last_seen, name)`. */
    val queryExpr: String? = null,
    /** Optional post-filter regex applied to resolved values. */
    val regex: String? = null,
    /** 0=none, 1=alphabetical asc, 2=alphabetical desc, 3=numeric asc, 4=numeric desc, 5=alpha ci asc, 6=alpha ci desc. */
    val sort: Int = 0,
)

data class VariableOption(val text: String, val value: String)
