# Android Launcher3 - iOS Stack View Modification

## Project Overview
This is the Android AOSP Launcher3 project with modifications to implement an iOS-style stack view for the Recent Apps screen.

**Important**: This is an Android native project that requires the Android Open Source Project (AOSP) build system (Soong/Blueprint) to compile. It cannot be built or run directly in Replit.

## iOS Stack View Implementation

### Files Created/Modified

#### New Files:
1. **`quickstep/src/com/android/quickstep/orientation/StackPagedViewHandler.kt`**
   - Vertical orientation handler for stack-style scrolling
   - Swaps X/Y axis for vertical stack navigation
   - Contains `computeStackTransformForIndex()` for calculating stack transforms

2. **`quickstep/src/com/android/quickstep/views/StackTransformHelper.kt`**
   - Helper object for computing iOS-style stack transforms
   - Handles scale, translationY, translationZ, rotationX, and alpha
   - Configurable parameters for stack appearance

### Integration Instructions

To integrate the iOS stack view into RecentsView.java, you need to:

1. **Add Stack Mode Flag** (in RecentsView.java around line 600):
```java
private boolean mStackModeEnabled = true; // Enable iOS stack view
private float mStackScrollProgress = 0f;
```

2. **Modify updateCurveProperties()** (around line 2535):
```java
public void updateCurveProperties() {
    if (getPageCount() == 0 || getPageAt(0).getMeasuredWidth() == 0) {
        return;
    }
    
    if (mStackModeEnabled) {
        updateStackTransforms();
        return;
    }
    
    // Original carousel code...
    int scroll = getPagedOrientationHandler().getPrimaryScroll(this);
    mClearAllButton.onRecentsViewScroll(scroll, mOverviewGridEnabled);
    mActionsView.getIndexScrollAlpha().updateValue(1 - mClearAllButton.getScrollAlpha());
}

private void updateStackTransforms() {
    int taskCount = getTaskViewCount();
    float scrollProgress = StackTransformHelper.INSTANCE.computeScrollProgress(
        getScrollY(), 
        getHeight() / 2
    );
    
    for (int i = 0; i < taskCount; i++) {
        TaskView taskView = getTaskViewAt(i);
        if (taskView != null) {
            StackTransformHelper.StackTransform transform = 
                StackTransformHelper.INSTANCE.computeStackTransform(i, scrollProgress, taskCount);
            StackTransformHelper.INSTANCE.applyStackTransform(taskView, transform, false);
        }
    }
}
```

3. **Update onScrollChanged()** to use vertical scrolling when in stack mode

### Stack Transform Parameters

The stack view appearance can be customized in `StackTransformHelper.kt`:

| Parameter | Default | Description |
|-----------|---------|-------------|
| STACK_SCALE_DECAY | 0.06f | Scale reduction per stack level |
| STACK_TRANSLATION_Y_PER_TASK | 80f | Vertical offset per task |
| STACK_ROTATION_PER_TASK | 1.5f | X-axis rotation per task |
| STACK_TRANSLATION_Z_PER_TASK | 8f | Depth/elevation per task |
| MAX_VISIBLE_STACK_DEPTH | 6 | Maximum visible tasks |
| MIN_SCALE | 0.65f | Minimum scale for back tasks |

## Building the Project

This project must be built as part of AOSP:

```bash
# In AOSP root directory
source build/envsetup.sh
lunch <target>
make Launcher3
```

Or for the QuickStep variant:
```bash
make Launcher3QuickStep
```

## Recent Changes
- Added StackPagedViewHandler.kt for vertical stack orientation
- Added StackTransformHelper.kt for iOS-style depth transforms
- Documentation for RecentsView.java integration

## Architecture Notes

The RecentsView uses a PagedView base class for horizontal scrolling. The stack view modification:
1. Changes primary scroll axis from horizontal to vertical
2. Applies scale, translation, and rotation transforms based on stack position
3. Uses translationZ for iOS-like depth/shadow effects
4. Maintains compatibility with existing gesture handling
