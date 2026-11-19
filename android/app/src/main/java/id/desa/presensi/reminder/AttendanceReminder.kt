package id.desa.presensi.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import id.desa.presensi.MainActivity
import id.desa.presensi.R
import java.util.Calendar

object AttendanceReminder {
    const val CHANNEL_ID = "presensi_reminder"
    private const val EXTRA_TYPE = "reminder_type"
    private const val REQ_MORNING = 1001
    private const val REQ_AFTERNOON = 1002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pengingat Presensi Desa",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Pengingat harian waktu absen masuk dan pulang perangkat desa."
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun scheduleDaily(context: Context) {
        ensureChannel(context)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduleAt(context, alarm, REQ_MORNING, 7, 15, "masuk")
        scheduleAt(context, alarm, REQ_AFTERNOON, 15, 45, "pulang")
    }

    fun cancel(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelOne(context, alarm, REQ_MORNING, "masuk")
        cancelOne(context, alarm, REQ_AFTERNOON, "pulang")
    }

    private fun scheduleAt(
        context: Context,
        alarm: AlarmManager,
        requestCode: Int,
        hour: Int,
        minute: Int,
        type: String,
    ) {
        val intent = Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_TYPE, type)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        alarm.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pending,
        )
    }

    private fun cancelOne(
        context: Context,
        alarm: AlarmManager,
        requestCode: Int,
        type: String,
    ) {
        val intent = Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_TYPE, type)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarm.cancel(pending)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AttendanceReminder.ensureChannel(context)
        val type = intent.getStringExtra("reminder_type") ?: "masuk"
        val isMorning = type == "masuk"

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            if (isMorning) 2001 else 2002,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (isMorning) "Waktunya Absen Masuk" else "Waktunya Absen Pulang"
        val body = if (isMorning) {
            "Jangan lupa pindai QR di kantor desa sebelum jam kerja dimulai."
        } else {
            "Pastikan Anda sudah mencatat absen pulang sebelum meninggalkan kantor desa."
        }

        val notification = NotificationCompat.Builder(context, AttendanceReminder.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(if (isMorning) 3001 else 3002, notification)
    }
}
