package pe.suarez.finanzas.domain;

import java.time.LocalDate;
import java.time.YearMonth;

/** Frecuencia de un movimiento recurrente. */
public enum Frequency {
    WEEKLY,
    MONTHLY,
    YEARLY;

    /**
     * Siguiente ocurrencia después de {@code from}. En mensual y anual se respeta el
     * día de referencia: el 31 cae el último día en meses más cortos y vuelve al 31 después.
     */
    public LocalDate next(LocalDate from, int anchorDay) {
        return switch (this) {
            case WEEKLY -> from.plusWeeks(1);
            case MONTHLY -> atAnchor(YearMonth.from(from).plusMonths(1), anchorDay);
            case YEARLY -> atAnchor(YearMonth.from(from).plusYears(1), anchorDay);
        };
    }

    private static LocalDate atAnchor(YearMonth ym, int anchorDay) {
        return ym.atDay(Math.min(anchorDay, ym.lengthOfMonth()));
    }
}
