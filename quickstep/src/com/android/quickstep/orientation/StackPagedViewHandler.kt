/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.quickstep.orientation

import android.graphics.Matrix
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ShapeDrawable
import android.util.FloatProperty
import android.util.Pair
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.updateLayoutParams
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Flags.enableOverviewIconMenu
import com.android.launcher3.LauncherAnimUtils
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.logger.LauncherAtom
import com.android.launcher3.touch.DefaultPagedViewHandler
import com.android.launcher3.touch.PagedOrientationHandler.Float2DAction
import com.android.launcher3.touch.PagedOrientationHandler.Int2DAction
import com.android.launcher3.touch.SingleAxisSwipeDetector
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition
import com.android.quickstep.views.IconAppChipView
import kotlin.math.max
import kotlin.math.min

/**
 * Handler for iOS-style vertical stack view in RecentsView.
 * Tasks are stacked vertically with depth effect - front task is larger,
 * back tasks are progressively smaller and shifted up.
 */
class StackPagedViewHandler : DefaultPagedViewHandler(), RecentsPagedOrientationHandler {
    private val tmpMatrix = Matrix()
    private val tmpRectF = RectF()

    companion object {
        const val STACK_SCALE_FACTOR = 0.08f
        const val STACK_TRANSLATION_Y_FACTOR = 60f
        const val STACK_ROTATION_FACTOR = 2f
        const val MAX_VISIBLE_TASKS = 5
    }

    override fun <T> getPrimaryValue(x: T, y: T): T = y

    override fun <T> getSecondaryValue(x: T, y: T): T = x

    override val isLayoutNaturalToLauncher: Boolean = true

    override fun adjustFloatingIconStartVelocity(velocity: PointF) {
        val tempX = velocity.x
        velocity.x = velocity.y
        velocity.y = tempX
    }

    override fun fixBoundsForHomeAnimStartRect(outStartRect: RectF, deviceProfile: DeviceProfile) {
        if (outStartRect.top > deviceProfile.heightPx) {
            outStartRect.offsetTo(outStartRect.left, 0f)
        } else if (outStartRect.top < -deviceProfile.heightPx) {
            outStartRect.offsetTo(outStartRect.left, 0f)
        }
    }

    override fun <T> setSecondary(target: T, action: Float2DAction<T>, param: Float) =
        action.call(target, 0f, param)

    override fun <T> set(
        target: T,
        action: Int2DAction<T>,
        primaryParam: Int,
        secondaryParam: Int,
    ) = action.call(target, secondaryParam, primaryParam)

    override fun getPrimarySize(view: View): Int = view.height

    override fun getPrimarySize(rect: RectF): Float = rect.height()

    override fun getStart(rect: RectF): Float = rect.top

    override fun getEnd(rect: RectF): Float = rect.bottom

    override fun rotateInsets(insets: Rect, outInsets: Rect) {
        outInsets.set(insets.top, insets.left, insets.bottom, insets.right)
    }

    override fun getClearAllSidePadding(view: View, isRtl: Boolean): Int =
        (if (isRtl) view.paddingBottom else -view.paddingTop) / 2

    override fun getSecondaryDimension(view: View): Int = view.width

    override val primaryViewTranslate: FloatProperty<View> = LauncherAnimUtils.VIEW_TRANSLATE_Y

    override val secondaryViewTranslate: FloatProperty<View> = LauncherAnimUtils.VIEW_TRANSLATE_X

    override val degreesRotated: Float = 0f

    override val rotation: Int = Surface.ROTATION_0

    override fun setPrimaryScale(view: View, scale: Float) {
        view.scaleY = scale
    }

    override fun setSecondaryScale(view: View, scale: Float) {
        view.scaleX = scale
    }

    override val secondaryTranslationDirectionFactor: Int
        get() = 1

    override fun getSplitTranslationDirectionFactor(
        stagePosition: Int,
        deviceProfile: DeviceProfile,
    ): Int = 1

    override fun getTaskMenuX(
        x: Float,
        thumbnailView: View,
        deviceProfile: DeviceProfile,
        taskInsetMargin: Float,
        taskViewIcon: View,
    ): Float = x + taskInsetMargin

    override fun getTaskMenuY(
        y: Float,
        thumbnailView: View,
        stagePosition: Int,
        taskMenuView: View,
        taskInsetMargin: Float,
        taskViewIcon: View,
    ): Float = y + taskInsetMargin

