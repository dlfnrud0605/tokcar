package com.example.tokcar.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.util.Log
import com.example.tokcar.core.Gear
import com.example.tokcar.core.GearSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Pleos Vehicle SDK로 기어를 읽는다. 지금 값을 한 번 읽고, 이후 바뀔 때마다 알림을 받는다.
 * 에뮬레이터에서는 Virtual Console 에서 기어를 바꿔 볼 수 있다. (이 SDK 함수는 deprecated 표시가 있지만 점검 앱에서 동작을 확인함)
 */
@Suppress("DEPRECATION")
class VehicleGearSource(private val context: Context) : GearSource {

    override val gear: Flow<Gear> = callbackFlow {
        val vehicle = Vehicle(context.applicationContext).also { it.initialize() }
        val listener = object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) {
                Log.i(TAG, "기어 알림: $gear")
                trySend(gear.toGear())
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "기어 알림 실패", e)
            }
        }
        vehicle.getGear().getCurrentGearSelectionState(
            { g -> Log.i(TAG, "기어 지금 값: $g"); trySend(g.toGear()) },
            { e -> Log.w(TAG, "기어 읽기 실패", e) },
        )
        vehicle.getGear().registerGearSelection(listener)
        awaitClose {
            runCatching { vehicle.getGear().unregisterGearSelection(listener) }
            runCatching { vehicle.release() }
        }
    }

    private fun VehicleGear?.toGear(): Gear = when (this) {
        VehicleGear.GEAR_DRIVE,
        VehicleGear.GEAR_1, VehicleGear.GEAR_2, VehicleGear.GEAR_3, VehicleGear.GEAR_4, VehicleGear.GEAR_5,
        VehicleGear.GEAR_6, VehicleGear.GEAR_7, VehicleGear.GEAR_8, VehicleGear.GEAR_9 -> Gear.DRIVE
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        else -> Gear.UNKNOWN
    }

    private companion object {
        const val TAG = "VehicleGear"
    }
}
