package com.engineering.uutcalc.domain

import kotlin.math.PI
import kotlin.math.pow

enum class EnergyUnit(val label: String, val toGcalRatio: Double) {
    GCAL_H("Гкал/ч", 1.0),
    KW("кВт", 1163.0),
    MW("МВт", 1.1630),
    GJ_H("ГДж/ч", 4.1868)
}

enum class PressureUnit(val label: String, val toBarRatio: Double) {
    BAR("бар", 1.0),
    KGS_CM2("кгс/см²", 1.01972),
    MPA("МПа", 0.1),
    KPA("кПа", 100.0)
}

data class CalculationResult(
    val qSumGcal: Double,
    val massFlowG: Double,
    val densityRho: Double,
    val volumeFlowV: Double,
    val velocityV: Double,
    val deltaPRash: Double,
    val recommendedDn: Int,
    val isAlmatyKm5Warning: Boolean = false,
    val needExpanderWarning: Boolean = false,
    val cableWarning: Boolean = false,
    val alertType: AlertType = AlertType.NONE,
    val alertMessage: String = ""
)

enum class AlertType { NONE, YELLOW_UNDERCOUNT, RED_OVERLOAD }

object EngineeringMath {

    fun calculateDensity(temperatureC: Double): Double {
        return 1000.0 - (0.018 * temperatureC) - (0.0038 * temperatureC.pow(2))
    }

    fun convertToGcal(value: Double, unit: EnergyUnit): Double {
        return value / unit.toGcalRatio
    }

    fun calculateHydraulics(
        qOt: Double,
        qVent: Double,
        qGvs: Double,
        energyUnit: EnergyUnit,
        t1: Double,
        t2: Double,
        selectedDnMm: Int,
        pipeDnMm: Int,
        kvs: Double,
        qMin: Double,
        qNom: Double,
        qMax: Double,
        isKm5Selected: Boolean,
        cableLengthM: Double
    ): CalculationResult {

        val qOtGcal = convertToGcal(qOt, energyUnit)
        val qVentGcal = convertToGcal(qVent, energyUnit)
        val qGvsGcal = convertToGcal(qGvs, energyUnit)
        val qSumGcal = qOtGcal + qVentGcal + qGvsGcal

        val deltaT = t1 - t2
        require(deltaT > 0) { "T1 должна быть строго больше T2" }

        val massFlowG = (qSumGcal / deltaT) * 1000.0
        val rho = calculateDensity(t1)
        val volumeFlowV = (massFlowG * 1000.0) / rho

        val dnMeters = selectedDnMm / 1000.0
        val area = (PI * dnMeters.pow(2)) / 4.0
        val velocityV = volumeFlowV / (area * 3600.0)

        val deltaPRash = if (kvs > 0) (volumeFlowV / kvs).pow(2) else 0.0

        var alertType = AlertType.NONE
        var alertMessage = ""

        if (volumeFlowV > qMax || velocityV > 2.5 || deltaPRash > 0.1) {
            alertType = AlertType.RED_OVERLOAD
            alertMessage = "Внимание: Диаметр DN занижен! Высокая скорость потока (v = %.2f м/с) и потери давления (ΔP = %.3f бар)."
                .format(velocityV, deltaPRash)
        } else if (volumeFlowV < qMin || volumeFlowV < (0.3 * qNom)) {
            alertType = AlertType.YELLOW_UNDERCOUNT
            alertMessage = "Внимание: Диаметр DN завышен! При минимальном водоразборе расход упадет ниже порога чувствительности Q_min."
        }

        val needExpander = (selectedDnMm * 0.8) > (0.7 * pipeDnMm)

        return CalculationResult(
            qSumGcal = qSumGcal,
            massFlowG = massFlowG,
            densityRho = rho,
            volumeFlowV = volumeFlowV,
            velocityV = velocityV,
            deltaPRash = deltaPRash,
            recommendedDn = selectedDnMm,
            isAlmatyKm5Warning = isKm5Selected,
            needExpanderWarning = needExpander,
            cableWarning = cableLengthM > 10.0,
            alertType = alertType,
            alertMessage = alertMessage
        )
    }
}
