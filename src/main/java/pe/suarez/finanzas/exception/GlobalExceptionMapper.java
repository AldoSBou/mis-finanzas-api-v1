package pe.suarez.finanzas.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import pe.suarez.finanzas.api.ApiError;
import pe.suarez.finanzas.api.ApiResponse;
import pe.suarez.finanzas.api.ErrorCatalog;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.api.Meta;

import java.util.List;

/**
 * Mapper global de excepciones. Devuelve respuestas envueltas en {@link ApiResponse}
 * con códigos semánticos del {@link ErrorCatalog}.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Context
    UriInfo uriInfo;

    @Context
    ResourceInfo resourceInfo;

    @Override
    public Response toResponse(Throwable ex) {
        ErrorCode code;
        String message;
        List<ApiError.FieldError> details = null;

        switch (ex) {
            case ApiException apiEx -> {
                code = apiEx.code();
                message = apiEx.getMessage();
            }
            case ConstraintViolationException cv -> {
                code = ErrorCode.VALIDATION_ERROR;
                message = ErrorCatalog.message(code);
                details = cv.getConstraintViolations().stream()
                        .map(v -> new ApiError.FieldError(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                        .toList();
            }
            case NotAuthorizedException ignored -> {
                code = ErrorCode.UNAUTHORIZED;
                message = ErrorCatalog.message(code);
            }
            case NotFoundException ignored -> {
                code = ErrorCode.NOT_FOUND;
                message = ErrorCatalog.message(code);
            }
            case NotAllowedException ignored -> {
                code = ErrorCode.METHOD_NOT_ALLOWED;
                message = ErrorCatalog.message(code);
            }
            case NotSupportedException ignored -> {
                code = ErrorCode.UNSUPPORTED_MEDIA_TYPE;
                message = ErrorCatalog.message(code);
            }
            case WebApplicationException wae -> {
                int status = wae.getResponse().getStatus();
                code = httpStatusToCode(status);
                message = wae.getMessage() != null ? wae.getMessage() : ErrorCatalog.message(code);
            }
            default -> {
                LOG.errorf(ex, "Error no manejado [requestId=%s]", MDC.get("requestId"));
                code = ErrorCode.INTERNAL_ERROR;
                message = ErrorCatalog.message(code);
            }
        }

        ApiError error = ApiError.of(code, message, details);
        Meta meta = Meta.now(currentRequestId(), uriInfo != null ? uriInfo.getPath() : null);
        ApiResponse<Object> body = ApiResponse.error(error, meta);

        return Response.status(code.httpStatus())
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }

    private static ErrorCode httpStatusToCode(int status) {
        return switch (status) {
            case 400 -> ErrorCode.BAD_REQUEST;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 409 -> ErrorCode.CONFLICT;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }

    private String currentRequestId() {
        Object id = MDC.get("requestId");
        return id != null ? id.toString() : null;
    }

    private String lastNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }
}
