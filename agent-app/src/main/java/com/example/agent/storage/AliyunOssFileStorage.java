package com.example.agent.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.DeleteObjectsRequest;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObjectSummary;
import com.example.agent.error.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;

/**
 * 阿里云 OSS 对象存储实现：私有桶 + 服务端上传 + 签名下载。
 */
@Slf4j
@Component
public class AliyunOssFileStorage implements FileStorage {

    private final String endpoint;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String bucket;
    private final long presignSeconds;

    private volatile OSS client;

    public AliyunOssFileStorage(@Value("${agent.storage.oss.endpoint:}") String endpoint,
                                @Value("${agent.storage.oss.access-key-id:}") String accessKeyId,
                                @Value("${agent.storage.oss.access-key-secret:}") String accessKeySecret,
                                @Value("${agent.storage.oss.bucket:}") String bucket,
                                @Value("${agent.storage.oss.presign-seconds:604800}") long presignSeconds) {
        this.endpoint = endpoint;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.bucket = bucket;
        this.presignSeconds = presignSeconds;
    }

    private OSS oss() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    if (!StringUtils.hasText(endpoint) || !StringUtils.hasText(accessKeyId)
                            || !StringUtils.hasText(accessKeySecret) || !StringUtils.hasText(bucket)) {
                        throw new BizException("OSS 未配置（endpoint / accessKey / bucket）");
                    }
                    client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
                }
            }
        }
        return client;
    }

    @Override
    public void put(String key, InputStream in, long size, String contentType) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(size);
            metadata.setContentType(contentType);
            oss().putObject(bucket, key, in, metadata);
        } catch (Exception e) {
            throw new BizException("文件上传失败：" + e.getMessage());
        }
    }

    @Override
    public byte[] get(String key) {
        try (InputStream in = oss().getObject(bucket, key).getObjectContent();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException("文件读取失败");
        }
    }

    @Override
    public void delete(String key) {
        try {
            oss().deleteObject(bucket, key);
        } catch (Exception e) {
            throw new BizException("文件删除失败：" + e.getMessage());
        }
    }

    @Override
    public void deletePrefix(String prefix) {
        try {
            String nextMarker = null;
            ObjectListing listing;
            do {
                listing = oss().listObjects(new ListObjectsRequest(bucket).withPrefix(prefix).withMarker(nextMarker));
                var keys = listing.getObjectSummaries().stream().map(OSSObjectSummary::getKey).toList();
                if (!keys.isEmpty()) {
                    oss().deleteObjects(new DeleteObjectsRequest(bucket).withKeys(keys));
                }
                nextMarker = listing.getNextMarker();
            } while (listing.isTruncated());
        } catch (Exception e) {
            throw new BizException("文件删除失败：" + e.getMessage());
        }
    }

    @Override
    public String presignedGetUrl(String key) {
        Date expiry = new Date(System.currentTimeMillis() + presignSeconds * 1000L);
        URL url = oss().generatePresignedUrl(bucket, key, expiry);
        return url.toString();
    }
}