//package com.wzz.smscode.config; // 请替换为您的实际包路径
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
//import java.util.concurrent.Executor;
//import java.util.concurrent.ThreadPoolExecutor;
//
//@Configuration
//public class ThreadPoolTaskExecutorConfig {
//
//    /**
//     * 定义一个名为 "taskExecutor" 的线程池 Bean。
//     *Async("taskExecutor")  注解会默认使用这个线程池。
//     * @return Executor
//     */
//    @Bean(name = "taskExecutor")
//    public Executor taskExecutor() {
//        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
//        // 1. 核心线程数：线程池中常驻的线程数量。即使它们处于空闲状态，也不会被销毁。
//        // 可以根据您系统的并发量来设置，例如 CPU 核心数的 2 倍。
//        int corePoolSize = Runtime.getRuntime().availableProcessors() * 2;
//        executor.setCorePoolSize(corePoolSize);
//        // 2. 最大线程数：线程池能容纳的最大线程数。当队列满了之后，会创建新线程，直到达到这个数量。
//        int maxPoolSize = corePoolSize * 2;
//        executor.setMaxPoolSize(maxPoolSize);
//        // 3. 任务队列容量：当核心线程都在忙时，新来的任务会进入这个队列等待。
//        // 设置一个合理的队列大小，防止内存溢出。
//        executor.setQueueCapacity(200);
//        // 4. 线程名称前缀：方便在日志中识别出是哪个线程池执行的任务。
//        executor.setThreadNamePrefix("async-task-");
//        // 5. 拒绝策略：当线程池和队列都满了之后，如何处理新来的任务。
//        // CallerRunsPolicy：由调用线程（提交任务的线程）自己来执行这个任务，这是一种简单的降级策略。
//        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
//        // 6. 线程空闲时间：当线程数超过核心线程数时，多余的空闲线程在被销毁前等待新任务的最长时间。
//        executor.setKeepAliveSeconds(60);
//        // 初始化线程池
//        executor.initialize();
//        return executor;
//    }
//}

package com.wzz.smscode.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration
public class ThreadPoolTaskExecutorConfig {

    @Bean(name = "taskExecutor")
    public AsyncTaskExecutor taskExecutor() {
        // 1. 创建一个带名称的虚拟线程工厂
        ThreadFactory factory = Thread.ofVirtual().name("vt-task-", 0).factory();
        // 2. 使用该工厂创建执行器
        return new TaskExecutorAdapter(Executors.newThreadPerTaskExecutor(factory));
    }
}