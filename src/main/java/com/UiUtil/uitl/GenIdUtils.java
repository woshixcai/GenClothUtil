package com.UiUtil.uitl;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 雪花算法ID生成工具类（含异常重试+时钟回摆补偿）
 */
public class GenIdUtils {
    // ====================== 核心参数 ======================
    private static final long START_TIMESTAMP = 1704067200000L;
    private static final long WORKER_ID_BITS = 10L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    // ====================== 运行时参数 ======================
    private static final long WORKER_ID;
    private static final AtomicLong SEQUENCE = new AtomicLong(0L);
    private static final AtomicLong LAST_TIMESTAMP = new AtomicLong(-1L);

    // ====================== 重试配置（可根据业务调整） ======================
    /** 机器ID生成重试次数 */
    private static final int WORKER_ID_RETRY_COUNT = 3;
    /** 时钟回摆重试次数 */
    private static final int CLOCK_BACK_RETRY_COUNT = 3;
    /** 重试间隔（毫秒） */
    private static final long RETRY_INTERVAL = 100L;

    // ====================== 静态初始化机器ID（带重试） ======================
    static {
        WORKER_ID = generateWorkerIdWithRetry();
    }

    /**
     * 生成机器ID（带重试机制）
     */
    private static long generateWorkerIdWithRetry() {
        int retryCount = 0;
        while (retryCount < WORKER_ID_RETRY_COUNT) {
            try {
                // 正常生成机器ID逻辑
                InetAddress address = InetAddress.getLocalHost();
                NetworkInterface ni = NetworkInterface.getByInetAddress(address);
                if (ni == null) {
                    throw new RuntimeException("网卡信息为空");
                }
                byte[] mac = ni.getHardwareAddress();
                if (mac == null || mac.length == 0) {
                    throw new RuntimeException("MAC地址为空");
                }
                long macLong = 0;
                for (int i = 0; i < 6; i++) {
                    macLong |= ((long) (mac[i] & 0xff)) << (8 * (5 - i));
                }
                long macPart = macLong % 1024;

                String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
                long pidPart = Long.parseLong(pid) % 1024;

                long workerId = (macPart + pidPart) % MAX_WORKER_ID;
                return workerId;
            } catch (Exception e) {
                retryCount++;
                if (retryCount >= WORKER_ID_RETRY_COUNT) {
                    // 重试耗尽，降级为随机生成
                    return (long) (Math.random() * MAX_WORKER_ID);
                }
                // 重试间隔
                try {
                    Thread.sleep(RETRY_INTERVAL);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                System.err.println("机器ID生成失败，重试第" + retryCount + "次，原因：" + e.getMessage());
            }
        }
        // 兜底：随机生成
        return (long) (Math.random() * MAX_WORKER_ID);
    }

    /**
     * 获取全局唯一雪花ID（核心方法，带重试）
     */
    public static long getSnowflakeId() {
        int retryCount = 0;
        while (retryCount < CLOCK_BACK_RETRY_COUNT) {
            try {
                return generateId(); // 核心生成逻辑
            } catch (RuntimeException e) {
                retryCount++;
                if (retryCount >= CLOCK_BACK_RETRY_COUNT) {
                    throw new RuntimeException("ID生成失败（重试" + CLOCK_BACK_RETRY_COUNT + "次）：" + e.getMessage(), e);
                }
                // 重试间隔
                try {
                    Thread.sleep(RETRY_INTERVAL);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("ID生成重试时线程中断", ie);
                }
                System.err.println("ID生成失败，重试第" + retryCount + "次，原因：" + e.getMessage());
            }
        }
        // 理论上不会走到这里，兜底抛出异常
        throw new RuntimeException("ID生成失败，重试次数耗尽");
    }

    /**
     * 核心ID生成逻辑（抽离出来方便重试）
     */
    private static long generateId() {
        long currentTimestamp = System.currentTimeMillis();
        long lastTimestamp = LAST_TIMESTAMP.get();

        // 时钟回摆处理
        if (currentTimestamp < lastTimestamp) {
            long offset = lastTimestamp - currentTimestamp;
            if (offset <= 5) {
                // 小幅度回摆：sleep补偿
                try {
                    Thread.sleep(offset + 1);
                    currentTimestamp = System.currentTimeMillis();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("时钟回摆补偿时线程中断", e);
                }
            } else {
                // 大幅度回摆：抛出异常（触发重试）
                throw new RuntimeException(
                        String.format("系统时钟回摆异常！上次时间戳：%d，当前时间戳：%d", lastTimestamp, currentTimestamp)
                );
            }
        }

        // 序列号生成
        if (currentTimestamp == lastTimestamp) {
            long sequence = SEQUENCE.incrementAndGet() & MAX_SEQUENCE;
            if (sequence == 0) {
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            SEQUENCE.set(0L);
        }

        // 更新最后时间戳
        if (!LAST_TIMESTAMP.compareAndSet(lastTimestamp, currentTimestamp)) {
            // CAS失败（多线程竞争），抛出异常触发重试
            throw new RuntimeException("时间戳更新失败，多线程竞争冲突");
        }

        // 组装ID
        return (currentTimestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT
                | (WORKER_ID << WORKER_ID_SHIFT)
                | SEQUENCE.get();
    }

    /**
     * 等待直到下一毫秒
     */
    private static long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    /**
     * 私有构造方法
     */
    private GenIdUtils() {}

}