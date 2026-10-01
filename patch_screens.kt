--- app/src/main/java/com/skippy/app/Screens.kt
+++ app/src/main/java/com/skippy/app/Screens.kt
@@ -47,6 +47,8 @@
 import androidx.compose.material3.Button
 import androidx.compose.material3.Card
 import androidx.compose.material3.CardDefaults
+import androidx.compose.material3.ExposedDropdownMenuBox
+import androidx.compose.material3.DropdownMenuItem
+import androidx.compose.material3.MenuAnchorType
 import androidx.compose.material3.CircularProgressIndicator
 import androidx.compose.material3.ExperimentalMaterial3Api
 import androidx.compose.material3.FilterChip
@@ -62,6 +64,7 @@
 import androidx.compose.material3.TextButton
 import androidx.compose.material3.TextField
 import androidx.compose.material3.TopAppBar
+import androidx.compose.material3.ExposedDropdownMenuDefaults
 import androidx.compose.material3.TopAppBarDefaults
 import androidx.compose.runtime.Composable
 import androidx.compose.runtime.LaunchedEffect
@@ -762,23 +765,47 @@
 
 @Composable
 fun SettingsScreen(ui: UiState, vm: AppViewModel, onSignIn: () -> Unit) {
+    LaunchedEffect(Unit) { vm.fetchGroupsIfNeeded() }
     var d by remember(ui.settings) { mutableStateOf(ui.settings) }
-    var groupText by remember(ui.settings) { mutableStateOf(ui.settings.groupId.toString()) }
+    
+    var expanded by remember { mutableStateOf(false) }
+    var search by remember(ui.settings.groupId, ui.availableGroups) {
+        val selected = ui.availableGroups.find { it.id == ui.settings.groupId }
+        mutableStateOf(selected?.let { "${it.name}" + (it.path?.let { p -> " ($p)" } ?: "") } ?: ui.settings.groupId.toString())
+    }
+    
     Column(
         Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
         verticalArrangement = Arrangement.spacedBy(14.dp),
     ) {
         AuthSection(ui, vm, onSignIn = onSignIn)
-        OutlinedTextField(
-            value = groupText,
-            onValueChange = { groupText = it.filter(Char::isDigit).take(8) },
-            label = { Text(stringResource(R.string.group_id)) },
-            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
-            singleLine = true,
-            modifier = Modifier.fillMaxWidth(),
-        )
+        
+        ExposedDropdownMenuBox(
+            expanded = expanded,
+            onExpandedChange = { expanded = it }
+        ) {
+            OutlinedTextField(
+                value = search,
+                onValueChange = { search = it; expanded = true },
+                label = { Text(stringResource(R.string.group_id)) },
+                singleLine = true,
+                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable, true),
+                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
+                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
+            )
+            ExposedDropdownMenu(
+                expanded = expanded,
+                onDismissRequest = { expanded = false }
+            ) {
+                val filtered = ui.availableGroups.filter { search.isBlank() || it.name.contains(search, ignoreCase = true) || it.path?.contains(search, ignoreCase = true) == true }.take(20)
+                filtered.forEach { g ->
+                    val text = "${g.name}" + (g.path?.let { " ($it)" } ?: "")
+                    DropdownMenuItem(text = { Text(text) }, onClick = { search = text; d = d.copy(groupId = g.id); expanded = false })
+                }
+            }
+        }
+        
         OutlinedTextField(
             value = d.rentree,
