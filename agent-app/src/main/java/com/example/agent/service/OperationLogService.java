package com.example.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agent.entity.OperationLog;
import com.example.agent.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 操作日志：写入与分页查询
 */
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    public void save(OperationLog operationLog) {
        operationLogMapper.insert(operationLog);
    }

    /**
     * 分页列表：用户名/操作描述/请求路径任一模糊命中
     */
    public Page<OperationLog> page(long page, long size, String keyword) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(OperationLog::getUsername, keyword)
                    .or().like(OperationLog::getOperation, keyword)
                    .or().like(OperationLog::getPath, keyword));
        }
        wrapper.orderByDesc(OperationLog::getId);
        return operationLogMapper.selectPage(new Page<>(page, size), wrapper);
    }
}
