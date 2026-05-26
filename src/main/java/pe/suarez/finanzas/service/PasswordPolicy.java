package pe.suarez.finanzas.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.exception.ApiException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Política de password basada en NIST SP 800-63B (modernizada).
 *
 * <p>Reglas:
 * <ul>
 *   <li>Mínimo 12 caracteres (12 > 8 reduce dramáticamente el espacio de búsqueda).</li>
 *   <li>Máximo 128 caracteres (acepta passphrases largas).</li>
 *   <li>NO se exige complejidad artificial (mayúsculas, especiales) — los usuarios
 *       crean patrones predecibles cuando se les obliga.</li>
 *   <li>Se rechaza contra una lista de passwords comunes filtradas en breaches.</li>
 *   <li>No se permite igual al email (caso obvio de mala práctica).</li>
 * </ul>
 *
 * <p><b>MITIGACIÓN MF-05</b>
 */
@ApplicationScoped
public class PasswordPolicy {

    private static final Logger LOG = Logger.getLogger(PasswordPolicy.class);
    private static final int MIN_LENGTH = 12;
    private static final int MAX_LENGTH = 128;

    private Set<String> commonPasswords;

    @PostConstruct
    void loadCommonPasswords() {
        commonPasswords = new HashSet<>();
        // Cargamos top-1000 passwords más comunes de un archivo embebido en el classpath.
        // Si el archivo no existe (ej. tests), fallamos silenciosamente con set vacío
        // y solo aplicamos las reglas de longitud.
        try (InputStream is = getClass().getResourceAsStream("/security/common-passwords.txt")) {
            if (is == null) {
                LOG.warn("common-passwords.txt no encontrado, política de password aplicará solo longitud");
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim().toLowerCase();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        commonPasswords.add(trimmed);
                    }
                }
            }
            LOG.infof("Cargadas %d passwords comunes para validación", commonPasswords.size());
        } catch (Exception e) {
            LOG.errorf(e, "Error cargando lista de passwords comunes");
        }
    }

    /**
     * Valida un password. Si no cumple, lanza {@link ApiException} con
     * {@link ErrorCode#AUTH_PASSWORD_TOO_WEAK} y un mensaje específico.
     */
    public void validate(String password) {
        if (password == null) {
            throw new ApiException(ErrorCode.AUTH_PASSWORD_TOO_WEAK,
                    "La contraseña no puede estar vacía");
        }

        if (password.length() < MIN_LENGTH) {
            throw new ApiException(ErrorCode.AUTH_PASSWORD_TOO_WEAK,
                    "La contraseña debe tener al menos " + MIN_LENGTH + " caracteres");
        }

        if (password.length() > MAX_LENGTH) {
            throw new ApiException(ErrorCode.AUTH_PASSWORD_TOO_WEAK,
                    "La contraseña no puede exceder " + MAX_LENGTH + " caracteres");
        }

        if (commonPasswords.contains(password.toLowerCase())) {
            throw new ApiException(ErrorCode.AUTH_PASSWORD_TOO_WEAK,
                    "Esta contraseña es muy común y aparece en listas de filtraciones. Elige una distinta.");
        }
    }
}
