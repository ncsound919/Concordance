package com.example.concordance.autonomy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.concordance.integration.agent.AgentClient
import com.example.concordance.integration.agent.AgentSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HighPowerResearchService : Service() {

    companion object {
        private const val CHANNEL_ID = "high_power_research"
        private const val NOTIFICATION_ID = 3001
        const val ACTION_START = "com.example.concordance.action.HIGH_POWER_START"
        const val ACTION_STOP = "com.example.concordance.action.HIGH_POWER_STOP"
        const val EXTRA_GOAL = "goal"

        fun start(context: Context, goal: String) {
            val intent = Intent(context, HighPowerResearchService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_GOAL, goal)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, HighPowerResearchService::class.java).setAction(ACTION_STOP)
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: kotlinx.coroutines.Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "concordance:high-power").apply {
            setReferenceCounted(false)
        }
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                job?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val goal = intent.getStringExtra(EXTRA_GOAL) ?: "research"
                AutonomyModeManager.init(this)
                AutonomyModeManager.go()
                AutonomyModeManager.setCurrentGoal(goal)
                ForegroundServiceTypes.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification("Starting high-power research..."),
                    ForegroundServiceTypes.SPECIAL_USE
                )
                wakeLock?.acquire(2 * 60 * 60 * 1000L)
                job = scope.launch {
                    runResearch(goal)
                }
            }
        }
        return START_STICKY
    }

    private suspend fun runResearch(goal: String) {
        AutonomyActivity.init(this)
        val entry = AutonomyActivity.start(
            AutonomyActivity.SOURCE_HIGH_POWER,
            "High power research: $goal"
        )
        val config = AgentSettingsStore(this).load()
        val client = AgentClient()
        notify("High power: researching", goal)
        try {
            when (val outcome = client.research(config, goal, useLlm = true)) {
                is com.example.concordance.integration.agent.AgentOutcome.Ok -> {
                    val r = outcome.value
                    AutonomyModeManager.tickStep()
                    val plan = ResearchRouting.plan(r.experiments)
                    // Only experiments the agent routed to a heavy backend are enqueued;
                    // local/cloudflare work is not the phone's to schedule and is reported.
                    val routedJobs = mutableListOf<String>()
                    for (target in plan.routed) {
                        val policy = target.policy ?: continue
                        val status = com.example.concordance.integration.queue.JobQueueManager.submitHeavyProcess(
                            dataClass = com.example.concordance.integration.queue.JobDataClass.RESEARCH_EXPERIMENT,
                            routingPolicy = policy,
                            inputs = target.experiment.title,
                            modelRevision = "research-v1",
                            params = mapOf(
                                "experiment" to target.experiment.title,
                                "design" to target.experiment.design,
                                "run_id" to r.runId
                            )
                        )
                        routedJobs.add(status.taskUuid)
                    }
                    // Poll routed jobs so the notification stops only when they settle.
                    for (jobUuid in routedJobs) {
                        var attempts = 0
                        while (attempts < 60) {
                            val st = com.example.concordance.integration.queue.JobQueueManager.getJobStatus(jobUuid)
                            if (st?.status == com.example.concordance.integration.queue.JobExecutionStatus.COMPLETED ||
                                st?.status == com.example.concordance.integration.queue.JobExecutionStatus.FAILED) {
                                break
                            }
                            kotlinx.coroutines.delay(2000)
                            attempts++
                        }
                    }
                    val msg = "Done: ${r.experiments.size} experiment(s) · ${plan.summary()} · " +
                            "critic ${r.criticVerdict} · ${r.paperArtifacts} paper file(s)"
                    ResearchRunStore.publish(ResearchRun(goal, result = r, routing = plan))
                    notify("High power: complete", msg)
                    AutonomyActivity.finish(entry, ActivityLevel.OK, msg)
                    android.util.Log.i("HighPowerResearch", "Run ${r.runId}: ${r.experiments.size} experiments, critic=${r.criticVerdict}, ${plan.summary()}")
                }
                is com.example.concordance.integration.agent.AgentOutcome.Refused -> {
                    ResearchRunStore.publish(ResearchRun(goal, error = "Refused: ${outcome.reason}"))
                    notify("High power: refused", outcome.reason)
                    AutonomyActivity.finish(entry, ActivityLevel.SKIPPED, "Refused: ${outcome.reason}")
                }
                is com.example.concordance.integration.agent.AgentOutcome.Failed -> {
                    ResearchRunStore.publish(ResearchRun(goal, error = outcome.message))
                    notify("High power: failed", outcome.message)
                    AutonomyActivity.finish(entry, ActivityLevel.FAILED, outcome.message)
                }
            }
        } catch (e: Exception) {
            ResearchRunStore.publish(ResearchRun(goal, error = e.message ?: e.javaClass.simpleName))
            notify("High power: error", e.message ?: "unknown error")
            AutonomyActivity.finish(
                entry,
                ActivityLevel.FAILED,
                "Error: ${e.message ?: e.javaClass.simpleName}"
            )
        } finally {
            wakeLock?.takeIf { it.isHeld }?.release()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun notify(title: String, text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        val open = packageManager.getLaunchIntentForPackage(packageName)
        val pi = open?.let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE) }
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .build()
        nm.notify(NOTIFICATION_ID, n)
    }

    private fun notification(text: String): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)
        val pi = open?.let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE) }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Concordance high power")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "High power research", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps high-power research running"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }
}
