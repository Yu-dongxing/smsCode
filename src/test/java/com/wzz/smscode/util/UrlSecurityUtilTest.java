package com.wzz.smscode.util;

import com.sun.net.httpserver.HttpServer;
import com.wzz.smscode.config.RestTemplateConfig;
import com.wzz.smscode.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void allowsTemplateVariablesWhenSavingProviderConfig() {
        assertDoesNotThrow(() -> UrlSecurityUtil.requireNonPrivateHttpTemplateUrl(
                "https://api.yuxijm.com/api/phone/getCode?token={{token}}&phone={{phone}}&project_id=111"));
    }

    @Test
    void stillBlocksPrivateHostWithTemplateVariables() {
        assertThrows(BusinessException.class, () -> UrlSecurityUtil.requireNonPrivateHttpTemplateUrl(
                "http://192.168.1.10/api?token={{token}}"));
    }

    @Test
    void restTemplateDoesNotFollowRedirects() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/final");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/final", exchange -> {
            byte[] body = "followed".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/redirect";
            ResponseEntity<String> response = new RestTemplateConfig().restTemplate().getForEntity(url, String.class);

            assertEquals(302, response.getStatusCode().value());
        } finally {
            server.stop(0);
        }
    }
}
