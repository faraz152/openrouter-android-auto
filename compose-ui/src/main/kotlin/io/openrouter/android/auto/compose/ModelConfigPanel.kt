package io.openrouter.android.auto.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.openrouter.android.auto.ParameterDef
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Dynamic parameter configuration form generated from [ParameterDef] definitions.
 *
 * - Slider for number params with min/max
 * - OutlinedTextField for integer/number without bounds
 * - Switch for boolean params
 * - Inline validation error text below each field
 */
@Composable
fun ModelConfigPanel(
    modelId: String,
    parameters: Map<String, JsonElement>,
    parameterDefs: Map<String, ParameterDef>,
    onSave: (Map<String, JsonElement>) -> Unit,
    modifier: Modifier = Modifier,
    onTest: (() -> Unit)? = null,
    validationErrors: Map<String, String> = emptyMap()
) {
    var editedParams by remember(parameters) { mutableStateOf(parameters.toMutableMap()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = modelId,
            style = MaterialTheme.typography.titleMedium
        )

        parameterDefs.forEach { (name, def) ->
            ParameterInput(
                name = name,
                def = def,
                value = editedParams[name],
                onValueChange = { newVal ->
                    editedParams = editedParams.toMutableMap().apply { put(name, newVal) }
                },
                error = validationErrors[name]
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            if (onTest != null) {
                OutlinedButton(onClick = onTest) {
                    Text("Test")
                }
            }
            Button(onClick = { onSave(editedParams) }) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun ParameterInput(
    name: String,
    def: ParameterDef,
    value: JsonElement?,
    onValueChange: (JsonElement) -> Unit,
    error: String?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge
        )
        def.description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when (def.type) {
            "boolean" -> {
                val checked = (value as? JsonPrimitive)?.booleanOrNull ?: false
                Switch(
                    checked = checked,
                    onCheckedChange = { onValueChange(JsonPrimitive(it)) }
                )
            }

            "number", "integer" -> {
                val hasRange = def.min != null && def.max != null
                if (hasRange) {
                    val min = def.min!!.toFloat()
                    val max = def.max!!.toFloat()
                    val current = (value as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: min
                    val step = if (def.type == "integer") 1f else (max - min) / 100f

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Slider(
                            value = current.coerceIn(min, max),
                            onValueChange = { newVal ->
                                val v = if (def.type == "integer") newVal.toInt().toDouble() else newVal.toDouble()
                                onValueChange(JsonPrimitive(v))
                            },
                            valueRange = min..max,
                            steps = if (def.type == "integer") (max - min).toInt() - 1 else 0,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (def.type == "integer") current.toInt().toString()
                            else "%.2f".format(current),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    val text = when {
                        value == null -> ""
                        def.type == "integer" -> (value as? JsonPrimitive)?.intOrNull?.toString() ?: ""
                        else -> (value as? JsonPrimitive)?.doubleOrNull?.toString() ?: ""
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { input ->
                            val parsed = if (def.type == "integer") {
                                input.toIntOrNull()?.let { JsonPrimitive(it) }
                            } else {
                                input.toDoubleOrNull()?.let { JsonPrimitive(it) }
                            }
                            if (parsed != null) onValueChange(parsed)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(name) }
                    )
                }
            }

            else -> {
                val text = (value as? JsonPrimitive)?.content ?: ""
                OutlinedTextField(
                    value = text,
                    onValueChange = { onValueChange(JsonPrimitive(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(name) }
                )
            }
        }

        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = OpenRouterTheme.ErrorColor,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}
