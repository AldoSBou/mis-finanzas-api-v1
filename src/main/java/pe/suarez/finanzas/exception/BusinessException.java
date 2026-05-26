package pe.suarez.finanzas.exception;

import pe.suarez.finanzas.api.ErrorCode;

public class BusinessException extends ApiException {

    public BusinessException(String message) {
        super(ErrorCode.BUSINESS_ERROR, message);
    }

    public BusinessException(ErrorCode specificCode, String message) {
        super(specificCode, message);
    }
}
