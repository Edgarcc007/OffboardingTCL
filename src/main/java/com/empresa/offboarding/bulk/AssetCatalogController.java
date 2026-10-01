package com.empresa.offboarding.bulk;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bulk/assets")
public class AssetCatalogController {
    // R7_INVENTORY_ACCESS
    @org.springframework.beans.factory.annotation.Autowired
    private R7Access r7Access;
    private final BulkWorkflow workflow;
    private final AssetCatalog catalog;

    public AssetCatalogController(BulkWorkflow workflow,AssetCatalog catalog) {
        this.workflow=workflow;this.catalog=catalog;
    }

    @GetMapping("/search")
    public ResponseEntity<AssetCatalog.Page> search(
        @RequestParam(defaultValue="") String q,
        @RequestParam(defaultValue="0") int offset,
        @RequestParam(defaultValue="all") String kind,
        Authentication authentication
    ) {
        r7Access.assets(authentication);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(catalog.search(q,offset,kind));
    }

    @GetMapping("/record/{id}")
    public ResponseEntity<AssetCatalog.Hit> record(
        @PathVariable long id,Authentication authentication
    ) {
        r7Access.assets(authentication);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(catalog.r6Record(id));
    }
}