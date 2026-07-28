package com.example.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ExerciseBodyRegion
import com.example.data.model.ExerciseCatalogLocationFilter
import com.example.data.model.ExerciseEquipmentType
import com.example.data.model.Muscle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseCatalogScreen(
    viewModel: ExerciseCatalogViewModel,
    initialTrainingPlace: String?,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(initialTrainingPlace) {
        initialTrainingPlace?.let(viewModel::applyInitialTrainingPlace)
    }

    Scaffold(
        modifier = Modifier.testTag("exercise_catalog_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Catálogo de ejercicios") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                label = { Text("Buscar ejercicio") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("exercise_catalog_search")
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ExerciseCatalogLocationFilter.entries) { location ->
                    FilterChip(
                        selected = state.location == location,
                        onClick = { viewModel.selectLocation(location) },
                        label = { Text(location.label()) },
                        modifier = Modifier.testTag("catalog_location_${location.name.lowercase()}")
                    )
                }
            }

            LazyRow(
                modifier = Modifier.testTag("catalog_filter_body_region"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.bodyRegion == null,
                        onClick = { viewModel.selectBodyRegion(null) },
                        label = { Text("Región: Todas") },
                        modifier = Modifier.testTag("catalog_body_region_all")
                    )
                }
                items(ExerciseBodyRegion.entries) { region ->
                    FilterChip(
                        selected = state.bodyRegion == region,
                        onClick = { viewModel.selectBodyRegion(region) },
                        label = { Text(region.label()) },
                        modifier = Modifier.testTag("catalog_body_region_${region.name.lowercase()}")
                    )
                }
            }

            LazyRow(
                modifier = Modifier.testTag("catalog_filter_muscle"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.muscle == null,
                        onClick = { viewModel.selectMuscle(null) },
                        label = { Text("Músculo: Todos") },
                        modifier = Modifier.testTag("catalog_muscle_all")
                    )
                }
                items(Muscle.entries) { muscle ->
                    FilterChip(
                        selected = state.muscle == muscle,
                        onClick = { viewModel.selectMuscle(muscle) },
                        label = { Text(muscle.displayName) },
                        modifier = Modifier.testTag("catalog_muscle_${muscle.name.lowercase()}")
                    )
                }
            }

            LazyRow(
                modifier = Modifier.testTag("catalog_filter_primary_equipment"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.equipment == null,
                        onClick = { viewModel.selectEquipment(null) },
                        label = { Text("Equipo: Cualquiera") },
                        modifier = Modifier.testTag("catalog_primary_equipment_all")
                    )
                }
                items(ExerciseEquipmentType.entries) { equipment ->
                    FilterChip(
                        selected = state.equipment == equipment,
                        onClick = { viewModel.selectEquipment(equipment) },
                        label = { Text(equipment.label()) },
                        modifier = Modifier.testTag("catalog_primary_equipment_${equipment.name.lowercase()}")
                    )
                }
            }

            if (state.location == ExerciseCatalogLocationFilter.HOME) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = state.availableEquipment.isEmpty(),
                            onClick = viewModel::clearEquipment,
                            label = { Text("Sin equipo") },
                            modifier = Modifier.testTag("catalog_equipment_none")
                        )
                    }
                    item {
                        EquipmentChip(ExerciseEquipmentType.DUMBBELL, "Mancuernas", state, viewModel)
                    }
                    item {
                        EquipmentChip(ExerciseEquipmentType.BENCH, "Banco", state, viewModel)
                    }
                }
            }

            TextButton(
                onClick = viewModel::clearFilters,
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag("catalog_clear_filters")
            ) {
                Text("Limpiar filtros")
            }

            if (state.usingFallback) {
                Text(
                    text = "Catálogo ampliado no disponible. Mostrando catálogo local.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("exercise_catalog_fallback")
                )
            }

            when {
                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag("exercise_catalog_loading")
                )
                state.items.isEmpty() -> Text(
                    "No hay ejercicios para estos filtros.",
                    modifier = Modifier.testTag("exercise_catalog_empty")
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("exercise_catalog_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.items, key = { it.catalogId }) { item ->
                        ExerciseCatalogCard(item = item, onClick = { onOpenDetail(item.catalogId) })
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseCatalogDetailScreen(
    viewModel: ExerciseCatalogViewModel,
    catalogId: String,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(catalogId) { viewModel.loadDetail(catalogId) }

    Scaffold(
        modifier = Modifier.testTag("exercise_catalog_detail_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Detalle del ejercicio") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                }
            )
        }
    ) { padding ->
        val detail = state.detail
        if (state.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }
        } else if (detail == null) {
            Text("Ejercicio no disponible.", modifier = Modifier.padding(padding).padding(16.dp))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .testTag("exercise_catalog_detail_placeholder")
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FitnessCenter,
                                contentDescription = "Vista previa del ejercicio no disponible",
                                modifier = Modifier.size(64.dp)
                            )
                            Text("Vista previa no disponible", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Text(
                        detail.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("exercise_catalog_detail_name")
                    )
                }
                item { Text("Músculo principal: ${detail.primaryMuscle}") }
                if (detail.secondaryMuscles.isNotEmpty()) {
                    item { Text("Músculos secundarios: ${detail.secondaryMuscles.joinToString()}") }
                }
                item {
                    Text(
                        "Región corporal: ${detail.bodyRegion}",
                        modifier = Modifier.testTag("exercise_catalog_detail_body_region")
                    )
                }
                item { Text("Equipo principal: ${detail.equipment}") }
                item {
                    val requirements = detail.equipmentRequirements
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString()
                        ?: "Ninguno"
                    Text(
                        "Requisitos físicos: $requirements",
                        modifier = Modifier.testTag("exercise_catalog_detail_requirements")
                    )
                }
                item {
                    Text(
                        "Compatibilidad: ${detail.locationCompatibility}",
                        modifier = Modifier.testTag("exercise_catalog_detail_compatibility")
                    )
                }
                item {
                    Text(
                        "Instrucciones",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        detail.instructions,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("exercise_catalog_detail_instructions")
                    )
                }
            }
        }
    }
}

