import sys

with open('app/src/main/java/com/skippy/app/Screens.kt', 'r') as f:
    content = f.read()

imports_to_add = """
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
"""

# Find a good place to add imports
import_marker = "import androidx.compose.material.icons.filled.Home"
if import_marker in content:
    content = content.replace(import_marker, import_marker + imports_to_add)

old_dropdown_code = """    var expanded by remember { mutableStateOf(false) }
    var search by remember(ui.settings.groupId, ui.availableGroups) {
        val selected = ui.availableGroups.find { it.id == ui.settings.groupId }
        mutableStateOf(selected?.let { "${it.name}" + (it.path?.let { p -> " ($p)" } ?: "") } ?: ui.settings.groupId.toString())
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AuthSection(ui, vm, onSignIn = onSignIn)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it; expanded = true },
                label = { Text(stringResource(R.string.group_id)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable, true),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                val filtered = ui.availableGroups.filter { search.isBlank() || it.name.contains(search, ignoreCase = true) || it.path?.contains(search, ignoreCase = true) == true }.take(20)
                filtered.forEach { g ->
                    val text = "${g.name}" + (g.path?.let { " ($it)" } ?: "")
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = { 
                            search = text
                            d = d.copy(groupId = g.id)
                            expanded = false 
                        }
                    )
                }
            }
        }"""

new_search_button = """    var showSearchDialog by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AuthSection(ui, vm, onSignIn = onSignIn)
        Row(
            Modifier.fillMaxWidth().clickable { showSearchDialog = true }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Groupes", style = MaterialTheme.typography.bodyLarge)
                Text("${d.groupIds.size} sélectionné(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }"""

if old_dropdown_code in content:
    content = content.replace(old_dropdown_code, new_search_button)
else:
    print("Could not find old_dropdown_code")

# Add the dialog at the end of SettingsScreen
end_of_settings_marker = """    }
}"""
new_dialog_usage = """    }
    if (showSearchDialog) {
        SearchGroupsDialog(
            availableGroups = ui.availableGroups,
            initialSelection = d.groupIds,
            onDismiss = { showSearchDialog = false },
            onSave = { selectedIds ->
                d = d.copy(groupIds = selectedIds)
                vm.saveSettings(d)
                showSearchDialog = false
            }
        )
    }
}

@Composable
fun SearchGroupsDialog(
    availableGroups: List<ApiGroup>,
    initialSelection: Set<Int>,
    onDismiss: () -> Unit,
    onSave: (Set<Int>) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(initialSelection) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        TextField(
                            value = search,
                            onValueChange = { search = it },
                            placeholder = { Text("Recherche") },
                            singleLine = true,
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                    },
                    actions = {
                        IconButton(onClick = { onSave(selected) }) { Icon(Icons.Default.Check, contentDescription = "Valider") }
                    }
                )
            }
        ) { padding ->
            val filtered = remember(search, availableGroups) {
                availableGroups.filter { search.isBlank() || it.name.contains(search, ignoreCase = true) || it.path?.contains(search, ignoreCase = true) == true }.take(100)
            }
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(filtered) { g ->
                    val isSelected = selected.contains(g.id)
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selected = if (isSelected) selected - g.id else selected + g.id
                        }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.bodyLarge)
                            if (!g.path.isNullOrBlank()) {
                                Text(g.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Checkbox(checked = isSelected, onCheckedChange = null)
                    }
                }
            }
        }
    }
}"""

# Find the end of SettingsScreen
# It's right before "// Setup + authentication"
setup_marker = "// ---------------------------------------------------------------------------------------------\n// Setup + authentication"
# Let's just do a manual replace of the exact end
settings_end_to_replace = """        OutlinedButton(onClick = { vm.sync(true) }, enabled = !ui.syncing) {
            Text(stringResource(R.string.sync_full))
        }
    }
}"""

if settings_end_to_replace in content:
    content = content.replace(settings_end_to_replace, settings_end_to_replace.replace("    }\n}", new_dialog_usage))
else:
    print("Could not find settings_end_to_replace")

with open('app/src/main/java/com/skippy/app/Screens.kt', 'w') as f:
    f.write(content)
