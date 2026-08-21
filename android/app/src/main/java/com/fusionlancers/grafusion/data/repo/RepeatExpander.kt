package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.model.Panel
import com.fusionlancers.grafusion.data.model.PanelGroup
import com.fusionlancers.grafusion.data.model.Variable

/**
 * Expand panels with `repeat="varname"` into one clone per selected value of that variable.
 * Each clone gets a synthetic id (originalId * REPEAT_ID_STRIDE + index), the variable name
 * appended to the title, and repeatValue set so [DashboardRepository.queryPanel] can pin the
 * variable to that single value.
 *
 * The original template panel is dropped from the output when the variable has 1+ values;
 * if the variable is missing or empty we keep the panel as-is so users see something.
 */
object RepeatExpander {

    private const val REPEAT_ID_STRIDE = 10_000L

    fun expand(groups: List<PanelGroup>, variables: List<Variable>): List<PanelGroup> {
        val byName = variables.associateBy { it.name }
        return groups.map { g ->
            val expanded = g.panels.flatMap { p -> expandPanel(p, byName) }
            g.copy(panels = expanded)
        }
    }

    private fun expandPanel(panel: Panel, vars: Map<String, Variable>): List<Panel> {
        val repeatName = panel.repeat ?: return listOf(panel)
        val v = vars[repeatName] ?: return listOf(panel)
        val values = v.current.filter { it.isNotBlank() && it != "\$__all" }
        if (values.isEmpty()) return listOf(panel)
        return values.mapIndexed { i, value ->
            panel.copy(
                id = panel.id * REPEAT_ID_STRIDE + i,
                title = if (panel.title.isBlank()) value else "${panel.title} - $value",
                repeatValue = value,
            )
        }
    }
}
