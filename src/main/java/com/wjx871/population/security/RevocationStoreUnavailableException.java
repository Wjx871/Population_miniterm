package com.wjx871.population.security;

import com.wjx871.population.common.BusinessException;
import org.springframework.http.HttpStatus;

public class RevocationStoreUnavailableException extends BusinessException {
    public RevocationStoreUnavailableException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE,
                "REVOCATION_STORE_UNAVAILABLE：身份令牌撤销服务暂不可用，请稍后重试");
        initCause(cause);
    }
}
