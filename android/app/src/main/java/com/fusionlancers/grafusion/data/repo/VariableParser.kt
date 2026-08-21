package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.model.Variable
import com.fusionlancers.grafusion.data.model.VariableOption
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object VariableParser {

    fun parse(dashboard: JsonObject): List<Variable> {
        val list = dashboard["templating"]?.jsonObject?.get("list")?.jsonArray ?: return emptyList()
        return list.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull() ?: return@mapNotNull null
            val type = obj["type"]?.jsonPrimitive?.contentOrNull() ?: "custom"
            val label = obj["label"]?.jsonPrimitive?.contentOrNull()
            val multi = obj["multi"]?.jsonPrimitive?.contentOrNull()?.toBooleanStrictOrNull() ?: false
            val includeAll = obj["includeAll"]?.jsonPrimitive?.contentOrNull()?.toBooleanStrictOrNull() ?: false
            val allValue = obj["allValue"]?.jsonPrimitive?.contentOrNull()
            val hide = obj["hide"]?.jsonPrimitive?.contentOrNull()?.toIntOrNull() ?: 0

            val optionsArr = obj["options"] as? JsonArray
            val options = optionsArr?.mapNotNull { opt ->
                val o = opt as? JsonObject ?: return@mapNotNull null
                val text = o["text"]?.jsonPrimitive?.contentOrNull() ?: return@mapNotNull null
                val value = o["value"]?.jsonPrimitive?.contentOrNull() ?: text
                VariableOption(text, value)
            } ?: emptyList()

            val current = extractCurrent(obj["current"] as? JsonObject)

            Variable(
                name = name,
                label = label,
                type = type,
                current = current,
                options = options,
                multi = multi,
                includeAll = includeAll,
                allValue = allValue,
                hide = hide,
            )
        }
    }

    private fun extractCurrent(current: JsonObject?): List<String> {
        if (current == null) return emptyList()
        val valueEl = current["value"] ?: return emptyList()
        return when (valueEl) {
            is JsonArray -> valueEl.mapNotNull { (it as? JsonPrimitive)?.contentOrNull() }
            is JsonPrimitive -> listOf(valueEl.contentOrNull() ?: return emptyList())
            else -> emptyList()
        }
    }

    private fun JsonPrimitive.contentOrNull(): String? = runCatching { content }.getOrNull()
}
