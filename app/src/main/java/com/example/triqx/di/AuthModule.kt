package com.example.triqx.di

import com.example.triqx.data.remote.OtpAuthService
import com.example.triqx.data.remote.RealOtpAuthService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindOtpAuthService(
        realOtpAuthService: RealOtpAuthService
    ): OtpAuthService
}
