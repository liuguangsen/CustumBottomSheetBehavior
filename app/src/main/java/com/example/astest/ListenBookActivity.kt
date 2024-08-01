package com.example.astest

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.astest.viewpager.ViewPagerAdapter

class ListenBookActivity : AppCompatActivity() {
    var recyclerView: RecyclerView? = null
    var scrollView: NestedScrollView? = null
    var scrollViewContent: TextView? = null
    var bottomLayout: LinearLayout? = null
    var standardView: ImageView? = null
    private val viewModel: ListenBookViewModel by viewModels()
    var viewPager2: ViewPager2? = null

    var mBottomSheetBehavior: BottomSheetBehavior<View>? = null
    var bottomSheetHelper: BottomSheetHelper? = null

    @SuppressLint("ClickableViewAccessibility", "MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_listen_book)
        scrollView = findViewById(R.id.scrollView)
        scrollViewContent = findViewById(R.id.scroll_view_content)
        bottomLayout = findViewById(R.id.bottom_layout)
        standardView = findViewById(R.id.standard_view)
        viewPager2 = findViewById(R.id.viewPager)
        viewPager2!!.adapter = ViewPagerAdapter(this)

        initBottomSheetBehavior()
    }

    override fun onDestroy() {
        bottomSheetHelper?.releaseView()
        bottomSheetHelper = null
        super.onDestroy()
    }

    private fun initBottomSheetBehavior() {
        if (bottomSheetHelper == null) {
            mBottomSheetBehavior = BottomSheetBehavior.from(bottomLayout!!)
            bottomSheetHelper = BottomSheetHelper(
                scrollView!!,
                bottomLayout!!,
                scrollViewContent!!,
                standardView!!,
                mBottomSheetBehavior!!,
                viewModel
            )
            bottomSheetHelper!!.initViewListener(object : ShowTitleCallback {
                override fun showTitle(show: Boolean, isFromScrollView: Boolean) {
                }
            })
        }
    }
}