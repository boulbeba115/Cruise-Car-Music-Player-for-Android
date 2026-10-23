package com.boulbeba.cruise.core.car.di

import com.boulbeba.cruise.core.car.calls.CallControl
import com.boulbeba.cruise.core.car.calls.CallController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CarModule {
    @Binds abstract fun callControl(impl: CallController): CallControl
}
