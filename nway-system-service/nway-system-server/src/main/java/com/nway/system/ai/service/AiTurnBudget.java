package com.nway.system.ai.service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 单轮分析预算：累计本轮所有进入模型上下文的工具结果字符数。
 * <p>
 * 预算放在 RuntimeContext 中按轮创建，工具在返回结果前消费；超预算后返回 handoff，
 * 避免多个中等结果累加后挤爆上下文。
 *
 * @author nway
 */
public class AiTurnBudget {

    private final int maxChars;

    private final AtomicLong usedChars = new AtomicLong();

    public AiTurnBudget(int maxChars) {
        this.maxChars = Math.max(0, maxChars);
    }

    /**
     * 消费字符数，返回消费后是否已超出预算。
     */
    public boolean consume(long chars) {
        if (chars <= 0) {
            return usedChars.get() > maxChars;
        }
        return usedChars.addAndGet(chars) > maxChars;
    }

    public boolean exceeded() {
        return maxChars > 0 && usedChars.get() > maxChars;
    }

    public int getMaxChars() {
        return maxChars;
    }

    public long getUsedChars() {
        return usedChars.get();
    }
}
