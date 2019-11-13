package com.devtau.ironHeroes.ui.activities.statistics

import com.devtau.ironHeroes.ui.StandardView
import com.github.mikephil.charting.data.LineData

interface StatisticsView: StandardView {
    fun showMuscleGroups(list: List<String>?, selectedIndex: Int)
    fun showExercises(list: List<String>?, selectedIndex: Int)
    fun showStatisticsData(lineData: LineData?)
}