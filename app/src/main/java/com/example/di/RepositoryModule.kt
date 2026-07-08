package com.example.di

import com.example.features.gym.data.repository.GymRepositoryImpl
import com.example.features.gym.domain.repository.GymRepository
import com.example.features.musclemap.data.repository.MuscleMapRepositoryImpl
import com.example.features.musclemap.domain.repository.MuscleMapRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMuscleMapRepository(
        impl: MuscleMapRepositoryImpl
    ): MuscleMapRepository

    @Binds
    @Singleton
    abstract fun bindGymRepository(
        impl: GymRepositoryImpl
    ): GymRepository
}
