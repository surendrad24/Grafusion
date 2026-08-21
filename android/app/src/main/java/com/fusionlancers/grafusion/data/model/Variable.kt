package com.fusionlancers.grafusion.data.model

/**
 * A dashboard template variable parsed from dashboard.templating.list.
 * We support the common types: custom, constant, textbox, interval, datasource,
 * and query (using its pre-resolved `current`/`options` snapshot from the JSON;
 * we don't re-query the datasource to refresh the option list).
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
)

data class VariableOption(val text: String, val value: String)
