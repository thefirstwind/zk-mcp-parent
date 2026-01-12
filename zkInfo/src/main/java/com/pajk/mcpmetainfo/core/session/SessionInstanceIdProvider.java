package com.pajk.mcpmetainfo.core.session;

import com.pajk.mcpmetainfo.core.config.McpSessionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

@Component
public class SessionInstanceIdProvider {

    private static final Logger log = LoggerFactory.getLogger(SessionInstanceIdProvider.class);

    private final String instanceId;

    public SessionInstanceIdProvider(McpSessionProperties properties) {
        if (StringUtils.hasText(properties.getInstanceId())) {
            this.instanceId = properties.getInstanceId();
        } else {
            this.instanceId = generateInstanceId();
            log.info("mcp.session.instance-id not configured, generated instanceId={}", this.instanceId);
        }
    }

    public String getInstanceId() {
        return instanceId;
    }

    private String generateInstanceId() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            return host + "-" + UUID.randomUUID();
        } catch (UnknownHostException e) {
            return "zkinfo-" + UUID.randomUUID();
        }
    }
}


