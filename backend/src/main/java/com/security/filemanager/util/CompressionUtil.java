package com.security.filemanager.util;

import lombok.extern.slf4j.Slf4j;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 压缩工具类
 * 
 * 【作用】
 * 在加密前压缩数据，减少存储空间占用
 * 在解密后解压数据，还原原始内容
 * 
 * 【算法】GZIP
 * - 标准的压缩算法
 * - 压缩率高
 * - 兼容性好
 * 
 * @author CourseDesign
 */
@Slf4j
public class CompressionUtil {
    
    /**
     * 压缩数据
     * 
     * @param data 原始数据
     * @return 压缩后的数据
     */
    public static byte[] compress(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            
            gzip.write(data);
            gzip.finish();
            
            byte[] compressed = bos.toByteArray();
            
            log.debug("数据压缩 - 原始: {} bytes, 压缩后: {} bytes, 压缩率: {}", 
                    data.length, 
                    compressed.length, 
                    String.format("%.2f%%", (1 - (double)compressed.length / data.length) * 100));
            
            return compressed;
            
        } catch (Exception e) {
            log.error("数据压缩失败", e);
            throw new RuntimeException("数据压缩失败", e);
        }
    }
    
    /**
     * 解压数据
     * 
     * @param compressedData 压缩后的数据
     * @return 原始数据
     */
    public static byte[] decompress(byte[] compressedData) {
        if (compressedData == null || compressedData.length == 0) {
            return compressedData;
        }
        
        try (ByteArrayInputStream bis = new ByteArrayInputStream(compressedData);
             GZIPInputStream gzip = new GZIPInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            
            byte[] buffer = new byte[8192];
            int len;
            while ((len = gzip.read(buffer)) > 0) {
                bos.write(buffer, 0, len);
            }
            
            byte[] decompressed = bos.toByteArray();
            
            log.debug("数据解压 - 压缩: {} bytes, 解压后: {} bytes", 
                    compressedData.length, 
                    decompressed.length);
            
            return decompressed;
            
        } catch (Exception e) {
            log.error("数据解压失败", e);
            throw new RuntimeException("数据解压失败", e);
        }
    }
}
