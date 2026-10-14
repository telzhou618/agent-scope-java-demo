package com.example.agent.storage;

import java.io.InputStream;

/**
 * 对象存储抽象：上传 / 读取 / 前缀删除 / 私有签名下载。
 * 当前实现：阿里云 OSS；后续可扩展 腾讯云 COS / AWS S3 / MinIO 等。
 */
public interface FileStorage {

    void put(String key, InputStream in, long size, String contentType);

    byte[] get(String key);

    void delete(String key);

    void deletePrefix(String prefix);

    /** 私有桶带签名的临时下载地址 */
    String presignedGetUrl(String key);
}