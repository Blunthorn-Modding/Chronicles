package io.github.chronicle.core.stats;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OfferingExhaustionTest {
    @Test void eachPriceScalesOnePositiveInput(){
        assertEquals(2.5f,OfferingExhaustion.scale(2,1.25));
        assertEquals(3.5f,OfferingExhaustion.scale(2,1.75));
        assertEquals(2,OfferingExhaustion.scale(2,1));
    }
    @Test void administrativeNegativeAndZeroInputsAreNotRepriced(){
        assertEquals(-40,OfferingExhaustion.scale(-40,1.75));
        assertEquals(0,OfferingExhaustion.scale(0,1.75));
    }
    @Test void invalidInputOrModifierDoesNotCreateNewInvalidState(){
        assertEquals(2,OfferingExhaustion.scale(2,Double.NaN));
        assertEquals(2,OfferingExhaustion.scale(2,Double.POSITIVE_INFINITY));
        assertEquals(2,OfferingExhaustion.scale(2,.5));
        assertTrue(Float.isNaN(OfferingExhaustion.scale(Float.NaN,1.75)));
    }
    @Test void largeFiniteInputsStayFiniteForTheNativeExhaustionClamp(){
        assertEquals(Float.MAX_VALUE,OfferingExhaustion.scale(Float.MAX_VALUE,256));
    }
}
