#!/bin/sh
# =============================================================
# entrypoint.sh — Prepara el entorno antes de arrancar Quarkus
#
# Las llaves JWT se entregan como variables de entorno en formato
# base64 (una sola línea, sin problemas de saltos de línea ni comillas).
# Este script las decodifica a archivos .pem que Quarkus puede leer.
#
# Variables esperadas en producción:
#   JWT_PRIVATE_KEY_B64  — contenido de privateKey.pem codificado en base64
#   JWT_PUBLIC_KEY_B64   — contenido de publicKey.pem codificado en base64
# =============================================================
set -e

KEYS_DIR="/app/keys"
mkdir -p "$KEYS_DIR"

if [ -n "$JWT_PRIVATE_KEY_B64" ] && [ -n "$JWT_PUBLIC_KEY_B64" ]; then
    echo "[entrypoint] Decodificando llaves JWT desde variables de entorno..."
    echo "$JWT_PRIVATE_KEY_B64" | base64 -d > "$KEYS_DIR/privateKey.pem"
    echo "$JWT_PUBLIC_KEY_B64"  | base64 -d > "$KEYS_DIR/publicKey.pem"
    chmod 600 "$KEYS_DIR/privateKey.pem" "$KEYS_DIR/publicKey.pem"

    # Apuntar Quarkus a los archivos generados
    export JWT_PRIVATE_KEY_LOCATION="$KEYS_DIR/privateKey.pem"
    export JWT_PUBLIC_KEY_LOCATION="$KEYS_DIR/publicKey.pem"
    echo "[entrypoint] Llaves JWT preparadas en $KEYS_DIR"
else
    echo "[entrypoint] ADVERTENCIA: JWT_PRIVATE_KEY_B64 / JWT_PUBLIC_KEY_B64 no definidas."
    echo "[entrypoint] Quarkus usará los valores por defecto (solo válido en local)."
fi

echo "[entrypoint] Arrancando Quarkus..."
exec java $JAVA_OPTS -jar quarkus-run.jar
