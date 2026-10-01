--- app/src/main/java/com/skippy/app/Screens.kt
+++ app/src/main/java/com/skippy/app/Screens.kt
@@ -35,6 +35,8 @@
 import androidx.compose.material.icons.automirrored.filled.List as ListIcon
 import androidx.compose.material.icons.filled.DateRange
 import androidx.compose.material.icons.filled.Home
+import androidx.compose.material.icons.filled.Search
+import androidx.compose.material.icons.filled.Close
+import androidx.compose.material.icons.filled.Check
 import androidx.compose.material.icons.filled.Settings
 import androidx.compose.material3.AlertDialog
 import androidx.compose.material3.Button
@@ -62,6 +64,9 @@
 import androidx.compose.material3.TextButton
 import androidx.compose.material3.ExposedDropdownMenuBox
 import androidx.compose.material3.DropdownMenuItem
+import androidx.compose.material3.Checkbox
+import androidx.compose.material3.IconButton
 import androidx.compose.material3.MenuAnchorType
 import androidx.compose.material3.ExposedDropdownMenuDefaults
 import androidx.compose.material3.lightColorScheme
@@ -769,14 +774,9 @@
 fun SettingsScreen(ui: UiState, vm: AppViewModel, onSignIn: () -> Unit) {
     LaunchedEffect(Unit) { vm.fetchGroupsIfNeeded() }
     var d by remember(ui.settings) { mutableStateOf(ui.settings) }
     
-    var expanded by remember { mutableStateOf(false) }
-    var search by remember(ui.settings.groupId, ui.availableGroups) {
-        val selected = ui.availableGroups.find { it.id == ui.settings.groupId }
-        mutableStateOf(selected?.let { "${it.name}" + (it.path?.let { p -> " ($p)" } ?: "") } ?: ui.settings.groupId.toString())
-    }
+    var showSearchDialog by remember { mutableStateOf(false) }
 
     Column(
         Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
         verticalArrangement = Arrangement.spacedBy(14.dp),
     ) {
         AuthSection(ui, vm, onSignIn = onSignIn)
-        ExposedDropdownMenuBox(
-            expanded = expanded,
-            onExpandedChange = { expanded = it }
-        ) {
-            OutlinedTextField(
-                value = search,
-                onValueChange = { search = it; expanded = true },
-                label = { Text(stringResource(R.string.group_id)) },
-                singleLine = true,
-                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable, true),
-                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
-                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
-            )
-            ExposedDropdownMenu(
-                expanded = expanded,
-                onDismissRequest = { expanded = false }
-            ) {
-                val filtered = ui.availableGroups.filter { search.isBlank() || it.name.contains(search, ignoreCase = true) || it.path?.contains(search, ignoreCase = true) == true }.take(20)
-                filtered.forEach { g ->
-                    val text = "${g.name}" + (g.path?.let { " ($it)" } ?: "")
-                    DropdownMenuItem(
-                        text = { Text(text) },
-                        onClick = { 
-                            search = text
-                            d = d.copy(groupId = g.id)
-                            expanded = false 
-                        }
-                    )
-                }
-            }
-        }
+        Row(
+            Modifier.fillMaxWidth().clickable { showSearchDialog = true }.padding(vertical = 12.dp),
+            verticalAlignment = Alignment.CenterVertically
+        ) {
+            Column(Modifier.weight(1f)) {
+                Text("Groupes", style = MaterialTheme.typography.bodyLarge)
+                Text("${d.groupIds.size} sélectionné(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
+            }
+        }
         OutlinedTextField(
             value = d.rentree,
@@ -910,6 +888,57 @@
             Text(stringResource(R.string.sync_full))
         }
     }
+    
+    if (showSearchDialog) {
+        SearchGroupsDialog(
+            availableGroups = ui.availableGroups,
+            initialSelection = d.groupIds,
+            onDismiss = { showSearchDialog = false },
+            onSave = { selectedIds ->
+                d = d.copy(groupIds = selectedIds)
+                vm.saveSettings(d)
+                showSearchDialog = false
+            }
+        )
+    }
 }
 
+@Composable
+fun SearchGroupsDialog(
+    availableGroups: List<ApiGroup>,
+    initialSelection: Set<Int>,
+    onDismiss: () -> Unit,
+    onSave: (Set<Int>) -> Unit
+) {
+    var search by remember { mutableStateOf("") }
+    var selected by remember { mutableStateOf(initialSelection) }
+
+    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
+        Scaffold(
+            topBar = {
+                TopAppBar(
+                    title = {
+                        TextField(
+                            value = search,
+                            onValueChange = { search = it },
+                            placeholder = { Text("Recherche") },
+                            singleLine = true,
+                            colors = androidx.compose.material3.TextFieldDefaults.colors(
+                                focusedContainerColor = Color.Transparent,
+                                unfocusedContainerColor = Color.Transparent,
+                                focusedIndicatorColor = Color.Transparent,
+                                unfocusedIndicatorColor = Color.Transparent
+                            )
+                        )
+                    },
+                    navigationIcon = {
+                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
+                    },
+                    actions = {
+                        IconButton(onClick = { onSave(selected) }) { Icon(Icons.Default.Check, contentDescription = "Valider") }
+                    }
+                )
+            }
+        ) { padding ->
+            val filtered = remember(search, availableGroups) {
+                availableGroups.filter { search.isBlank() || it.name.contains(search, ignoreCase = true) || it.path?.contains(search, ignoreCase = true) == true }.take(100)
+            }
+            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
+                items(filtered) { g ->
+                    val isSelected = selected.contains(g.id)
+                    Row(
+                        Modifier.fillMaxWidth().clickable {
+                            selected = if (isSelected) selected - g.id else selected + g.id
+                        }.padding(16.dp),
+                        verticalAlignment = Alignment.CenterVertically
+                    ) {
+                        Column(Modifier.weight(1f)) {
+                            Text(g.name, style = MaterialTheme.typography.bodyLarge)
+                            if (!g.path.isNullOrBlank()) {
+                                Text(g.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
+                            }
+                        }
+                        Checkbox(checked = isSelected, onCheckedChange = null)
+                    }
+                }
+            }
+        }
+    }
+}
+
