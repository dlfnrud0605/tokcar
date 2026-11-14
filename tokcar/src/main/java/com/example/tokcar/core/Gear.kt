package com.example.tokcar.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** 차의 기어. 앞으로 가는 기어(D, 수동 1~9단)는 모두 [DRIVE]로 본다. */
enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

/** 차의 기어를 알려 주는 곳. 실제는 Vehicle SDK([com.example.tokcar.vehicle.VehicleGearSource]). */
interface GearSource {
    val gear: Flow<Gear>
}

/** 기어를 알 수 없는 환경(테스트 등). */
object NoGearSource : GearSource {
    override val gear: Flow<Gear> = emptyFlow()
}
