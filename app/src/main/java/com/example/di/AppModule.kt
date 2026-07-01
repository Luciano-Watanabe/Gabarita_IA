package com.example.di

import com.example.data.database.AppDatabase
import com.example.data.repository.StudyRepository
import com.example.data.TokenManager
import com.example.ui.viewmodel.StudyViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Database
    single { AppDatabase.getDatabase(androidContext()) }
    single { get<AppDatabase>().studyDao() }
    
    // Repository
    single { StudyRepository(get()) }
    
    // Token Manager
    single { TokenManager(androidContext()) }
    
    // Network
    single { com.example.data.api.RetrofitClient.service }
    
    // ViewModels
    viewModel { StudyViewModel(androidApplication(), get(), get(), get()) }
}
