package com.example.ninjaau.ui.floating

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ninjaau.core.GameManager
import com.example.ninjaau.model.BountyConfig

@Composable
fun BountyCheckList(
    configs: List<BountyConfig>,
    onConfigsChanged: (List<BountyConfig>) -> Unit
) {
    var editableConfigs by remember(configs) { mutableStateOf(configs) }

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
                            if (it.id == config.id) it.copy(enabled = checked) else it
                        }
                        onConfigsChanged(editableConfigs)
                        GameManager.updateBountyConfigs(editableConfigs)
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = config.name,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
