package com.ahmadarif.gdface.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ahmadarif.gdface.sample.Person
import com.ahmadarif.gdface.sample.gdApp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private enum class SortOrder(val label: String) {
    Newest("Newest first"),
    Oldest("Oldest first"),
    Name("Name A–Z")
}

@Composable
fun FaceListScreen(nav: NavController) {
    val faces = LocalContext.current.gdApp.faces
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(SortOrder.Newest) }
    var sortMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Person?>(null) }
    var deleting by remember { mutableStateOf<Person?>(null) }

    val shown = faces.persons
        .filter { it.name.contains(query.trim(), ignoreCase = true) }
        .let {
            when (sort) {
                SortOrder.Newest -> it.sortedByDescending { p -> p.createdAt }
                SortOrder.Oldest -> it.sortedBy { p -> p.createdAt }
                SortOrder.Name -> it.sortedBy { p -> p.name.lowercase() }
            }
        }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Face Database", onBack = { nav.popBackStack() })
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search name...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Gd.TextSecondary) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Gd.TextSecondary)
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = gdTextFieldColors(),
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { sortMenu = true }) {
                    Icon(Icons.Default.FilterList, contentDescription = "Sort", tint = Gd.TextPrimary)
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.label, fontWeight = if (order == sort) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { sort = order; sortMenu = false }
                        )
                    }
                }
            }
        }
        Gap(12)
        Text(
            "${shown.size} ${if (shown.size == 1) "face" else "faces"}",
            color = Gd.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Gap(8)

        if (faces.persons.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No faces yet", color = Gd.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Gap(6)
                Text("Enroll a face and it will show up here.", color = Gd.TextSecondary, textAlign = TextAlign.Center)
                Gap(20)
                PrimaryButton("Enroll Face", { nav.navigate("enroll") })
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(shown, key = { it.id }) { person ->
                    PersonRow(
                        person = person,
                        onAddPhoto = { nav.navigate("enroll?personId=${person.id}") },
                        onRename = { renaming = person },
                        onDelete = { deleting = person }
                    )
                }
            }
        }
    }

    renaming?.let { person ->
        var text by remember(person.id) { mutableStateOf(person.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = Gd.Surface,
            title = { Text("Rename", color = Gd.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(60) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = gdTextFieldColors()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        scope.launch { faces.rename(person, text) }
                        renaming = null
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } }
        )
    }

    deleting?.let { person ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Gd.Surface,
            title = { Text("Delete ${person.name}?", color = Gd.TextPrimary) },
            text = {
                Text(
                    "This removes ${person.images.size} ${if (person.images.size == 1) "photo" else "photos"} and the face data. It cannot be undone.",
                    color = Gd.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { faces.delete(person) }
                    deleting = null
                }) { Text("Delete", color = Gd.Danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PersonRow(person: Person, onAddPhoto: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val faces = LocalContext.current.gdApp.faces
    var menu by remember { mutableStateOf(false) }
    val added = remember(person.createdAt) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(person.createdAt))
    }

    GdCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FaceImageView(
                person.images.firstOrNull()?.let { faces.photoFile(it) },
                Modifier.size(58.dp),
                shape = RoundedCornerShape(14.dp)
            )
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(person.name, color = Gd.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Added $added", color = Gd.TextSecondary, fontSize = 13.sp)
                Text(
                    "${person.images.size} ${if (person.images.size == 1) "image" else "images"}",
                    color = Gd.TextSecondary,
                    fontSize = 13.sp
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Gd.TextSecondary)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Add photo") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = { menu = false; onAddPhoto() }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { menu = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Gd.Danger) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Gd.Danger) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
    }
}
