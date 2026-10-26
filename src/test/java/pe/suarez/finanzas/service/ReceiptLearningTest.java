package pe.suarez.finanzas.service;

import org.junit.jupiter.api.Test;
import pe.suarez.finanzas.dto.ReceiptDtos.ReceiptItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Lo que la persona corrigió en boletas anteriores manda sobre el modelo. */
class ReceiptLearningTest {

    @Test
    void normalizesAbbreviationsAndPunctuation() {
        assertEquals("lch gloria evap 400g", ReceiptService.normalize("LCH. GLORIA  EVAP 400G"));
        assertEquals("det ariel 2kg", ReceiptService.normalize("Det Ariel 2Kg"));
        assertEquals("pan integral", ReceiptService.normalize("Pan integral"));
    }

    @Test
    void learnedCategoryOverridesTheModel() {
        Map<String, Long> learned = Map.of("det ariel 2kg", 20L);
        List<ReceiptItem> items = List.of(
                new ReceiptItem("DET ARIEL 2KG", new BigDecimal("28.30"), 10L, null),
                new ReceiptItem("LCH GLORIA", new BigDecimal("4.50"), 10L, null),
                new ReceiptItem("MACETA", new BigDecimal("6.00"), null, "Jardín"));

        List<ReceiptItem> out = ReceiptService.applyLearned(items, learned);

        assertEquals(20L, out.get(0).categoryId(), "el detergente va donde la persona lo puso antes");
        assertNull(out.get(0).suggestedCategory());
        assertEquals(10L, out.get(1).categoryId(), "lo no aprendido no cambia");
        assertNull(out.get(2).categoryId());
        assertEquals("Jardín", out.get(2).suggestedCategory());
    }

    @Test
    void nothingLearnedLeavesItemsUntouched() {
        List<ReceiptItem> items = List.of(new ReceiptItem("PAN", new BigDecimal("1.00"), 1L, null));
        assertSame(items, ReceiptService.applyLearned(items, Map.of()));
    }
}
