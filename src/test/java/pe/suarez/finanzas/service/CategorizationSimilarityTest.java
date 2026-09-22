package pe.suarez.finanzas.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CategorizationSimilarityTest {

    private static double sim(String a, String b) {
        return CategorizationService.similarity(
                CategorizationService.tokens(CategorizationService.normalize(a)),
                CategorizationService.tokens(CategorizationService.normalize(b)));
    }

    @Test
    void normalizeDropsNumbersSymbolsAndAccents() {
        assertEquals("uber trip lima", CategorizationService.normalize("UBER *TRIP 4821 LIMA"));
        assertEquals("cafe peru", CategorizationService.normalize("Café-Perú #12"));
    }

    @Test
    void sameMerchantWithExtraWordsMatches() {
        assertTrue(sim("UBER *TRIP 4821", "Uber Trip 9913 LIMA") >= 0.6);
        assertTrue(sim("NETFLIX.COM", "NETFLIX COM 8829") >= 0.6);
    }

    @Test
    void differentMerchantsWithCommonBankPrefixDoNotMatch() {
        assertTrue(sim("COMPRA POS PLAZA VEA", "COMPRA POS TAMBO") < 0.6);
        assertTrue(sim("YAPE ENVIO JUAN PEREZ", "YAPE ENVIO MARIA LOPEZ") < 0.6);
    }
}
