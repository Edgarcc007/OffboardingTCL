package com.empresa.offboarding.bulk;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.nio.file.*;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AssetCatalogBootstrap implements ApplicationRunner {
    private final AssetCatalog catalog;
    private final ObjectMapper json;

    @Value("${OFFBOARDING_ADMIN_JOB:}")
    private String jobFile;

    public AssetCatalogBootstrap(AssetCatalog catalog,ObjectMapper json) {
        this.catalog=catalog;this.json=json;
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        if(jobFile==null||jobFile.isBlank()) return;

        Path path=Path.of(jobFile);
        var request=json.readTree(Files.readAllBytes(path));
        if(!"assets-r4".equals(request.path("operation").asText()))
            throw new IllegalArgumentException("Operacion de mantenimiento no reconocida.");

        var result=catalog.apply(
            Path.of(request.path("file").asText()),
            request.path("sha256").asText());

        Files.write(path.resolveSibling(path.getFileName()+".done"),
            json.writeValueAsBytes(result));
    }
}