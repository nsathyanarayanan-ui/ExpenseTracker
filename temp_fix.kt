private fun renderPieChart(cats: List<com.expensetracker.db.CategoryTotal>) {
    if (cats.isEmpty()) {
        binding.pieChart.clear()
        binding.pieChart.invalidate()
        return
    }
