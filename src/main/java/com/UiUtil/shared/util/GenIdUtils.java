package com.UiUtil.shared.util;

/**
 * 分布式唯一 ID 生成工具，基于机器 MAC 地址 + 进程 PID + 时间戳 + 原子序列号实现，
 * 生成全局唯一的数字型 ID，用于商品编号、店铺编号等业务主键生成。
 */
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
                    return (long) (Math.random() * MAX_WORKER_ID);
                }
                try {
                    Thread.sleep(RETRY_INTERVAL);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                System.err.println("机器ID生成失败，重试第" + retryCount + "次，原因：" + e.getMessage());
            }
        }
        return (long) (Math.random() * MAX_WORKER_ID);
    }

    /**
     * 获取全局唯一雪花ID（核心方法，带重试）
     */
    public static long getSnowflakeId() {
        int retryCount = 0;
        while (retryCount < CLOCK_BACK_RETRY_COUNT) {
            try {
                return generateId();
            } catch (RuntimeException e) {
                retryCount++;
                if (retryCount >= CLOCK_BACK_RETRY_COUNT) {
                    throw new RuntimeException("ID生成失败（重试" + CLOCK_BACK_RETRY_COUNT + "次）：" + e.getMessage(), e);
                }
                try {
                    Thread.sleep(RETRY_INTERVAL);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("ID生成重试时线程中断", ie);
                }
                System.err.println("ID生成失败，重试第" + retryCount + "次，原因：" + e.getMessage());
            }
        }
        throw new RuntimeException("ID生成失败，重试次数耗尽");
    }

    /**
     * 核心ID生成逻辑（抽离出来方便重试）
     */
    private static long generateId() {
        long currentTimestamp = System.currentTimeMillis();
        long lastTimestamp = LAST_TIMESTAMP.get();

        if (currentTimestamp < lastTimestamp) {
            long offset = lastTimestamp - currentTimestamp;
            if (offset <= 5) {
                try {
                    Thread.sleep(offset + 1);
                    currentTimestamp = System.currentTimeMillis();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("时钟回摆补偿时线程中断", e);
                }
            } else {
                throw new RuntimeException(
                        String.format("系统时钟回摆异常！上次时间戳：%d，当前时间戳：%d", lastTimestamp, currentTimestamp)
                );
            }
        }

        if (currentTimestamp == lastTimestamp) {
            long sequence = SEQUENCE.incrementAndGet() & MAX_SEQUENCE;
            if (sequence == 0) {
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            SEQUENCE.set(0L);
        }

        if (!LAST_TIMESTAMP.compareAndSet(lastTimestamp, currentTimestamp)) {
            throw new RuntimeException("时间戳更新失败，多线程竞争冲突");
        }

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

    private GenIdUtils() {}
}
