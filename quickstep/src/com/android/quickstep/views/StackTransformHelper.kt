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
package com.android.quickstep.views

import android.view.View
import com.android.app.animation.Interpolators
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Helper class for computing iOS-style stack transforms for RecentsView.
 * 
 * The stack view displays tasks stacked vertically with:
 * - Front task (index 0) at full size and opacity
 * - Back tasks progressively smaller, shifted up, and slightly rotated
 * - Depth effect using translationZ for shadow/elevation
 */
object StackTransformHelper {
    
    private const val STACK_SCALE_DECAY = 0.06f
    private const val STACK_TRANSLATION_Y_PER_TASK = 80f
    private const val STACK_ROTATION_PER_TASK = 1.5f
    private const val STACK_TRANSLATION_Z_PER_TASK = 8f
    private const val MAX_VISIBLE_STACK_DEPTH = 6
    private const val MIN_SCALE = 0.65f
    private const val MAX_ROTATION = 8f
    
    private val STACK_INTERPOLATOR = Interpolators.DECELERATE_2
    
    data class StackTransform(
        val scale: Float,
        val translationY: Float,
        val translationZ: Float,
        val rotationX: Float,
        val alpha: Float,
        val visible: Boolean
    )
    
    /**
     * Computes the stack transform for a task at a given index.
     * 
     * @param index The index of the task in the stack (0 = front/focused)
     * @param scrollProgress The scroll progress for smooth transitions (0-1 per task)
     * @param totalTasks Total number of tasks in the stack
     * @return StackTransform containing all transformation values
     */
    fun computeStackTransform(
        index: Int,
        scrollProgress: Float,
        totalTasks: Int
    ): StackTransform {
        val effectiveIndex = (index - scrollProgress).coerceIn(
            -0.5f, 
            (MAX_VISIBLE_STACK_DEPTH + 0.5f).toFloat()
        )
        
        if (effectiveIndex < -0.5f || effectiveIndex > MAX_VISIBLE_STACK_DEPTH) {
            return StackTransform(
                scale = MIN_SCALE,
                translationY = 0f,
                translationZ = 0f,
                rotationX = 0f,
                alpha = 0f,
                visible = false
            )
        }
        
        val normalizedIndex = max(0f, effectiveIndex)
        val t = STACK_INTERPOLATOR.getInterpolation(
            (normalizedIndex / MAX_VISIBLE_STACK_DEPTH).coerceIn(0f, 1f)
        )
        
        val scale = (1f - (normalizedIndex * STACK_SCALE_DECAY)).coerceIn(MIN_SCALE, 1f)
        
        val translationY = -normalizedIndex * STACK_TRANSLATION_Y_PER_TASK
        
        val translationZ = (MAX_VISIBLE_STACK_DEPTH - normalizedIndex) * STACK_TRANSLATION_Z_PER_TASK
        
        val rotationX = (normalizedIndex * STACK_ROTATION_PER_TASK).coerceIn(0f, MAX_ROTATION)
        
        val alpha = when {
            effectiveIndex < 0f -> 1f + effectiveIndex * 2f
            effectiveIndex > MAX_VISIBLE_STACK_DEPTH - 1 -> 
                1f - (effectiveIndex - (MAX_VISIBLE_STACK_DEPTH - 1))
            else -> 1f
        }.coerceIn(0f, 1f)
        
        return StackTransform(
            scale = scale,
            translationY = translationY,
            translationZ = translationZ,
            rotationX = rotationX,
            alpha = alpha,
            visible = alpha > 0.01f
        )
    }
    
    /**
     * Applies the stack transform to a TaskView.
     * 
     * @param taskView The TaskView to transform
     * @param transform The computed StackTransform
     * @param animate Whether to animate the transformation
     */
    fun applyStackTransform(
        taskView: View,
        transform: StackTransform,
        animate: Boolean = false
    ) {
        if (animate) {
            taskView.animate()
                .scaleX(transform.scale)
                .scaleY(transform.scale)
                .translationY(transform.translationY)
                .translationZ(transform.translationZ)
                .rotationX(transform.rotationX)
                .alpha(transform.alpha)
                .setDuration(200)
                .setInterpolator(Interpolators.EMPHASIZED)
                .start()
        } else {
            taskView.scaleX = transform.scale
            taskView.scaleY = transform.scale
            taskView.translationY = transform.translationY
            taskView.translationZ = transform.translationZ
            taskView.rotationX = transform.rotationX
            taskView.alpha = transform.alpha
        }
        
        taskView.visibility = if (transform.visible) View.VISIBLE else View.INVISIBLE
    }
    
    /**
     * Computes scroll progress based on current scroll position.
     * 
     * @param scrollY Current vertical scroll position
     * @param taskHeight Height of each task card
     * @return Float representing progress through the stack
     */
    fun computeScrollProgress(scrollY: Int, taskHeight: Int): Float {
        if (taskHeight <= 0) return 0f
        return scrollY.toFloat() / taskHeight
    }
    
    /**
     * Applies stack transforms to all visible TaskViews in RecentsView.
     * 
     * @param recentsView The RecentsView containing TaskViews
     * @param scrollProgress Current scroll progress
     */
    fun updateAllStackTransforms(
        taskViews: List<View>,
        scrollProgress: Float
    ) {
        taskViews.forEachIndexed { index, taskView ->
            val transform = computeStackTransform(
                index = index,
                scrollProgress = scrollProgress,
                totalTasks = taskViews.size
            )
            applyStackTransform(taskView, transform)
        }
    }
    
    /**
     * Configuration for the stack view appearance.
     */
    object Config {
        var scaleDecay: Float = STACK_SCALE_DECAY
        var translationYPerTask: Float = STACK_TRANSLATION_Y_PER_TASK
        var rotationPerTask: Float = STACK_ROTATION_PER_TASK
        var translationZPerTask: Float = STACK_TRANSLATION_Z_PER_TASK
        var maxVisibleDepth: Int = MAX_VISIBLE_STACK_DEPTH
        var minScale: Float = MIN_SCALE
        var maxRotation: Float = MAX_ROTATION
        
        fun reset() {
            scaleDecay = STACK_SCALE_DECAY
            translationYPerTask = STACK_TRANSLATION_Y_PER_TASK
            rotationPerTask = STACK_ROTATION_PER_TASK
            translationZPerTask = STACK_TRANSLATION_Z_PER_TASK
            maxVisibleDepth = MAX_VISIBLE_STACK_DEPTH
            minScale = MIN_SCALE
            maxRotation = MAX_ROTATION
        }
    }
}
