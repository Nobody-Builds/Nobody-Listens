package com.nobodybuilds.listens.ui.glancewidget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.nobodybuilds.listens.MainActivity
import com.nobodybuilds.listens.data.garden.GardenRepository
import com.nobodybuilds.listens.presentation.garden.GardenColors
import com.nobodybuilds.listens.presentation.garden.GardenPalette
import com.nobodybuilds.listens.presentation.garden.GardenSceneSpec
import com.nobodybuilds.listens.presentation.garden.buildGardenLayout
import com.nobodybuilds.listens.presentation.garden.renderGardenBitmap
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GardenWidgetEntryPoint {
    fun gardenRepository(): GardenRepository
}

private class GardenWidgetData(val bitmap: Bitmap?, val dayLabel: String)

/** 2x2 "Garden" widget: the listening-garden plant and "Day N". */
class GardenWidget2x2 : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = runCatching { load(context) }
            .onFailure { Timber.w(it, "Garden widget: load failed") }
            .getOrElse { GardenWidgetData(null, "Garden") }
        provideContent {
            GlanceTheme(colors = NbGlanceColors) {
                GardenContent(data)
            }
        }
    }

    private suspend fun load(context: Context): GardenWidgetData {
        val repo = EntryPointAccessors
            .fromApplication(context.applicationContext, GardenWidgetEntryPoint::class.java)
            .gardenRepository()
        val snapshot = repo.snapshot()
        val colors = GardenColors.flowerColors(repo.recentMoodMix())
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val bitmap = withContext(Dispatchers.Default) {
            val layout = buildGardenLayout(
                GardenSceneSpec(
                    stage = snapshot.growth.stage,
                    treeMaturity = snapshot.growth.treeMaturity,
                    seed = snapshot.seed,
                    flowerColors = colors,
                    season = snapshot.season,
                    milestones = snapshot.growth.milestonesReached,
                )
            )
            renderGardenBitmap(layout, if (night) GardenPalette.Dark else GardenPalette.Light, BITMAP_SIZE)
        }
        val label = if (snapshot.gardenDay > 0) "Day ${snapshot.gardenDay}" else "New garden"
        return GardenWidgetData(bitmap, label)
    }

    @Composable
    private fun GardenContent(data: GardenWidgetData) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(24.dp)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            data.bitmap?.let { bmp ->
                Image(
                    provider = ImageProvider(bmp),
                    contentDescription = "Your listening garden",
                    contentScale = ContentScale.Fit,
                    modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                )
            }
            Text(
                text = data.dayLabel,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
    }

    private companion object {
        const val BITMAP_SIZE = 360
    }
}
