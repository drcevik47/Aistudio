sed -i 's/drawText(priceLabelLayout, Offset(chartWidth + 12f, touch.y - priceLabelLayout.size.height\/2f))/drawText(textLayoutResult = priceLabelLayout, topLeft = Offset(chartWidth + 12f, touch.y - priceLabelLayout.size.height\/2f))/' app/src/main/java/com/example/ui/components/AdvancedCandlestickChart.kt

sed -i 's/drawText(dateLabelLayout, Offset(cx - dateLabelLayout.size.width\/2f, chartHeight + 8f))/drawText(textLayoutResult = dateLabelLayout, topLeft = Offset(cx - dateLabelLayout.size.width\/2f, chartHeight + 8f))/' app/src/main/java/com/example/ui/components/AdvancedCandlestickChart.kt

sed -i 's/drawText(ohlcLayout, Offset(12f, 8f))/drawText(textLayoutResult = ohlcLayout, topLeft = Offset(12f, 8f))/' app/src/main/java/com/example/ui/components/AdvancedCandlestickChart.kt
