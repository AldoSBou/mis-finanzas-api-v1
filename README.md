# Mis Finanzas API

API REST para el control de finanzas personales construida con **Quarkus 3.21**, **Java 21** y **PostgreSQL 16**.

## Arquitectura

```
pe.suarez.finanzas/
├── domain/         → Entidades JPA (Panache) + enums
├── repository/     → PanacheRepository por entidad
├── service/        → Lógica de negocio (@ApplicationScoped, @Transactional)
├── dto/            → Records de Java 21 para request/response
├── mapper/         → Mappers manuales entity ↔ DTO
├── resource/       → JAX-RS resources
├── exception/      → ExceptionMapper global (RFC 7807)
└── security/       → UserContext (extrae userId del JWT) + JwtIssuer
```

## Requisitos previos

- JDK 21+
- Maven 3.9+
- Docker (para PostgreSQL local)
- OpenSSL (para generar las llaves JWT)

## Setup paso a paso

### 1. Levantar PostgreSQL

```bash
docker compose up -d
```

### 2. Generar llaves JWT (RS256)

Las llaves son **únicas por entorno** y nunca se commitean. Genera tu par localmente:

```bash
# Llave privada (firma tokens)
openssl genrsa -out privateKey.pem 2048

# Convertir a PKCS#8 (formato que requiere SmallRye JWT)
openssl pkcs8 -topk8 -inform PEM -in privateKey.pem -out privateKey-pkcs8.pem -nocrypt
mv privateKey-pkcs8.pem privateKey.pem

# Llave pública (verifica tokens)
openssl rsa -in privateKey.pem -pubout -outform PEM -out publicKey.pem
```

Las llaves quedan en la raíz del proyecto. Ya están ignoradas en `.gitignore`.

### 3. Correr la aplicación

```bash
./mvnw quarkus:dev
```

Quarkus arrancará con:
- API en http://localhost:8080
- Swagger UI en http://localhost:8080/q/swagger-ui
- Dev UI en http://localhost:8080/q/dev

Flyway aplicará las migraciones automáticamente.

## Endpoints principales

| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/api/auth/register` | Registra un usuario y siembra categorías + reglas plantilla |
| `POST` | `/api/auth/login` | Devuelve JWT |
| `GET` | `/api/auth/me` | Datos del usuario autenticado |
| `GET` | `/api/categories` | Lista categorías del usuario |
| `POST` | `/api/categories` | Crea categoría personalizada |
| `PUT/DELETE` | `/api/categories/{id}` | Actualiza/archiva |
| `GET` | `/api/accounts` | Cuentas con su saldo actual |
| `POST/PUT/DELETE` | `/api/accounts[/{id}]` | Crea/actualiza/archiva cuentas |
| `GET` | `/api/transactions?period=2026-04&accountId=3&page=0&size=20` | Movimientos del mes (opcional: de una cuenta) |
| `GET` | `/api/transactions?from=2025-01-01&to=2026-12-31&q=soat&type=EXPENSE&categoryId=5` | Búsqueda con filtros (texto, tipo, categoría, cuenta, montos) |
| `GET` | `/api/transactions/export?...` | Mismos filtros, descarga CSV (UTF-8 con BOM para Excel) |
| `POST/PUT/DELETE` | `/api/transactions[/{id}]` | CRUD de ingresos, gastos y transferencias |
| `GET` | `/api/transactions/exchange-rate?currency=USD` | Último tipo de cambio usado (204 si ninguno) |
| `GET/POST` | `/api/recurring` | Recurrentes (semanal, mensual, anual; automáticos o con confirmación) |
| `PUT/DELETE` | `/api/recurring/{id}` | Editar / eliminar recurrente |
| `POST` | `/api/recurring/{id}/register` · `/skip` | Registrar (monto real) u omitir la ocurrencia pendiente |
| `GET` | `/api/category-budgets?period=2026-09` | Límite, gastado y programado por categoría |
| `PUT/DELETE` | `/api/category-budgets/{categoryId}` | Definir / quitar límite mensual |
| `GET` | `/api/reports?until=2026-09&months=12` | Ingresos, gastos, ahorro, patrimonio y gasto por categoría por mes |
| `GET` | `/api/allocation-rules` | Reglas de asignación (incluye 50/30/20, 70/20/10, Kakebo) |
| `POST/PUT/DELETE` | `/api/allocation-rules[/{id}]` | CRUD de reglas |
| `GET` | `/api/budgets?period=2026-04` | Presupuesto del mes |
| `POST` | `/api/budgets` | Upsert presupuesto del mes |
| `GET` | `/api/dashboard?period=2026-04` | **Resumen completo del panel principal** |

## Decisiones de diseño

**Multi-tenant desde el inicio.** Todas las entidades llevan `userId` plano (no FK relación) y todas las queries filtran por `userContext.userId()`. El `userId` viene del claim `uid` del JWT.

**Montos en `BigDecimal(14,2)`.** Nunca `double` o `float` para dinero. Soporta hasta `999,999,999,999.99`.

**Reglas de asignación con JSONB.** El campo `percentages` es un mapa `bucket → %` flexible. Permite cualquier metodología (50/30/20, 70/20/10, Kakebo, custom) sin cambiar el schema. Los buckets son un enum (`AllocationBucket`).

**Cuentas y transferencias.** Cada movimiento pertenece a una cuenta (efectivo, banco, tarjeta, Yape, ahorro, inversión). El saldo se calcula: saldo inicial + ingresos − gastos − transferencias salientes + entrantes. Una `TRANSFER` no es ingreso ni gasto: pagar la tarjeta no duplica el gasto (ya se registró al comprar). Transferir a una cuenta `SAVINGS`/`INVESTMENT` cuenta como ahorro del mes; sacar dinero de ella lo resta.

**Multi-moneda.** La moneda es la de la cuenta. Cada movimiento guarda `exchange_rate` y `amount_base` (monto en la moneda base del usuario). Los reportes suman solo `amount_base`. En transferencias hacia la moneda base el tipo de cambio sale de los montos (100 USD → 372 PEN = 3.72); en otros casos el cliente lo envía.

**Dashboard.** `expenses` es consumo (sin categorías de ahorro/inversión), `savings` es ahorro neto del mes y `balance = income − expenses − savings`.

**Recurrentes sin cron.** En Railway Free el backend se duerme, así que una tarea programada no correría. Las ocurrencias automáticas vencidas se registran al abrir la app (dashboard, movimientos, cuentas, reportes), con `SELECT ... FOR UPDATE` para no duplicar cuando varias pantallas cargan a la vez. Los de confirmación manual quedan como pendientes en el dashboard.

**Presupuesto por categoría.** Límite mensual en moneda base, igual para todos los meses. Estado OK / WARNING (80%) / OVER (100%) sobre lo gastado; `willExceed` avisa si lo programado en recurrentes hará pasar el límite.

**Categorías ↔ Buckets.** Cada categoría tiene un `defaultBucket`. Las transacciones heredan el bucket de su categoría al agregarse en el dashboard. Esto evita duplicar lógica de clasificación en cada movimiento.

**Index crítico:** `(user_id, transaction_date DESC)` en `transactions`. Es la query más frecuente.

**Errors en RFC 7807.** Las respuestas de error usan `application/problem+json` con `type/title/status/detail/timestamp`. Para validaciones se incluye un array `errors` con `field` y `message`.

## Variables de entorno

| Variable | Default | Descripción |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/misfinanzas` | JDBC URL |
| `DB_USER` | `finanzas` | Usuario de DB |
| `DB_PASSWORD` | `finanzas` | Password de DB |
| `JWT_PUBLIC_KEY_LOCATION` | `publicKey.pem` | Ruta a llave pública |
| `JWT_PRIVATE_KEY_LOCATION` | `privateKey.pem` | Ruta a llave privada |
| `JWT_ISSUER` | `https://mis-finanzas.suarez.pe` | Issuer del JWT |
| `LOG_SQL` | `false` | Activa logging de SQL |

## Tests

```bash
./mvnw test
```

Los tests usan `@QuarkusTest` y **Dev Services**: levantan un PostgreSQL desechable en Docker (requiere Docker corriendo) y aplican las migraciones reales. Nunca tocan tu base local.

`src/test/resources/docker-java.properties` fija la API de Docker en 1.44: Docker Engine 29+ rechaza la versión antigua que pide el Testcontainers de Quarkus 3.21. Se puede quitar al actualizar Quarkus.

## Build

```bash
# Jar runner
./mvnw package

# Ejecutar
java -jar target/quarkus-app/quarkus-run.jar

# Imagen nativa (requiere GraalVM o Mandrel)
./mvnw package -Dnative
```

## Próximos pasos sugeridos

1. **Importador CSV** de estados de cuenta (BCP, Interbank) con reglas de categorización.
2. **Metas de ahorro** con progreso.
3. **Tarjetas de crédito**: fecha de corte y pago, compras en cuotas.
4. **Registro sin conexión** (cola local en la PWA).
5. **Tipo de cambio automático** (SUNAT/SBS) para prellenar movimientos en otra moneda.
