package com.example.administrator.groupphotodemo;

import android.content.Context;

import com.alibaba.sdk.android.oss.ClientConfiguration;
import com.alibaba.sdk.android.oss.OSS;
import com.alibaba.sdk.android.oss.OSSClient;
import com.alibaba.sdk.android.oss.common.OSSLog;
import com.alibaba.sdk.android.oss.common.auth.OSSAuthCredentialsProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSCredentialProvider;

/**
 * Created by wangzheng on 2017/11/22.
 */

public class Config {
    // 访问的endpoint地址
    public static final String endpoint = "endpoint";
    public static final String STSSERVER = "STSSERVER";
    public static final String bucket = "bucket";
    public static OssService initOSS(Context mContext, String endpoint, String bucket) {
        OSSCredentialProvider credentialProvider;
        //使用自己的获取STSToken的类
        credentialProvider = new OSSAuthCredentialsProvider(Config.STSSERVER);
        ClientConfiguration conf = new ClientConfiguration();
        conf.setConnectionTimeout(15 * 1000); // 连接超时，默认15秒
        conf.setSocketTimeout(15 * 1000); // socket超时，默认15秒
        conf.setMaxConcurrentRequest(5); // 最大并发请求书，默认5个
        conf.setMaxErrorRetry(2); // 失败后最大重试次数，默认2次
        OSS oss = new OSSClient(mContext, endpoint, credentialProvider, conf);
        OSSLog.enableLog();
        return new OssService(oss, bucket);
    }
}
