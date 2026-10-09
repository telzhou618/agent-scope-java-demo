package com.example.agent.service;

import com.example.agent.entity.OperationLog;
import com.example.agent.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 操作日志写入
 */
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    public void save(OperationLog operationLog) {
        operationLogMapper.insert(operationLog);
    }
}
