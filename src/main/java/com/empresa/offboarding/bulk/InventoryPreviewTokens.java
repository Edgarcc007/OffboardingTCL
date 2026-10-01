package com.empresa.offboarding.bulk;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.time.Instant;
import java.util.*;

@Component
public class InventoryPreviewTokens {
    public record Claims(
        long owner,String fileHash,String revision,int rows,
        String fileName,long expires,String operation
    ) {}

    private final byte[] secret=new byte[32];
    private final ObjectMapper json;

    public InventoryPreviewTokens(ObjectMapper json) {
        this.json=json;
        new SecureRandom().nextBytes(secret);
    }

    public String issue(long owner,String hash,String revision,int rows,String fileName) {
        return sign(new Claims(
            owner,hash,revision,rows,fileName,
            Instant.now().plusSeconds(600).getEpochSecond(),
            UUID.randomUUID().toString()));
    }

    public String sign(Claims claims) {
        try {
            byte[] data=json.writeValueAsBytes(claims);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(data)+"."+
                Base64.getUrlEncoder().withoutPadding().encodeToString(mac(data));
        } catch(Exception error) {
            throw new IllegalStateException("Unable to prepare inventory confirmation.",error);
        }
    }

    public Claims verify(String token,long owner) {
        if(token==null||token.length()>4096)
            throw invalid();

        try {
            String[] parts=token.split("\\.",-1);
            if(parts.length!=2)throw invalid();

            byte[] data=Base64.getUrlDecoder().decode(parts[0]);
            byte[] signature=Base64.getUrlDecoder().decode(parts[1]);
            if(!MessageDigest.isEqual(signature,mac(data)))throw invalid();

            Claims claims=json.readValue(data,Claims.class);
            if(claims.owner()!=owner)
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,"This preview belongs to another account.");

            if(claims.expires()<Instant.now().getEpochSecond())
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,"The preview expired. Analyze the Excel again.");

            if(claims.fileHash()==null||claims.revision()==null||
               claims.fileName()==null||claims.operation()==null||
               claims.rows()<1||claims.rows()>50000)
                throw invalid();

            UUID.fromString(claims.operation());
            return claims;
        } catch(ResponseStatusException error) {
            throw error;
        } catch(Exception error) {
            throw invalid();
        }
    }

    private byte[] mac(byte[] data) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret,"HmacSHA256"));
        return mac.doFinal(data);
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(
            HttpStatus.BAD_REQUEST,"Invalid preview. Analyze the Excel again.");
    }
}