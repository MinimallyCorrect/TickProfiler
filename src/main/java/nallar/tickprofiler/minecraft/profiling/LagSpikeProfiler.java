package nallar.tickprofiler.minecraft.profiling;

import com.google.common.base.Function;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import nallar.tickprofiler.Log;
import nallar.tickprofiler.minecraft.commands.Command;
import nallar.tickprofiler.util.CollectionsUtil;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class LagSpikeProfiler {
    private static final StackTraceElement[] EMPTY_STACK_TRACE = new StackTraceElement[0];
    private static final int lagSpikeMillis = 200;
    private static final long lagSpikeNanoSeconds = TimeUnit.SECONDS.toNanos(lagSpikeMillis);
    private static boolean inProgress;
    private static volatile long lastTickTime = 0;
    private final ICommandSender commandSender;
    private final long stopTime;
    private boolean detected;
    private boolean stopping;

    public LagSpikeProfiler(ICommandSender commandSender, int time_) {
        this.commandSender = commandSender;
        stopTime = System.nanoTime() + TimeUnit.SECONDS.toNanos(time_);
    }

    public static boolean profile(ICommandSender commandSender, int time_) {
        synchronized (LagSpikeProfiler.class) {
            if (inProgress) {
                Command.sendChat(commandSender, "Lag spike profiling is already in progress");
                return false;
            }
            inProgress = true;
        }
        Command.sendChat(commandSender, "Started lag spike detection for " + time_ + " seconds.");
        new LagSpikeProfiler(commandSender, time_).start();
        return true;
    }

    public static long tick() {
        long nanoTime = System.nanoTime();
        tick(nanoTime);
        return nanoTime;
    }

    public static void tick(long nanoTime) {
        lastTickTime = nanoTime;
    }

    private static void printThreadDump(StringBuilder sb) {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();
        if (deadlockedThreads == null) {
            TreeMap<String, String> sortedThreads = sortedThreads(threadMXBean);
            sb.append(CollectionsUtil.join(sortedThreads.values(), "\n"));
        } else {
            ThreadInfo[] infos = threadMXBean.getThreadInfo(deadlockedThreads, true, true);
            sb.append("Definitely deadlocked: \n");
            for (ThreadInfo threadInfo : infos) {
                sb.append(toString(threadInfo, true)).append('\n');
            }
        }
    }

    private static TreeMap<String, String> sortedThreads(ThreadMXBean threadMXBean) {
        LoadingCache<String, List<ThreadInfo>> threads = CacheBuilder.newBuilder().build(new CacheLoader<String, List<ThreadInfo>>() {
            @Override
            public List<ThreadInfo> load(final String key) throws Exception {
                return new ArrayList<>();
            }
        });

        ThreadInfo[] t = threadMXBean.dumpAllThreads(true, true);
        for (ThreadInfo thread : t) {
            String info = toString(thread, false);
            if (info != null) {
                threads.getUnchecked(info).add(thread);
            }
        }

        TreeMap<String, String> sortedThreads = new TreeMap<>();
        for (Map.Entry<String, List<ThreadInfo>> entry : threads.asMap().entrySet()) {
            List<ThreadInfo> threadInfoList = entry.getValue();
            ThreadInfo lowest = null;
            for (ThreadInfo threadInfo : threadInfoList) {
                if (lowest == null || threadInfo.getThreadName().toLowerCase().compareTo(lowest.getThreadName().toLowerCase()) < 0) {
                    lowest = threadInfo;
                }
            }
            List threadNameList = CollectionsUtil.newList(threadInfoList, new Function<Object, Object>() {
                @Override
                public Object apply(final Object input) {
                    return ((ThreadInfo) input).getThreadName();
                }
            });
            Collections.sort(threadNameList);
            sortedThreads.put(lowest.getThreadName(), '"' + CollectionsUtil.join(threadNameList, "\", \"") + "\" " + entry.getKey());
        }

        return sortedThreads;
    }

    private static void trySleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
        }
    }

    private static String toString(ThreadInfo threadInfo, boolean name) {
        if (threadInfo == null) {
            return null;
        }
        StackTraceElement[] stackTrace = threadInfo.getStackTrace();
        if (stackTrace == null) {
            stackTrace = EMPTY_STACK_TRACE;
        }
        StringBuilder sb = new StringBuilder();
        if (name) {
            sb.append('"').append(threadInfo.getThreadName()).append('"').append(" Id=").append(threadInfo.getThreadId()).append(' ');
        }
        sb.append(threadInfo.getThreadState());
        if (threadInfo.getLockName() != null) {
            sb.append(" on ").append(threadInfo.getLockName());
        }
        if (threadInfo.getLockOwnerName() != null) {
            sb.append(" owned by \"").append(threadInfo.getLockOwnerName()).append("\" Id=").append(threadInfo.getLockOwnerId());
        }
        if (threadInfo.isSuspended()) {
            sb.append(" (suspended)");
        }
        if (threadInfo.isInNative()) {
            sb.append(" (in native)");
        }
        int run = 0;
        sb.append('\n');
        for (int i = 0; i < stackTrace.length; i++) {
            String steString = stackTrace[i].toString();
            if (steString.contains(".run(")) {
                run++;
            }
            sb.append("\tat ").append(steString);
            sb.append('\n');
            if (i == 0 && threadInfo.getLockInfo() != null) {
                Thread.State ts = threadInfo.getThreadState();
                switch (ts) {
                    case BLOCKED:
                        sb.append("\t-  blocked on ").append(threadInfo.getLockInfo());
                        sb.append('\n');
                        break;
                    case WAITING:
                        sb.append("\t-  waiting on ").append(threadInfo.getLockInfo());
                        sb.append('\n');
                        break;
                    case TIMED_WAITING:
                        sb.append("\t-  waiting on ").append(threadInfo.getLockInfo());
                        sb.append('\n');
                        break;
                    default:
                }
            }

            for (MonitorInfo mi : threadInfo.getLockedMonitors()) {
                if (mi.getLockedStackDepth() == i) {
                    sb.append("\t-  locked ").append(mi);
                    sb.append('\n');
                }
            }
        }

        LockInfo[] locks = threadInfo.getLockedSynchronizers();
        if (locks.length > 0) {
            sb.append("\n\tNumber of locked synchronizers = ").append(locks.length);
            sb.append('\n');
            for (LockInfo li : locks) {
                sb.append("\t- ").append(li);
                sb.append('\n');
            }
        }
        sb.append('\n');
        return (run <= 2 && sb.indexOf("at java.util.concurrent.LinkedBlockingQueue.take(") != -1) ? null : sb.toString();
    }

    private void start() {
        final int sleepTime = Math.max(1000, (lagSpikeMillis * 1000) / 6);
        Thread detectorThread = new Thread(new Runnable() {
            @Override
            public void run() {
                trySleep(60000);
                while (!stopping && checkForLagSpikes()) {
                    trySleep(sleepTime);
                }
                synchronized (LagSpikeProfiler.class) {
                    inProgress = false;
                }
            }
        });
        detectorThread.setName("Lag Spike Detector");
        detectorThread.start();
    }

    public void stop() {
        stopping = true;
    }

    boolean checkForLagSpikes() {
        long time = System.nanoTime();

        if (time > stopTime)
            return false;

        long deadTime = time - lastTickTime;
        if (deadTime < lagSpikeNanoSeconds) {
            detected = false;
            return true;
        }

        if (detected)
            return true;

        final MinecraftServer minecraftServer = MinecraftServer.getServer();
        if (!minecraftServer.isServerRunning() || minecraftServer.isServerStopped()) {
            return false;
        }

        detected = true;
        handleLagSpike(deadTime);
        return true;
    }

    private void handleLagSpike(long deadNanoSeconds) {
        StringBuilder sb = new StringBuilder();
        sb
                .append("The server appears to have ").append("lag spiked.")
                .append("\nLast tick ").append(deadNanoSeconds / 1000000000f).append("s ago.");

        printThreadDump(sb);

        Log.error(sb.toString());

        if (commandSender != null && !(commandSender instanceof DedicatedServer))
            Command.sendChat(commandSender, "Lag spike detected. See console/log for more information.");
        trySleep(15000);
    }
}