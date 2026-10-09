package com.jagaldol.dailytarot

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jagaldol.dailytarot.widget.DailyTarotWidgetReceiver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetHostTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun twoBoundWidgetsShowTodaysReadingAndRefreshAfterRapidChanges() {
        val activity = compose.activity
        val app = activity.application as TarotApplication
        val manager = AppWidgetManager.getInstance(activity)
        val host = AppWidgetHost(activity, 73017)
        val views = mutableListOf<AppWidgetHostView>()
        runBlocking {
            app.sync.disconnect()
            app.repository.drawToday()
            app.repository.selectManually(17, true)
        }
        shell("appwidget grantbind --package ${activity.packageName}")
        try {
            compose.runOnIdle {
                host.startListening()
                repeat(2) {
                    val id = host.allocateAppWidgetId()
                    val options = Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 180)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180)
                    }
                    assertTrue(manager.bindAppWidgetIdIfAllowed(
                        id, ComponentName(activity, DailyTarotWidgetReceiver::class.java), options,
                    ))
                    val view = host.createView(activity, id, manager.getAppWidgetInfo(id))
                    (activity.window.decorView as ViewGroup).addView(view, FrameLayout.LayoutParams(400, 600))
                    views.add(view)
                }
            }
            waitForImages(views, description(17, reversed = true))
            runBlocking {
                app.repository.selectManually(2, false)
                app.repository.selectManually(21, false)
            }
            waitForImages(views, description(21, reversed = false))
        } finally {
            compose.runOnIdle {
                views.forEach { (it.parent as? ViewGroup)?.removeView(it) }
                host.stopListening()
                host.deleteHost()
            }
            shell("appwidget revokebind --package ${activity.packageName}")
        }
    }

    private fun waitForImages(views: List<AppWidgetHostView>, description: String) {
        compose.waitUntil(20_000) {
            var matched = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                matched = views.all { containsDescription(it, description) }
            }
            matched
        }
    }

    private fun containsDescription(view: View, description: String): Boolean {
        if (view.contentDescription == description) return true
        return view is ViewGroup && (0 until view.childCount).any {
            containsDescription(view.getChildAt(it), description)
        }
    }

    private fun shell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }
}
