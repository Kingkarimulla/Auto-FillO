package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CategoryType
import com.example.data.repository.DecryptedField
import com.example.security.EncryptionManager
import com.example.ui.viewmodel.FormFillViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDataScreen(viewModel: FormFillViewModel) {
    val profiles by viewModel.allProfiles.collectAsState()
    val activeProfileId by viewModel.activeProfileId.collectAsState()
    val fields by viewModel.activeDecryptedFields.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<CategoryType?>(null) }

    // Track expanded status for categories
    val expandedCategories = remember {
        mutableStateMapOf<CategoryType, Boolean>().apply {
            CategoryType.values().forEach { this[it] = true }
        }
    }

    // Track visible unmasked state for items
    val unmaskedItems = remember { mutableStateMapOf<Long, Boolean>() }

    // Dialog State
    var showAddFieldDialog by remember { mutableStateOf(false) }
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var editingFieldId by remember { mutableStateOf<Long?>(null) }

    var targetCategory by remember { mutableStateOf(CategoryType.PRIMARY) }
    var inputKey by remember { mutableStateOf("") }
    var inputLabel by remember { mutableStateOf("") }
    var inputValue by remember { mutableStateOf("") }
    var inputIsSensitive by remember { mutableStateOf(false) }

    var newProfileName by remember { mutableStateOf("") }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingFieldId = null
                    inputKey = ""
                    inputLabel = ""
                    inputValue = ""
                    inputIsSensitive = false
                    showAddFieldDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Field")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            // Header & Batch Profile Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Vault Data",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                TextButton(onClick = { showAddProfileDialog = true }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "New Profile")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Profile", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Profile Tabs
            if (profiles.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = profiles.indexOfFirst { it.id == activeProfileId }.coerceAtLeast(0),
                    edgePadding = 0.dp
                ) {
                    profiles.forEach { p ->
                        Tab(
                            selected = p.id == activeProfileId,
                            onClick = { viewModel.setActiveProfile(p.id) },
                            text = {
                                Text(
                                    text = p.profileName,
                                    fontWeight = if (p.id == activeProfileId) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Filter Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search fields, labels, values...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category Collapsible Accordions
            val filteredFields = fields.filter { f ->
                searchQuery.isBlank() ||
                        f.fieldLabel.contains(searchQuery, ignoreCase = true) ||
                        f.fieldValue.contains(searchQuery, ignoreCase = true) ||
                        f.category.displayName.contains(searchQuery, ignoreCase = true)
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                CategoryType.values().forEach { category ->
                    val catFields = filteredFields.filter { it.category == category }

                    if (searchQuery.isBlank() || catFields.isNotEmpty()) {
                        item {
                            val isExpanded = expandedCategories[category] ?: true

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { expandedCategories[category] = !isExpanded },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = getCategoryIcon(category),
                                                contentDescription = category.displayName,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = category.displayName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "(${catFields.size})",
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    targetCategory = category
                                                    inputKey = ""
                                                    inputLabel = ""
                                                    inputValue = ""
                                                    inputIsSensitive = category == CategoryType.BANK || category == CategoryType.GOVERNMENT
                                                    showAddFieldDialog = true
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Add",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = "Toggle"
                                            )
                                        }
                                    }

                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(
                                            modifier = Modifier.padding(top = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (catFields.isEmpty()) {
                                                Text(
                                                    text = "No saved fields in ${category.displayName}. Tap '+' to add.",
                                                    fontSize = 12.sp,
                                                    color = Color.Gray,
                                                    modifier = Modifier.padding(vertical = 6.dp)
                                                )
                                            } else {
                                                catFields.forEach { field ->
                                                    val isUnmasked = unmaskedItems[field.id] ?: false
                                                    val displayVal = if (field.isSensitive && !isUnmasked) {
                                                        EncryptionManager.maskValue(field.fieldValue)
                                                    } else {
                                                        field.fieldValue
                                                    }

                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(
                                                                MaterialTheme.colorScheme.background,
                                                                shape = RoundedCornerShape(10.dp)
                                                            )
                                                            .padding(12.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = field.fieldLabel,
                                                                fontSize = 11.sp,
                                                                color = Color.Gray
                                                            )
                                                            Text(
                                                                text = displayVal,
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                color = MaterialTheme.colorScheme.onBackground
                                                            )
                                                        }

                                                        Row {
                                                            if (field.isSensitive) {
                                                                IconButton(
                                                                    onClick = { unmaskedItems[field.id] = !isUnmasked },
                                                                    modifier = Modifier.size(32.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = if (isUnmasked) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                                        contentDescription = "Mask",
                                                                        modifier = Modifier.size(18.dp)
                                                                    )
                                                                }
                                                            }

                                                            IconButton(
                                                                onClick = {
                                                                    editingFieldId = field.id
                                                                    targetCategory = field.category
                                                                    inputKey = field.fieldKey
                                                                    inputLabel = field.fieldLabel
                                                                    inputValue = field.fieldValue
                                                                    inputIsSensitive = field.isSensitive
                                                                    showAddFieldDialog = true
                                                                },
                                                                modifier = Modifier.size(32.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Edit,
                                                                    contentDescription = "Edit Field",
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }

                                                            IconButton(
                                                                onClick = { viewModel.deleteField(field.id) },
                                                                modifier = Modifier.size(32.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Delete,
                                                                    contentDescription = "Delete",
                                                                    tint = MaterialTheme.colorScheme.error,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Field Dialog
    if (showAddFieldDialog) {
        AlertDialog(
            onDismissRequest = { showAddFieldDialog = false },
            title = { Text(if (editingFieldId != null) "Edit Data Field" else "Add New Data Field", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Category: ${targetCategory.displayName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

                    OutlinedTextField(
                        value = inputLabel,
                        onValueChange = { inputLabel = it },
                        label = { Text("Field Label (e.g. Username, Password, IFSC)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inputValue,
                        onValueChange = { inputValue = it },
                        label = { Text("Value to Auto-Fill") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = inputIsSensitive,
                            onCheckedChange = { inputIsSensitive = it }
                        )
                        Text("Mask Sensitive Value (Encrypt)", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputLabel.isNotBlank() && inputValue.isNotBlank()) {
                            viewModel.saveOrUpdateField(
                                fieldId = editingFieldId ?: 0L,
                                category = targetCategory,
                                key = inputKey,
                                label = inputLabel,
                                value = inputValue,
                                isSensitive = inputIsSensitive
                            )
                            showAddFieldDialog = false
                        }
                    }
                ) {
                    Text(if (editingFieldId != null) "Update Field" else "Save to Vault")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFieldDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Profile Dialog
    if (showAddProfileDialog) {
        AlertDialog(
            onDismissRequest = { showAddProfileDialog = false },
            title = { Text("Create New Profile Batch") },
            text = {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    label = { Text("Profile Name (e.g. Job Applications)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProfileName.isNotBlank()) {
                            viewModel.addNewProfile(newProfileName)
                            newProfileName = ""
                            showAddProfileDialog = false
                        }
                    }
                ) {
                    Text("Create Profile")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

fun getCategoryIcon(category: CategoryType): ImageVector {
    return when (category) {
        CategoryType.PRIMARY -> Icons.Default.Person
        CategoryType.ADDRESS -> Icons.Default.LocationOn
        CategoryType.EDUCATION -> Icons.Default.School
        CategoryType.BANK -> Icons.Default.AccountBalance
        CategoryType.EMPLOYMENT -> Icons.Default.Work
        CategoryType.FAMILY -> Icons.Default.People
        CategoryType.GOVERNMENT -> Icons.Default.Badge
        CategoryType.CUSTOM -> Icons.Default.Tune
    }
}
