package com.empresa.offboarding.bulk;

import com.empresa.offboarding.entity.AppUser;
import com.empresa.offboarding.enums.AppRole;
import com.empresa.offboarding.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Semaphore;

@RestController
@RequestMapping("/api/bulk/assets/manage")
@PreAuthorize("hasAnyRole('ADMIN','IT_ENGINEER_VALIDATOR')")
public class InventoryUpdateController {
    private static final int MAX_BYTES=20*1024*1024;
    private static final Path TEMP=Path.of(
        "C:/OffboardingTCL/private-temp/inventory-update");

    // R7_INVENTORY_ACCESS
    @org.springframework.beans.factory.annotation.Autowired
    private R7Access r7Access;
    private final BulkWorkflow workflow;
    private final AppUserRepository users;
    private final AssetCatalog catalog;
    private final InventoryPreviewTokens tokens;
    private final Semaphore slot=new Semaphore(1);

    public InventoryUpdateController(
        BulkWorkflow workflow,AppUserRepository users,
        AssetCatalog catalog,InventoryPreviewTokens tokens
    ) {
        this.workflow=workflow;this.users=users;
        this.catalog=catalog;this.tokens=tokens;
    }

    public static boolean canWrite(AppUser user) {
        return user!=null&&user.isEnabled()&&
            user.getRoles()!=null&&(user.getRoles().contains(AppRole.ADMIN)||user.getRoles().contains(AppRole.IT_ENGINEER_VALIDATOR));
    }

    private long admin(Authentication authentication) {
        var owner=r7Access.assets(authentication);
        AppUser user=users.findById(owner.id()).orElse(null);
        if(!canWrite(user))
            throw new AccessDeniedException("Only ADMIN or IT Engineer Validator can update inventory.");
        return owner.id();
    }

    @GetMapping("/session")
    public ResponseEntity<Map<String,Object>> session(
        Authentication authentication,CsrfToken csrf
    ) {
        long owner=admin(authentication);
        if(csrf==null)
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,"CSRF protection is unavailable.");

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
            "owner",owner,"header",csrf.getHeaderName(),"token",csrf.getToken(),
            "current",catalog.r6Info()));
    }

    public record Preview(
        String token,String fileName,String fileHash,int newRows,
        AssetCatalog.CatalogInfo current,List<List<String>> sample
    ) {}

    @PostMapping(value="/preview",consumes=MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Preview preview(
        @RequestHeader("X-File-Name") String fileName,
        HttpServletRequest request,Authentication authentication
    ) throws Exception {
        long owner=admin(authentication);
        reserve();
        Path file=null;

        try {
            String name=cleanName(fileName);
            byte[] bytes=read(request);
            String hash=hash(bytes);
            file=Files.createTempFile(TEMP,"inventory-",".xlsx");
            Files.write(file,bytes);
            List<AssetCatalog.RawRow> rows=parse(file);
            var current=catalog.r6Info();

            String token=tokens.issue(owner,hash,current.revision(),rows.size(),name);
            return new Preview(
                token,name,hash,rows.size(),current,
                rows.stream().limit(5).map(AssetCatalog.RawRow::values).toList());
        } finally {
            remove(file);slot.release();
        }
    }

    @PostMapping(value="/commit",consumes=MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public AssetCatalog.UpdateResult commit(
        @RequestHeader("X-Inventory-Preview") String token,
        @RequestHeader(value="X-Complete-Inventory",defaultValue="false") boolean complete,
        HttpServletRequest request,Authentication authentication
    ) throws Exception {
        long owner=admin(authentication);
        if(!complete)
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,"Confirm that this is the COMPLETE inventory.");

        var claims=tokens.verify(token,owner);
        reserve();
        Path file=null;

        try {
            byte[] bytes=read(request);
            if(!hash(bytes).equals(claims.fileHash()))
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,"The file changed. Analyze it again.");

            file=Files.createTempFile(TEMP,"inventory-",".xlsx");
            Files.write(file,bytes);
            List<AssetCatalog.RawRow> rows=parse(file);

            if(rows.size()!=claims.rows())
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,"The file content changed. Analyze it again.");

            return catalog.r6Replace(
                rows,claims.fileHash(),claims.revision(),owner,
                claims.operation(),claims.fileName(),complete);
        } finally {
            remove(file);slot.release();
        }
    }

    private void reserve() {
        if(!slot.tryAcquire())
            throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,"Another inventory file is being processed.");
    }

    private static String cleanName(String value) {
        String name=URLDecoder.decode(value,StandardCharsets.UTF_8)
            .replace('\r',' ').replace('\n',' ').strip();
        if(!name.toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,"Select the original .xlsx inventory file.");
        return name.length()>180?name.substring(0,180):name;
    }

    private static byte[] read(HttpServletRequest request) throws IOException {
        if(request.getContentLengthLong()>MAX_BYTES)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Maximum file size: 20 MB.");

        byte[] bytes=request.getInputStream().readNBytes(MAX_BYTES+1);
        if(bytes.length>MAX_BYTES)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Maximum file size: 20 MB.");
        if(bytes.length==0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"The file is empty.");
        return bytes;
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static List<AssetCatalog.RawRow> parse(Path file) {
        try {
            return AssetCatalog.readWorkbook(file);
        } catch(Exception error) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unable to read the inventory. Use the original XLSX with its 22 headers.");
        }
    }

    private static void remove(Path path) {
        if(path==null)return;
        try { Files.deleteIfExists(path); }
        catch(IOException error) {
            org.slf4j.LoggerFactory.getLogger(InventoryUpdateController.class)
                .warn("Unable to remove an inventory temporary file.");
        }
    }
}