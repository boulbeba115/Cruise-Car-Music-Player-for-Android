package com.boulbeba.cruise.feature.settings.steering

import androidx.lifecycle.ViewModel
import com.boulbeba.cruise.core.car.HeadUnitManager
import com.boulbeba.cruise.core.car.calls.CallController
import com.boulbeba.cruise.core.car.steering.KeyAction
import com.boulbeba.cruise.core.car.steering.KeyMappingStore
import com.boulbeba.cruise.core.car.steering.PressType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SteeringViewModel @Inject constructor(
    val headUnit: HeadUnitManager,
    val mapping: KeyMappingStore,
    val calls: CallController,
) : ViewModel() {

    init {
        // Listen to the MCU even when nothing is playing, so buttons can be learned.
        headUnit.start()
    }

    fun set(code: Int, press: PressType, action: KeyAction) = mapping.set(code, press, action)

    fun setSmartCall(enabled: Boolean) {
        mapping.smartCallButton = enabled
    }
}
