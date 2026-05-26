package pe.suarez.finanzas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.suarez.finanzas.domain.AllocationBucket;
import pe.suarez.finanzas.domain.TransactionType;

public final class CategoryDtos {

    private CategoryDtos() {}

    public record CategoryRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull TransactionType type,
            AllocationBucket defaultBucket,
            @Size(max = 7) String color,
            @Size(max = 40) String icon
    ) {}

    public record CategoryResponse(
            Long id,
            String name,
            TransactionType type,
            AllocationBucket defaultBucket,
            String color,
            String icon,
            boolean archived
    ) {}
}
