package com.example.ninjaau.ui.floating

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ninjaau.core.GameManager
import com.example.ninjaau.core.util.BountyConfigStorage
import com.example.ninjaau.model.BountyConfig
import androidx.compose.ui.platform.LocalContext

@Composable
fun BountyCheckList(
    configs: List<BountyConfig>,
    onConfigsChanged: (List<BountyConfig>) -> Unit
) {
    var editableConfigs by remember(configs) { mutableStateOf(configs) }
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(editableConfigs) { config ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = config.enabled,
                    onCheckedChange = { checked ->
                        editableConfigs = editableConfigs.map {
                            if (it.grade == config.grade) it.copy(enabled = checked) else it
                        }
                        onConfigsChanged(editableConfigs)
                        GameManager.updateBountyConfigs(editableConfigs)
                        BountyConfigStorage.save(context, editableConfigs)
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = config.grade.displayName,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (config.grade.canChaseDream && config.enabled) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (config.chaseDream) "追梦 ON" else "追梦",
                        fontSize = 11.sp,
                        color = if (config.chaseDream) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.clickable {
                            editableConfigs = editableConfigs.map {
                                if (it.grade == config.grade) it.copy(chaseDream = !it.chaseDream)
                                else it
                            }
                            onConfigsChanged(editableConfigs)
                            BountyConfigStorage.save(context, editableConfigs)
                        }
                    )
                }
            }
        }
    }
}
