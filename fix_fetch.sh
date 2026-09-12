#!/bin/bash
awk '
/fun fetchChartData/ {
    print "    fun fetchChartData(symbol: String, interval: String) {"
    print "        viewModelScope.launch {"
    print "            _uiState.update { it.copy(chartInterval = interval, chartKlines = emptyList()) } // Clear while loading new interval"
    print "            val allKlines = mutableListOf<com.example.ui.components.KlineData>()"
    print "            var currentEnd: Long? = null"
    print "            var keepFetching = true"
    print "            var pages = 0"
    print "            val maxPages = 5 // Total 5000 candles"
    print "            "
    print "            while (keepFetching && pages < maxPages) {"
    print "                val result = repository.getKlines(symbol = symbol, interval = interval, end = currentEnd, limit = 1000)"
    print "                result.onSuccess { klineResult ->"
    print "                    val klines = klineResult.list.mapNotNull { entry ->"
    print "                        try {"
    print "                            com.example.ui.components.KlineData("
    print "                                timeMillis = entry[0].toLong(),"
    print "                                open = entry[1].toDouble(),"
    print "                                high = entry[2].toDouble(),"
    print "                                low = entry[3].toDouble(),"
    print "                                close = entry[4].toDouble(),"
    print "                                volume = entry[5].toDouble()"
    print "                            )"
    print "                        } catch (e: Exception) {"
    print "                            null"
    print "                        }"
    print "                    }"
    print "                    if (klines.isEmpty()) {"
    print "                        keepFetching = false"
    print "                    } else {"
    print "                        allKlines.addAll(klines)"
    print "                        currentEnd = klines.minOf { it.timeMillis } - 1"
    print "                        pages++"
    print "                        // Update UI progressively so user sees data loading"
    print "                        _uiState.update { it.copy(chartKlines = allKlines.distinctBy { k -> k.timeMillis }.toList()) }"
    print "                    }"
    print "                }.onFailure {"
    print "                    keepFetching = false"
    print "                }"
    print "            }"
    print "        }"
    print "    }"
    in_func = 1
    next
}
in_func && /^    }/ {
    in_func = 0
    next
}
in_func { next }
{ print }
' app/src/main/java/com/example/ui/MainViewModel.kt > tmp.kt && mv tmp.kt app/src/main/java/com/example/ui/MainViewModel.kt
