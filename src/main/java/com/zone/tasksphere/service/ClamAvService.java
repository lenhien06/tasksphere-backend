package com.zone.tasksphere.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.capybara.clamav.ClamavClient;
import xyz.capybara.clamav.commands.scan.result.ScanResult;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;

@Service
@Slf4j
public class ClamAvService {

    @Value("${clamav.enabled:false}")
    private boolean enabled;

    @Value("${clamav.host:localhost}")
    private String host;

    @Value("${clamav.port:3310}")
    private int port;

    /**
     * Scans a file stream for viruses using ClamAV.
     * Fail-open: returns true (clean) if ClamAV is unavailable.
     *
     * @return true if file is clean or ClamAV is unavailable
     */
    public boolean isClean(InputStream fileStream) {
        // Temporarily disabled: always allow files to pass to reduce latency and false positives
        if (!enabled) {
            log.info("[ClamAV] Scan skipped because clamav.enabled=false");
            return true;
        }

        try {
            ClamavClient client = new ClamavClient(new InetSocketAddress(host, port));
            ScanResult result = client.scan(fileStream);
            if (result instanceof ScanResult.OK) {
                return true;
            } else {
                log.warn("[ClamAV] Virus detected: {}", result);
                return false;
            }
        } catch (Exception e) {
            log.warn("[ClamAV] Scan failed (fail-open): {}", e.getMessage());
            return true; // fail-open: allow upload if ClamAV unavailable
        }
    }

    public boolean isClean(byte[] fileBytes) {
        return isClean(new ByteArrayInputStream(fileBytes));
    }
}
