package pe.suarez.finanzas.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;

/** Una importación de estado de cuenta; sus movimientos llevan {@code import_batch_id}. */
@Entity
@Table(name = "import_batches")
public class ImportBatch extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "account_id", nullable = false)
    public Long accountId;

    @Column(name = "file_name", length = 200)
    public String fileName;

    @Column(name = "row_count", nullable = false)
    public int rowCount;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
