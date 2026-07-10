package com.wjx871.population.material;

import com.wjx871.population.common.BusinessException;
import java.io.IOException; import java.io.InputStream; import java.nio.file.*; import java.security.*; import java.util.*;
import org.springframework.beans.factory.annotation.Value; import org.springframework.core.io.Resource; import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.web.multipart.MultipartFile;

/** Local storage with generated names, canonical path checks and SHA-256 hashing. */
@Service
public class LocalFileStorageService {
    private static final Set<String> EXTENSIONS=Set.of("pdf","jpg","jpeg","png");
    private static final Set<String> TYPES=Set.of("application/pdf","image/jpeg","image/png");
    private final Path root; private final long maxBytes;
    public LocalFileStorageService(@Value("${application.upload.dir}") String dir,@Value("${application.upload.max-size-mb}") long maxMb) {
        root=Path.of(dir).toAbsolutePath().normalize(); maxBytes=Math.multiplyExact(maxMb,1024L*1024L);
        try { Files.createDirectories(root); } catch(IOException e){ throw new IllegalStateException("无法创建上传目录",e); }
    }
    public StoredFile store(MultipartFile file) {
        if(file==null||file.isEmpty()) throw bad("上传文件不能为空");
        if(file.getSize()>maxBytes) throw bad("文件超过允许的最大大小");
        String original=Optional.ofNullable(file.getOriginalFilename()).orElse("file");
        String safeOriginal=Path.of(original).getFileName().toString();
        String ext=extension(safeOriginal); if(!EXTENSIONS.contains(ext)) throw bad("不允许的文件扩展名");
        String type=Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT); if(!TYPES.contains(type)) throw bad("不允许的文件类型");
        String stored=UUID.randomUUID().toString().replace("-","")+"."+ext; Path target=root.resolve(stored).normalize();
        if(!target.startsWith(root)) throw bad("非法文件路径");
        try(InputStream input=file.getInputStream()) { MessageDigest digest=MessageDigest.getInstance("SHA-256");
            byte[] bytes=input.readAllBytes(); String hash=HexFormat.of().formatHex(digest.digest(bytes)); Files.write(target,bytes,StandardOpenOption.CREATE_NEW); return new StoredFile(safeOriginal,stored,target.toString(),type,file.getSize(),hash);
        } catch(IOException|NoSuchAlgorithmException e){ throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,"文件保存失败"); }
    }
    public Resource load(String stored) { try { Path p=root.resolve(stored).normalize(); if(!p.startsWith(root)||!Files.isRegularFile(p)) throw new IOException(); return new UrlResource(p.toUri()); } catch(Exception e){ throw new BusinessException(HttpStatus.NOT_FOUND,"材料文件不存在"); } }
    public void delete(String stored) { try { Path p=root.resolve(stored).normalize(); if(!p.startsWith(root)) throw new IOException(); Files.deleteIfExists(p); } catch(IOException e){ throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,"物理文件删除失败"); } }
    private String extension(String n){ int i=n.lastIndexOf('.'); return i<0?"":n.substring(i+1).toLowerCase(Locale.ROOT); }
    private BusinessException bad(String m){return new BusinessException(HttpStatus.BAD_REQUEST,m);}
    public record StoredFile(String original,String stored,String path,String contentType,long size,String sha256){}
}
