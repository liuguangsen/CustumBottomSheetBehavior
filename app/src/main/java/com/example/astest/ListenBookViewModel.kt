package com.example.astest

import androidx.lifecycle.ViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior

class ListenBookViewModel : ViewModel() {
    var behaviorState: Int = BottomSheetBehavior.STATE_HALF_EXPANDED
    var isUserScrollScrollView: Boolean = false
    var adapterBottomSheetHeight: Int = 600
    var showAppBarTitle: Boolean = false
}