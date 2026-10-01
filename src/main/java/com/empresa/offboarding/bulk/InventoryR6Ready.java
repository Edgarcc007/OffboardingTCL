package com.empresa.offboarding.bulk;

import org.springframework.boot.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.nio.file.*;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE+150)
public class InventoryR6Ready implements ApplicationRunner {
    private final AssetCatalog catalog;

    public InventoryR6Ready(AssetCatalog catalog) {
        this.catalog=catalog;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        Path directory=Path.of("C:/OffboardingTCL/private-temp/inventory-update");
        if(!Files.isDirectory(directory)||!Files.isWritable(directory))
            throw new IllegalStateException("Private inventory upload directory is unavailable.");
        catalog.r6Info(); // Solo lectura: comprobar estructura y consulta.
    }
}