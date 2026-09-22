package pe.suarez.finanzas.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrequencyTest {

    @Test
    void monthlyKeepsAnchorDayAcrossShortMonths() {
        LocalDate jan31 = LocalDate.of(2027, 1, 31);
        LocalDate feb = Frequency.MONTHLY.next(jan31, 31);
        LocalDate mar = Frequency.MONTHLY.next(feb, 31);
        assertEquals(LocalDate.of(2027, 2, 28), feb);
        assertEquals(LocalDate.of(2027, 3, 31), mar);
    }

    @Test
    void yearlyHandlesLeapDay() {
        LocalDate leap = LocalDate.of(2028, 2, 29);
        assertEquals(LocalDate.of(2029, 2, 28), Frequency.YEARLY.next(leap, 29));
        assertEquals(LocalDate.of(2032, 2, 29),
                Frequency.YEARLY.next(Frequency.YEARLY.next(Frequency.YEARLY.next(
                        Frequency.YEARLY.next(leap, 29), 29), 29), 29));
    }

    @Test
    void weeklyAddsSevenDays() {
        assertEquals(LocalDate.of(2027, 1, 7), Frequency.WEEKLY.next(LocalDate.of(2026, 12, 31), 31));
    }
}
