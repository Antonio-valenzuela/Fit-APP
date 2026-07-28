package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.ExerciseBodyRegion
import com.example.data.model.ExerciseEquipmentType
import com.example.data.model.ExerciseImportStatus
import com.example.data.model.ExerciseLocationCompatibility
import com.example.data.model.Muscle

class ExerciseCatalogConverters {
    @TypeConverter fun muscleToString(value: Muscle?): String? = value?.name
    @TypeConverter fun stringToMuscle(value: String?): Muscle? = value?.let(Muscle::valueOf)
    @TypeConverter fun bodyRegionToString(value: ExerciseBodyRegion?): String? = value?.name
    @TypeConverter fun stringToBodyRegion(value: String?): ExerciseBodyRegion? = value?.let(ExerciseBodyRegion::valueOf)
    @TypeConverter fun equipmentToString(value: ExerciseEquipmentType?): String? = value?.name
    @TypeConverter fun stringToEquipment(value: String?): ExerciseEquipmentType? = value?.let(ExerciseEquipmentType::valueOf)
    @TypeConverter fun locationToString(value: ExerciseLocationCompatibility?): String? = value?.name
    @TypeConverter fun stringToLocation(value: String?): ExerciseLocationCompatibility? = value?.let(ExerciseLocationCompatibility::valueOf)
    @TypeConverter fun statusToString(value: ExerciseImportStatus?): String? = value?.name
    @TypeConverter fun stringToStatus(value: String?): ExerciseImportStatus? = value?.let(ExerciseImportStatus::valueOf)
}