@Composable
private fun EquipmentChip(
    equipment: ExerciseEquipmentType,
    label: String,
    state: ExerciseCatalogUiState,
    viewModel: ExerciseCatalogViewModel
) {
    FilterChip(
        selected = equipment in state.availableEquipment,
        onClick = { viewModel.toggleEquipment(equipment) },
        label = { Text(label) },
        modifier = Modifier.testTag("catalog_equipment_${equipment.name.lowercase()}")
    )
}

@Composable
private fun ExerciseCatalogCard(item: ExerciseCatalogUiItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("catalog_item_${item.catalogId}")
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.primaryMuscle, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(8.dp))
                Text("• ${item.equipment}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun ExerciseCatalogLocationFilter.label(): String = when (this) {
    ExerciseCatalogLocationFilter.ANY -> "Cualquiera"
    ExerciseCatalogLocationFilter.HOME -> "Casa"
    ExerciseCatalogLocationFilter.GYM -> "Gimnasio"
}

private fun ExerciseBodyRegion.label(): String = when (this) {
    ExerciseBodyRegion.BACK -> "Espalda"
    ExerciseBodyRegion.CARDIO -> "Cardio"
    ExerciseBodyRegion.CHEST -> "Pecho"
    ExerciseBodyRegion.CORE -> "Core"
    ExerciseBodyRegion.FOREARMS -> "Antebrazos"
    ExerciseBodyRegion.LOWER_LEGS -> "Piernas inferiores"
    ExerciseBodyRegion.NECK -> "Cuello"
    ExerciseBodyRegion.SHOULDERS -> "Hombros"
    ExerciseBodyRegion.UPPER_ARMS -> "Brazos"
    ExerciseBodyRegion.UPPER_LEGS -> "Piernas superiores"
}

private fun ExerciseEquipmentType.label(): String = name
    .lowercase()
    .replace('_', ' ')
    .replaceFirstChar { it.titlecase() }
