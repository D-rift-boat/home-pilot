package com.dboat.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.user.entity.SysAuditLog;
import com.dboat.user.service.SysAuditLogService;
import com.dboat.user.mapper.SysAuditLogMapper;
import org.springframework.stereotype.Service;

/**
* @author tanghj
* @description 针对表【sys_audit_log(系统操作审计日志表)】的数据库操作Service实现
* @createDate 2026-09-13 18:29:59
*/
@Service
public class SysAuditLogServiceImpl extends ServiceImpl<SysAuditLogMapper, SysAuditLog>
    implements SysAuditLogService{

}




