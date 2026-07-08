package com.wzz.smscode.util;

import com.wzz.smscode.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlSecurityUtilTest {

    @Test
    void blocksRfc1918PrivateIpv4() {
        assertThrows(BusinessException.class,
                () -> UrlSecurityUtil.requireNonPrivateHttpUrl("http://192.168.1.10/api"));
    }

    @Test
    void allowsPublicIpv4AndLoopbackByCurrentRequirement() {
        assertDoesNotThrow(() -> UrlSecurityUtil.requireNonPrivateHttpUrl("https://8.8.8.8/dns-query"));
        assertDoesNotThrow(() -> UrlSecurityUtil.requireNonPrivateHttpUrl("http://127.0.0.1:8080/health"));
    }
}
