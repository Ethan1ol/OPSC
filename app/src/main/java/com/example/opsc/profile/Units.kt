package com.example.opsc.profile

import com.example.opsc.auth.FieldError
import com.example.opsc.auth.Validators

/** Metric or imperial for height and weight. Data is always stored in cm and kg. */
enum class Units(val prefValue: String) {
    METRIC("metric"),
    IMPERIAL("imperial");

    companion object {
        /** Turns a saved value into units. Unknown or missing gives metric. */
        fun fromPref(value: String?): Units = entries.firstOrNull { it.prefValue == value } ?: METRIC
    }
}

/** Converts between stored metric values and what the user sees. */
object UnitConverter {
    private const val CM_PER_INCH = 2.54
    private const val LB_PER_KG = 2.2046226218

    fun cmToDisplay(cm: Double, units: Units): Double = if (units == Units.METRIC) cm else cm / CM_PER_INCH
    fun displayToCm(value: Double, units: Units): Double = if (units == Units.METRIC) value else value * CM_PER_INCH
    fun kgToDisplay(kg: Double, units: Units): Double = if (units == Units.METRIC) kg else kg * LB_PER_KG
    fun displayToKg(value: Double, units: Units): Double = if (units == Units.METRIC) value else value / LB_PER_KG

    /** Formats a number with at most one decimal and no trailing .0. */
    fun format(value: Double): String {
        val rounded = Math.round(value * 10) / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }

    /** Converts typed text when the user changes units, for example 180 cm becomes 70.9 in. Blank or invalid text is left as it is. */
    fun convertTypedHeight(text: String, from: Units, to: Units): String {
        val value = Validators.parseDecimal(text) ?: return text
        return format(cmToDisplay(displayToCm(value, from), to))
    }

    fun convertTypedWeight(text: String, from: Units, to: Units): String {
        val value = Validators.parseDecimal(text) ?: return text
        return format(kgToDisplay(displayToKg(value, from), to))
    }
}

/** Height and weight checks for the chosen units. */
object ProfileValidators {
    fun validateHeight(text: String, units: Units): FieldError? {
        if (text.isBlank()) return null
        val value = Validators.parseDecimal(text) ?: return FieldError.NUMBER_INVALID
        val cm = UnitConverter.displayToCm(value, units)
        return if (cm in Validators.MIN_HEIGHT_CM..Validators.MAX_HEIGHT_CM) null
        else if (units == Units.METRIC) FieldError.HEIGHT_OUT_OF_RANGE else FieldError.HEIGHT_OUT_OF_RANGE_IMPERIAL
    }

    fun validateWeight(text: String, units: Units): FieldError? {
        if (text.isBlank()) return null
        val value = Validators.parseDecimal(text) ?: return FieldError.NUMBER_INVALID
        val kg = UnitConverter.displayToKg(value, units)
        return if (kg in Validators.MIN_WEIGHT_KG..Validators.MAX_WEIGHT_KG) null
        else if (units == Units.METRIC) FieldError.WEIGHT_OUT_OF_RANGE else FieldError.WEIGHT_OUT_OF_RANGE_IMPERIAL
    }

    /** Typed height in centimetres, or null if blank or invalid. */
    fun heightToCm(text: String, units: Units): Double? =
        Validators.parseDecimal(text)?.let { UnitConverter.displayToCm(it, units) }

    /** Typed weight in kilograms, or null if blank or invalid. */
    fun weightToKg(text: String, units: Units): Double? =
        Validators.parseDecimal(text)?.let { UnitConverter.displayToKg(it, units) }
}
