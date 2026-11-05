package com.example.opsc.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the "does this signed-in user still need a profile?" decision. */
class ProfileGateTest {
    @Test fun existingProfileOpensTheApp() = assertEquals(ProfileRoute.OPEN_APP, routeFor(ProfileStatus.EXISTS))
    @Test fun missingProfileGoesToFinishProfile() = assertEquals(ProfileRoute.FINISH_PROFILE, routeFor(ProfileStatus.MISSING))
    @Test fun unknownStatusDoesNotTrapTheUser() = assertEquals(ProfileRoute.OPEN_APP, routeFor(ProfileStatus.UNKNOWN))
}
