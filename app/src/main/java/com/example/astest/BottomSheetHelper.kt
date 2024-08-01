package com.example.astest

import android.annotation.SuppressLint
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.doOnNextLayout
import androidx.core.widget.NestedScrollView

/**
 * 听书页面处理专辑顶部以及内容联动的五种效果
 * 参考 qq音乐 网易云音乐 界面效果, 实现联动效果
 * 1.scrollView 滑动到专辑内容区域bottom，如果继续上滑，需要bottomLayout跟随上滑（控制往上最小到0）
 * 2.scrollView 滑动到专辑内容区域bottom，如果继续下滑，需要bottomLayout继续下滑（控制往下最大道折叠状态高度）
 * 3.bottomLayout 往上滑动，抬起Behavior结束，需要检测ScrollView是否需要滑动，如需要需要滑动
 * 4.bottomLayout 往下滑动，需要检测ScrollView是否需要滑动，如需要需要滑动
 * 5.bottomLayout 往下滑动，抬起Behavior结束，需要检测ScrollView是否需要滑动，如需要需要滑动
 *
 * @author 刘广森
 * @since 2024/08/01
 */
class BottomSheetHelper(
    var scrollView: NestedScrollView,
    var bottomLayout: LinearLayout,
    var contentView: TextView,
    var standardView: View,
    var bottomSheetBehavior: BottomSheetBehavior<View>,
    var viewModel: ListenBookViewModel
) {
    companion object {
        /**
         * 默认的方向
         */
        val SCROLL_ORIENTATION_DEFAULT = 0

        /**
         * 向上滑动
         */
        val SCROLL_ORIENTATION_UP = 1

        /**
         * 向下滑动
         */
        val SCROLL_ORIENTATION_DOWN = 2

        /**
         * scrollview往上滑动后scrollY 大于等于 显示title
         */
        val STANDARD_SHOW_TITLE_HEIGHT = 64
    }

    /**
     * BottomSheetBehavior 滑动监听器
     */
    var behaviorCallback: BottomSheetBehavior.BottomSheetCallback? = null

    /**
     * 滑动监听
     */
    var onScrollChangeListener: View.OnScrollChangeListener? = null

    /**
     * 外部title显示控制回调
     */
    var showTitleCallback: ShowTitleCallback? = null

    /**
     * 触摸监听
     */
    var onTouchListener: View.OnTouchListener? = null

    /**
     * 内容和scrollView顶部 差了 50dp(标题栏) + 44dp(状态栏)
     */
    var topConstantDistance = 94 * 4

    /**
     * 半屏需求的高度比例
     */
    var halfExpandedRatioFloat = 0.6f

    /**
     * 专辑文案区域的显示高度，存在比 Behavior half状态的高度， 高 或者 底
     * 如果：高的话，需要填充部分高度，不然计算会有问题，这部分高度，补充到占位View 的高度里面去 值为 topConstantDistance + expandStateHeight - halfStateHeight
     * 如果：低的话，就为channel_list_detail_collapsing_toolbarLayout 的 bottom
     */
    var topContentDistance = 0

    /**
     * 适配不同设备的高度 先以手机开发
     *
     * 分别的一应Behavior 折叠 半屏 全屏的 高度值
     */
    var collapsedStateHeight = 0
    var halfStateHeight = 0
    var expandStateHeight = 0

    /**
     * scrollView 的滑动方向
     */
    var scrollViewOrientation = 0

    /**
     * 滑动BottomSheetBehavior时候的回弹，需要检测标准，控制scrollView跟着回弹
     */
    var standardViewColScrollY =
        0 // topContentDistance - (expandStateHeight - collapsedStateHeight) - topConstantDistance
    var standardViewHalfScrollY =
        0 // topContentDistance - (expandStateHeight - halfStateHeight) - topConstantDistance

    /**
     * bottomLayout 的滑动方向
     */
    var bottomLayoutOrientation = 0

    /**
     * 记录bottomLayout滑动后的top值
     */
    var oldBottomLayoutTop = SCROLL_ORIENTATION_DEFAULT

    /**
     * 记录Behavior的旧状态
     */
    var oldBehaviorState = -1

    /**
     * 记录用户是否滑动过一次ScrollView
     */
    var hasUserScrollScrollView = false

    fun initViewListener(showTitleCallback: ShowTitleCallback? = null) {
        this.showTitleCallback = showTitleCallback
        initScrollView()
        initBottomLayout()
    }

    /**
     * 初始化ScrollView滑动处理逻辑
     * 实现效果1 效果2
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun initScrollView() {
        onTouchListener = object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        hasUserScrollScrollView = true
                        viewModel.isUserScrollScrollView = true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        viewModel.isUserScrollScrollView = false
                    }
                }
                return false
            }
        }
        scrollView.setOnTouchListener(onTouchListener)
        // 效果1: 滑动scrollView , Behavior跟随效果
        onScrollChangeListener = @RequiresApi(Build.VERSION_CODES.M)
        object : View.OnScrollChangeListener {
            override fun onScrollChange(
                v: View?,
                scrollX: Int,
                scrollY: Int,
                oldScrollX: Int,
                oldScrollY: Int
            ) {
                // 拦截掉行为的拖动和松手后修正动作状态，另外用户没有滑动话的状态也拦截掉
                if (oldBehaviorState == BottomSheetBehavior.STATE_DRAGGING
                    || oldBehaviorState == BottomSheetBehavior.STATE_SETTLING
                    || !hasUserScrollScrollView
                ) return

                // title显示的临界值
                val show = scrollY > STANDARD_SHOW_TITLE_HEIGHT

                // 计算滑动方向
                if (scrollY < oldScrollY) {
                    scrollViewOrientation = SCROLL_ORIENTATION_DOWN
                } else if (scrollY > oldScrollY) {
                    scrollViewOrientation = SCROLL_ORIENTATION_UP
                } else {
                    scrollViewOrientation = SCROLL_ORIENTATION_DEFAULT
                }

                // 滑动距离
                var scrollDistanceY = oldScrollY - scrollY

                // 检测bottomLayout的位置，计算是否需要让bottomLayout跟随滑动
                val cuShouldScrollY = topContentDistance - bottomLayout.top - topConstantDistance
                if (scrollViewOrientation == SCROLL_ORIENTATION_UP) {
                    // 向上滑动，滑过了 标准view
                    if (cuShouldScrollY <= scrollY) {
                        // 往上上最多滑动到0
                        if (bottomLayout.top + scrollDistanceY < 0) {
                            scrollDistanceY = -bottomLayout.top
                        }
                        bottomSheetBehavior.state =
                            BottomSheetBehavior.STATE_DRAGGING_OTHER_VIEW_OFFSET
                        viewModel.behaviorState =
                            BottomSheetBehavior.STATE_DRAGGING_OTHER_VIEW_OFFSET
                        // 偏移量移动
                        // bottomSheetBehavior.otherViewDragHeight = bottomLayout.top + scrollDistanceY
                        // ViewCompat.offsetTopAndBottom(bottomLayout, scrollDistanceY)

                        // 根据scrollY值 修正行为的top进行移动
                        var cuBehaviorTop = topContentDistance - scrollY - topConstantDistance
                        bottomSheetBehavior.otherViewDragHeight = cuBehaviorTop
                        ViewCompat.offsetTopAndBottom(
                            bottomLayout,
                            cuBehaviorTop - bottomLayout.top
                        )
                    } else {
                        val standTop = standardView.top - scrollY - topConstantDistance
                        if (standTop > expandStateHeight - collapsedStateHeight) {
                            // 检测是否能回归到行为COLLAPSED状态的高度
                            var cuBehaviorTop = expandStateHeight - collapsedStateHeight
                            bottomSheetBehavior.otherViewDragHeight = cuBehaviorTop
                            ViewCompat.offsetTopAndBottom(
                                bottomLayout,
                                cuBehaviorTop - bottomLayout.top
                            )
                        } else {
                            // 根据scrollY值 修正行为的top进行移动
                            var cuBehaviorTop = topContentDistance - scrollY - topConstantDistance
                            bottomSheetBehavior.otherViewDragHeight = cuBehaviorTop
                            ViewCompat.offsetTopAndBottom(
                                bottomLayout,
                                cuBehaviorTop - bottomLayout.top
                            )
                        }
                    }
                } else if (scrollViewOrientation == SCROLL_ORIENTATION_DOWN) {
                    if (cuShouldScrollY >= scrollY) {
                        // 往下最多滑动要压缩状态的高度
                        if (bottomLayout.top + scrollDistanceY > expandStateHeight - collapsedStateHeight) {
                            scrollDistanceY =
                                expandStateHeight - collapsedStateHeight - bottomLayout.top
                        }
                        bottomSheetBehavior.state =
                            BottomSheetBehavior.STATE_DRAGGING_OTHER_VIEW_OFFSET
                        viewModel.behaviorState =
                            BottomSheetBehavior.STATE_DRAGGING_OTHER_VIEW_OFFSET
                        bottomSheetBehavior.otherViewDragHeight = bottomLayout.top + scrollDistanceY
                        ViewCompat.offsetTopAndBottom(bottomLayout, scrollDistanceY)
                    }
                }

                // 标题不显示就去显示title
                if (viewModel.showAppBarTitle != show) {
                    showTitleCallback?.showTitle(show, true)
                }
            }

        }
        scrollView.setOnScrollChangeListener(onScrollChangeListener)
    }

    /**
     * 初始化BottomSheetBehavior滑动处理逻辑
     * 实现效果3 效果4 效果5
     */
    private fun initBottomLayout() {
        bottomLayout.doOnNextLayout {
            // 不同屏幕的三种状态的高度不一样
            expandStateHeight = it.measuredHeight

            // 用于滑动scrillView的时候，最多往上滑动一个expandStateHeight高度
            halfStateHeight = (expandStateHeight * halfExpandedRatioFloat).toInt()
            collapsedStateHeight = viewModel.adapterBottomSheetHeight

            // 根据半屏显示，专辑区域内容是否够高来设置内容区域topContentDistance，给后续滑动使用
            if (contentView.bottom < expandStateHeight - halfStateHeight) {
                topContentDistance = expandStateHeight - halfStateHeight
            } else {
                topContentDistance = contentView.bottom
            }

            // 初始化 不同的状态高度对应的standardView的scrollY
            standardViewColScrollY =
                topContentDistance - (expandStateHeight - collapsedStateHeight) - topConstantDistance
            standardViewHalfScrollY =
                topContentDistance - (expandStateHeight - halfStateHeight) - topConstantDistance

            // 根据计算初始化行为
            initBottomSheetBehavior()
        }
    }

    private fun initBottomSheetBehavior() {
        val dp = viewModel.adapterBottomSheetHeight
        bottomSheetBehavior.apply {
            halfExpandedRatio = halfExpandedRatioFloat
            isFitToContents = false
            peekHeight = dp
            isHideable = false
            // 默认行为
            if (viewModel.behaviorState in arrayOf(
                    BottomSheetBehavior.STATE_COLLAPSED,
                    BottomSheetBehavior.STATE_HALF_EXPANDED,
                    BottomSheetBehavior.STATE_EXPANDED
                )
            ) {
                setState(viewModel.behaviorState)
            } else if (viewModel.behaviorState == BottomSheetBehavior.STATE_DRAGGING_OTHER_VIEW_OFFSET) {
                // 这种不做处理, 已在BottomSheetBehavior增加STATE_DRAGGING_TOHER_VIEW_OFFSET对应高度处理逻辑
            } else if (scrollView.scrollY == 0 && viewModel.behaviorState != BottomSheetBehavior.STATE_DRAGGING_OTHER_VIEW_OFFSET) {
                setState(BottomSheetBehavior.STATE_HALF_EXPANDED)
            } else {
                // 默认根据ScrollView的滑动y 计算行为的滚动
                val cuShouldTop = topContentDistance - scrollView.scrollY - topConstantDistance
                // 向上滑动，滑过了 标准view
                if (state == BottomSheetBehavior.STATE_DRAGGING_OTHER_VIEW_OFFSET) {
                    if (cuShouldTop != bottomLayout.top) {
                        var scrollDistanceY = cuShouldTop - bottomLayout.top
                        bottomSheetBehavior.state =
                            BottomSheetBehavior.STATE_DRAGGING_OTHER_VIEW_OFFSET
                        viewModel.behaviorState =
                            BottomSheetBehavior.STATE_DRAGGING_OTHER_VIEW_OFFSET
                        bottomSheetBehavior.otherViewDragHeight = cuShouldTop
                    }
                }
            }
            behaviorCallback = object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    viewModel.behaviorState = newState
                    when (newState) {
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            showTitleCallback?.showTitle(true, false)
                        }

                        BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                            showTitleCallback?.showTitle(viewModel.showAppBarTitle, false)
                            // 向下回弹
                            if (scrollView.scrollY >= standardViewHalfScrollY) {
                                // 需要scrollView跟随Behavior 一起回弹到正确的位置
                                scrollView.scrollTo(0, standardViewHalfScrollY)
                            }
                        }

                        BottomSheetBehavior.STATE_COLLAPSED -> {
                            showTitleCallback?.showTitle(viewModel.showAppBarTitle, false)
                            if (scrollView.scrollY > standardViewColScrollY) {
                                // 需要scrollView跟随Behavior 一起回弹到正确的位置
                                scrollView.scrollTo(0, standardViewColScrollY)
                            }
                        }

                        BottomSheetBehavior.STATE_DRAGGING -> {
                            // 拖动状态 忽略
                        }

                        else -> {
                            showTitleCallback?.showTitle(viewModel.showAppBarTitle, false)
                        }
                    }
                    // 先处理完状态再记录
                    oldBehaviorState = newState
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    val isDraggingState = oldBehaviorState == BottomSheetBehavior.STATE_DRAGGING
                    val cuTop = bottomSheet.top
                    if (oldBottomLayoutTop == SCROLL_ORIENTATION_DEFAULT) {
                        oldBottomLayoutTop = cuTop
                    }
                    var bottomDistance = cuTop - oldBottomLayoutTop
                    if (cuTop > oldBottomLayoutTop) {
                        // 向下滑动
                        bottomLayoutOrientation = SCROLL_ORIENTATION_DOWN
                    } else if (cuTop < oldBottomLayoutTop) {
                        // 向上
                        bottomLayoutOrientation = SCROLL_ORIENTATION_UP
                    } else {
                        bottomLayoutOrientation = SCROLL_ORIENTATION_DEFAULT
                    }
                    oldBottomLayoutTop = cuTop
                    val cuShouldScrollY = topContentDistance - bottomSheet.top - topConstantDistance
                    if (bottomLayoutOrientation == SCROLL_ORIENTATION_DOWN) {
                        if (cuShouldScrollY <= scrollView.scrollY && isDraggingState) {
                            scrollView.scrollTo(0, scrollView.scrollY - bottomDistance)
                        }
                    }
                }
            }
            this.addBottomSheetCallback(behaviorCallback as BottomSheetBehavior.BottomSheetCallback)
        }
    }


    fun releaseView() {
        behaviorCallback?.let {
            bottomSheetBehavior.removeBottomSheetCallback(it)
        }
        behaviorCallback = null
        scrollView.setOnTouchListener(onTouchListener)
        scrollView.setOnScrollChangeListener(onScrollChangeListener)
        onTouchListener = null
        onScrollChangeListener = null
        showTitleCallback = null
    }
}

interface ShowTitleCallback {
    fun showTitle(show: Boolean = false, isFromScrollView: Boolean = false)
}