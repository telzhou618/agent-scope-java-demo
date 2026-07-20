

package com.example.agent.error;

/**
 * 错误编码，由5位数字组成，前2位为模块编码，后3位为业务编码
 * <p>
 * 如：10001（10代表系统模块，001代表业务代码）
 * </p>
 *
 * @author zhougaojun
 * @since 1.0.0
 */
public interface ErrorCode {
    int INTERNAL_SERVER_ERROR = 500;
    int UNAUTHORIZED = 401;

    int NOT_NULL = 10001;
    int DB_RECORD_EXISTS = 10002;
    int PARAMS_GET_ERROR = 10003;
    int ACCOUNT_PASSWORD_ERROR = 10004;
    int ACCOUNT_DISABLE = 10005;
    int IDENTIFIER_NOT_NULL = 10006;
    int CAPTCHA_ERROR = 10007;
    int SUB_MENU_EXIST = 10008;
    int PASSWORD_ERROR = 10009;
    int SUPERIOR_DEPT_ERROR = 10011;
    int SUPERIOR_MENU_ERROR = 10012;
    int DATA_SCOPE_PARAMS_ERROR = 10013;
    int DEPT_SUB_DELETE_ERROR = 10014;
    int DEPT_USER_DELETE_ERROR = 10015;
    int UPLOAD_FILE_EMPTY = 10019;
    int TOKEN_NOT_EMPTY = 10020;
    int TOKEN_INVALID = 10021;
    int ACCOUNT_LOCK = 10022;
    int OSS_UPLOAD_FILE_ERROR = 10024;
    int REDIS_ERROR = 10027;
    int JOB_ERROR = 10028;
    int INVALID_SYMBOL = 10029;
    int SENTINEL_FLOW_EXCEPTION = 10030;
    int SENTINEL_PARAM_FLOW_EXCEPTION = 10031;
    int SENTINEL_DEGRADE_EXCEPTION = 10032;
    int SENTINEL_DISTRIBUTED_LOCK_EXCEPTION = 10033;
    int QRCODE_EXPIRED = 10034;
    /**
     * 道具发送失败
     */
    int PROPS_SEND_FAIL = 10035;
    /**
     * 道具接口调用失败
     */
    int PROPS_INTERFACE_ERROR = 10036;
}
