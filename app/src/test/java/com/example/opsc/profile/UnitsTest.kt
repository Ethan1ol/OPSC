package com.example.opsc.profile

import com.example.opsc.auth.FieldError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for unit conversion and the unit-aware height and weight checks. */
class UnitsTest {
    @Test fun unknownPrefGivesMetric() {
        assertEquals(Units.METRIC, Units.fromPref(null))
        assertEquals(Units.METRIC, Units.fromPref("furlongs"))
        assertEquals(Units.IMPERIAL, Units.fromPref("imperial"))
    }

    @Test fun metricIsUnchanged() {
        assertEquals(180.0, UnitConverter.cmToDisplay(180.0, Units.METRIC), 0.0001)
        assertEquals(75.0, UnitConverter.displayToKg(75.0, Units.METRIC), 0.0001)
    }

    @Test fun heightConversion() {
        assertEquals(70.866, UnitConverter.cmToDisplay(180.0, Units.IMPERIAL), 0.001)
        assertEquals(182.88, UnitConverter.displayToCm(72.0, Units.IMPERIAL), 0.001)
    }

    @Test fun weightConversion() {
        assertEquals(165.347, UnitConverter.kgToDisplay(75.0, Units.IMPERIAL), 0.001)
        assertEquals(75.0, UnitConverter.displayToKg(165.3467, Units.IMPERIAL), 0.001)
    }

    @Test fun roundTripKeepsTheValue() {
        val cm = 173.0
        assertEquals(cm, UnitConverter.displayToCm(UnitConverter.cmToDisplay(cm, Units.IMPERIAL), Units.IMPERIAL), 0.0001)
    }

    @Test fun formatTrimsTrailingZero() {
        assertEquals("175", UnitConverter.format(175.0))
        assertEquals("72.5", UnitConverter.format(72.54))
        assertEquals("70.9", UnitConverter.format(70.866))
    }

    @Test fun switchingUnitsConvertsTypedText() {
        assertEquals("70.9", UnitConverter.convertTypedHeight("180", Units.METRIC, Units.IMPERIAL))
        assertEquals("180", UnitConverter.convertTypedHeight("70.866", Units.IMPERIAL, Units.METRIC))
        assertEquals("165.3", UnitConverter.convertTypedWeight("75", Units.METRIC, Units.IMPERIAL))
    }

    @Test fun switchingUnitsLeavesBadTextAlone() {
        assertEquals("", UnitConverter.convertTypedHeight("", Units.METRIC, Units.IMPERIAL))
        assertEquals("tall", UnitConverter.convertTypedHeight("tall", Units.METRIC, Units.IMPERIAL))
    }

    @Test fun heightValidationMetric() {
        assertNull(ProfileValidators.validateHeight("", Units.METRIC))
        assertNull(ProfileValidators.validateHeight("175", Units.METRIC))
        assertEquals(FieldError.HEIGHT_OUT_OF_RANGE, ProfileValidators.validateHeight("30", Units.METRIC))
        assertEquals(FieldError.NUMBER_INVALID, ProfileValidators.validateHeight("abc", Units.METRIC))
    }

    @Test fun heightValidationImperial() {
        assertNull(ProfileValidators.validateHeight("70", Units.IMPERIAL))
        assertEquals(FieldError.HEIGHT_OUT_OF_RANGE_IMPERIAL, ProfileValidators.validateHeight("10", Units.IMPERIAL))
        assertEquals(FieldError.HEIGHT_OUT_OF_RANGE_IMPERIAL, ProfileValidators.validateHeight("150", Units.IMPERIAL))
    }

    @Test fun weightValidation() {
        assertNull(ProfileValidators.validateWeight("70", Units.METRIC))
        assertEquals(FieldError.WEIGHT_OUT_OF_RANGE, ProfileValidators.validateWeight("5", Units.METRIC))
        assertNull(ProfileValidators.validateWeight("150", Units.IMPERIAL))
        assertEquals(FieldError.WEIGHT_OUT_OF_RANGE_IMPERIAL, ProfileValidators.validateWeight("10", Units.IMPERIAL))
    }

    @Test fun savingConvertsBackToMetric() {
        assertEquals(182.88, ProfileValidators.heightToCm("72", Units.IMPERIAL)!!, 0.001)
        assertEquals(68.0388, ProfileValidators.weightToKg("150", Units.IMPERIAL)!!, 0.001)
        assertNull(ProfileValidators.heightToCm("", Units.METRIC))
    }
}