    override fun getTaskMenuWidth(
        thumbnailView: View,
        deviceProfile: DeviceProfile,
        @StagePosition stagePosition: Int,
    ): Int {
        val padding = thumbnailView.resources.getDimensionPixelSize(R.dimen.task_menu_edge_padding)
        return thumbnailView.measuredWidth - (2 * padding)
    }

    override fun getTaskMenuHeight(
        taskInsetMargin: Float,
        deviceProfile: DeviceProfile,
        taskMenuX: Float,
        taskMenuY: Float,
    ): Int =
        deviceProfile.heightPx -
            deviceProfile.insets.top -
            taskMenuY.toInt() -
            deviceProfile.overviewActionsClaimedSpaceBelow

    override fun setTaskOptionsMenuLayoutOrientation(
        deviceProfile: DeviceProfile,
        taskMenuLayout: LinearLayout,
        dividerSpacing: Int,
        dividerDrawable: ShapeDrawable,
    ) {
        taskMenuLayout.orientation = LinearLayout.VERTICAL
        dividerDrawable.intrinsicHeight = dividerSpacing
        taskMenuLayout.dividerDrawable = dividerDrawable
    }

    override fun setLayoutParamsForTaskMenuOptionItem(
        lp: LinearLayout.LayoutParams,
        viewGroup: LinearLayout,
        deviceProfile: DeviceProfile,
    ) {
        viewGroup.orientation = LinearLayout.HORIZONTAL
        lp.width = LinearLayout.LayoutParams.MATCH_PARENT
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    override fun updateDwbBannerLayout(
        taskViewWidth: Int,
        taskViewHeight: Int,
        isGroupedTaskView: Boolean,
        deviceProfile: DeviceProfile,
        snapshotViewWidth: Int,
        snapshotViewHeight: Int,
        banner: View,
    ) {
        banner.pivotX = 0f
        banner.pivotY = 0f
        banner.rotation = degreesRotated
        banner.updateLayoutParams<FrameLayout.LayoutParams> {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
    }

    override fun getDwbBannerTranslations(
        taskViewWidth: Int,
        taskViewHeight: Int,
        splitBounds: SplitConfigurationOptions.SplitBounds?,
        deviceProfile: DeviceProfile,
        thumbnailViews: Array<View>,
        desiredTaskId: Int,
        banner: View,
    ): Pair<Float, Float> = Pair(0f, 0f)

    override val upDownSwipeDirection: SingleAxisSwipeDetector.Direction =
        SingleAxisSwipeDetector.VERTICAL

    override fun getUpDirection(isRtl: Boolean): Int = SingleAxisSwipeDetector.DIRECTION_NEGATIVE

    override fun getDownDirection(isRtl: Boolean): Int = SingleAxisSwipeDetector.DIRECTION_POSITIVE

    override fun isGoingUp(displacement: Float, isRtl: Boolean): Boolean = displacement < 0

    override fun getTaskDragDisplacementFactor(isRtl: Boolean): Int = 1

    override fun getTaskDismissVerticalDirection(): Int = -1

    override fun getTaskDismissLength(secondaryDimension: Int, taskThumbnailBounds: Rect): Int =
        taskThumbnailBounds.bottom

    override fun getTaskLaunchLength(secondaryDimension: Int, taskThumbnailBounds: Rect): Int =
        secondaryDimension - taskThumbnailBounds.bottom

    override fun getDistanceToBottomOfRect(dp: DeviceProfile, rect: Rect): Int =
        dp.heightPx - rect.bottom

    override fun getSplitPositionOptions(dp: DeviceProfile): List<SplitPositionOption> =
        listOf(
            SplitPositionOption(
                R.drawable.ic_split_vertical,
                R.string.recent_task_option_split_screen,
                SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT,
                SplitConfigurationOptions.STAGE_TYPE_MAIN,
            )
        )

    override fun getInitialSplitPlaceholderBounds(
        placeholderHeight: Int,
        placeholderInset: Int,
        dp: DeviceProfile,
        @StagePosition stagePosition: Int,
        out: Rect,
    ) {
        val screenWidth = dp.widthPx
        val screenHeight = dp.heightPx
        out.set(0, 0, screenWidth, placeholderHeight)
        out.inset(placeholderInset, 0)
    }

    override fun updateSplitIconParams(
        out: View,
        onScreenRectCenterX: Float,
        onScreenRectCenterY: Float,
        fullscreenScaleX: Float,
        fullscreenScaleY: Float,
        drawableWidth: Int,
        drawableHeight: Int,
        dp: DeviceProfile,
        @StagePosition stagePosition: Int,
    ) {
        out.x = (onScreenRectCenterX / fullscreenScaleX - 1.0f * drawableWidth / 2)
        out.y = (onScreenRectCenterY / fullscreenScaleY - 1.0f * drawableHeight / 2)
    }

    override fun setSplitInstructionsParams(
        out: View,
        dp: DeviceProfile,
        splitInstructionsHeight: Int,
        splitInstructionsWidth: Int,
    ) {
        out.pivotX = 0f
        out.pivotY = splitInstructionsHeight.toFloat()
        out.rotation = degreesRotated
        val distanceToEdge = dp.overviewActionsClaimedSpaceBelow
        val insetCorrectionX = (dp.insets.right - dp.insets.left) / 2
        val insetCorrectionY = dp.insets.bottom
        out.translationX = insetCorrectionX.toFloat()
        out.translationY = (-distanceToEdge + insetCorrectionY).toFloat()
        val lp = out.layoutParams as FrameLayout.LayoutParams
        lp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        out.layoutParams = lp
    }

    override fun getFinalSplitPlaceholderBounds(
        splitDividerSize: Int,
        dp: DeviceProfile,
        @StagePosition stagePosition: Int,
        out1: Rect,
        out2: Rect,
    ) {
        val screenHeight = dp.heightPx
        val screenWidth = dp.widthPx
        out1.set(0, 0, screenWidth, screenHeight / 2 - splitDividerSize)
        out2.set(0, screenHeight / 2 + splitDividerSize, screenWidth, screenHeight)
    }

    override fun setSplitTaskSwipeRect(
        dp: DeviceProfile,
        outRect: Rect,
        splitInfo: SplitConfigurationOptions.SplitBounds,
        desiredStagePosition: Int,
    ) {
        val topLeftTaskPercent = splitInfo.leftTopTaskPercent
        val dividerBarPercent = splitInfo.dividerPercent
        val taskbarHeight = if (dp.isTransientTaskbar) 0 else dp.taskbarHeight
        val scale = outRect.height().toFloat() / (dp.availableHeightPx - taskbarHeight)
        val topTaskHeight = dp.availableHeightPx * topLeftTaskPercent
        val scaledTopTaskHeight = topTaskHeight * scale
        val dividerHeight = dp.availableHeightPx * dividerBarPercent * scale
        
        if (desiredStagePosition == SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT) {
            outRect.bottom = outRect.top + scaledTopTaskHeight.toInt()
        } else {
            outRect.top += (scaledTopTaskHeight + dividerHeight).toInt()
        }
    }

    override fun measureGroupedTaskViewThumbnailBounds(
        primarySnapshot: View,
        secondarySnapshot: View,
        parentWidth: Int,
        parentHeight: Int,
        splitBoundsConfig: SplitConfigurationOptions.SplitBounds,
        dp: DeviceProfile,
        isRtl: Boolean,
    ) {
        val spaceAboveSnapshot = dp.overviewTaskThumbnailTopMarginPx
        val totalThumbnailHeight = parentHeight - spaceAboveSnapshot
        val dividerBar = (totalThumbnailHeight * splitBoundsConfig.dividerPercent).toInt()
        val primaryHeight = (totalThumbnailHeight * splitBoundsConfig.leftTopTaskPercent).toInt()
        val secondaryHeight = totalThumbnailHeight - primaryHeight - dividerBar

        primarySnapshot.translationY = spaceAboveSnapshot.toFloat()
        secondarySnapshot.translationY = (spaceAboveSnapshot + primaryHeight + dividerBar).toFloat()

        primarySnapshot.updateLayoutParams<FrameLayout.LayoutParams> {
            width = parentWidth
            height = primaryHeight
        }
        secondarySnapshot.updateLayoutParams<FrameLayout.LayoutParams> {
            width = parentWidth
            height = secondaryHeight
        }
    }

    override fun setIconAndSnapshotParams(
        iconView: View,
        taskIconMargin: Int,
        taskIconHeight: Int,
        thumbnailTopMargin: Int,
        isRtl: Boolean,
    ) {
        val iconParams = iconView.layoutParams as FrameLayout.LayoutParams
        iconParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        iconParams.topMargin = taskIconMargin
        iconParams.marginStart = 0
        iconParams.marginEnd = 0
    }

    override fun setSplitIconParams(
        primaryIconView: View,
        secondaryIconView: View,
        taskIconHeight: Int,
        primarySnapshotWidth: Int,
        primarySnapshotHeight: Int,
        groupedTaskViewHeight: Int,
        groupedTaskViewWidth: Int,
        isRtl: Boolean,
        deviceProfile: DeviceProfile,
        splitConfig: SplitConfigurationOptions.SplitBounds,
    ) {
        val primaryParams = primaryIconView.layoutParams as FrameLayout.LayoutParams
        val secondaryParams = secondaryIconView.layoutParams as FrameLayout.LayoutParams
        
        primaryParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        secondaryParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        
        val dividerHeight = (groupedTaskViewHeight * splitConfig.dividerPercent).toInt()
        secondaryParams.topMargin = primarySnapshotHeight + dividerHeight
    }

    override fun getDefaultSplitPosition(deviceProfile: DeviceProfile): Int =
        SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT

    override fun getFloatingTaskOffscreenTranslationTarget(
        floatingTask: View,
        onScreenRect: RectF,
        stagePosition: Int,
        dp: DeviceProfile,
    ): Float = dp.heightPx - onScreenRect.top

    override fun setFloatingTaskPrimaryTranslation(
        floatingTask: View,
        translation: Float,
        dp: DeviceProfile,
    ) {
        floatingTask.translationY = translation
    }

    override fun getFloatingTaskPrimaryTranslation(floatingTask: View, dp: DeviceProfile): Float =
        floatingTask.translationY

    override fun <T> getSplitSelectTaskOffset(
        primary: FloatProperty<T>,
        secondary: FloatProperty<T>,
        deviceProfile: DeviceProfile,
    ): Pair<FloatProperty<T>, FloatProperty<T>> = Pair(primary, secondary)

    override fun getHandlerTypeForLogging(): LauncherAtom.TaskSwitcherContainer.OrientationHandler =
        LauncherAtom.TaskSwitcherContainer.OrientationHandler.PORTRAIT

    override fun getRecentsRtlSetting(resources: android.content.res.Resources): Boolean =
        Utilities.isRtl(resources)

    override fun getTaskViewIconAppChipMenuGravity(): Int = 
        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL

    override fun setIconAppChipChildrenLayoutParams(
        iconView: View,
        appNameView: View,
        deviceProfile: DeviceProfile,
    ) {
        val iconParams = iconView.layoutParams as LinearLayout.LayoutParams
        iconParams.gravity = Gravity.CENTER_VERTICAL
        val appNameParams = appNameView.layoutParams as LinearLayout.LayoutParams
        appNameParams.gravity = Gravity.CENTER_VERTICAL
    }

    override fun setIconAppChipMenuParams(
        iconAppChipView: IconAppChipView,
        snapshotView: View,
        chipOffset: Int,
        chipAnchorMarginStart: Int,
    ) {
        iconAppChipView.updateLayoutParams<FrameLayout.LayoutParams> {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = chipOffset
        }
    }

    fun computeStackTransformForIndex(
        index: Int,
        totalCount: Int,
        scrollProgress: Float,
    ): StackTransform {
        val effectiveIndex = (index - scrollProgress).coerceIn(0f, (MAX_VISIBLE_TASKS - 1).toFloat())
        
        val scale = 1f - (effectiveIndex * STACK_SCALE_FACTOR)
        val translationY = effectiveIndex * STACK_TRANSLATION_Y_FACTOR
        val rotation = effectiveIndex * STACK_ROTATION_FACTOR
        val translationZ = (MAX_VISIBLE_TASKS - effectiveIndex) * 10f
        val alpha = if (effectiveIndex >= MAX_VISIBLE_TASKS) 0f else 1f
        
        return StackTransform(
            scale = scale.coerceIn(0.6f, 1f),
            translationY = -translationY,
            translationZ = translationZ,
            rotation = rotation.coerceIn(0f, 10f),
            alpha = alpha
        )
    }

    data class StackTransform(
        val scale: Float,
        val translationY: Float,
        val translationZ: Float,
        val rotation: Float,
        val alpha: Float
    )
}
