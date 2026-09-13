package com.dboat.user.dto.session;

import lombok.Builder;
import lombok.Data;

/**
 * Refresh Token 消费结果
 * <p>
 * 由 {@code LUA_REFRESH_CONSUME} 脚本原子执行后解析得到，用于区分三种结果：
 * <ul>
 *   <li>{@link Status#SUCCESS}   —— 消费成功，可签发新的双令牌</li>
 *   <li>{@link Status#NOT_FOUND} —— 令牌不存在或已过期，需要重新登录</li>
 *   <li>{@link Status#REPLAY}    —— 已作废的旧令牌被再次使用，判定令牌泄漏，需撤销该用户全部会话</li>
 * </ul>
 * </p>
 *
 * @author dboat
 */
@Data
@Builder
public class RefreshConsumeResult {

    /**
     * 消费状态枚举
     */
    public enum Status {
        /** 消费成功 */
        SUCCESS,
        /** 令牌不存在或已过期 */
        NOT_FOUND,
        /** 重放攻击 */
        REPLAY
    }

    /** 消费状态 */
    private Status status;

    /** 关联的用户ID（重放场景下同样返回，用于撤销全部会话） */
    private String userId;

    /** 关联的租户ID */
    private String orgId;

    /** 原会话信息，仅 {@link Status#SUCCESS} 时有值 */
    private UserSessionDTO session;

    /**
     * 是否消费成功
     *
     * @return true=可以签发新令牌
     */
    public boolean isSuccess() {
        return Status.SUCCESS == status;
    }

    /**
     * 是否为重放攻击
     *
     * @return true=检测到已作废令牌被重用
     */
    public boolean isReplay() {
        return Status.REPLAY == status;
    }
}
