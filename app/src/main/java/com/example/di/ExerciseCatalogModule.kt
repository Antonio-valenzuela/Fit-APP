package com.example.di

import com.example.data.local.AppDatabase
import com.example.data.local.ExerciseCatalogBootstrapper
import com.example.data.repository.ExerciseCatalogReader
import com.example.data.repository.ExerciseCatalogRepository
import com.example.data.repository.RoomExerciseCatalogReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Makes the catalog boundary injectable without activating bootstrap or changing UI consumers. */
@Module
@InstallIn(SingletonComponent::class)
object ExerciseCatalogModule {
    @Provides
    @Singleton
    fun provideExerciseCatalogBootstrapper(database: AppDatabase): ExerciseCatalogBootstrapper =
        ExerciseCatalogBootstrapper(database)

    @Provides
    @Singleton
    fun provideExerciseCatalogReader(database: AppDatabase): ExerciseCatalogReader =
        RoomExerciseCatalogReader(database.exerciseCatalogDao())

    @Provides
    @Singleton
    fun provideExerciseCatalogRepository(reader: ExerciseCatalogReader): ExerciseCatalogRepository =
        ExerciseCatalogRepository(reader)
}
