

package com.example.agent.error;

/**
 * 错误编码
 *
 * @author zhougaojun
 * @since 1.0.0
 */
public interface ErrorCode {
    int INTERNAL_SERVER_ERROR = 500;
    int UNAUTHORIZED = 401;

    int NOT_NULL = 10001;
    int PARAMS_GET_ERROR = 10003;
}
