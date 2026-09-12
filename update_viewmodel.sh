#!/bin/bash
cat << 'INNER' >> app/src/main/java/com/example/ui/MainViewModel.kt

    fun fetchChartData(symbol: String, interval: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(chartInterval = interval) }
            val result = repository.getKlines(symbol = symbol, interval = interval, limit = 200)
            result.onSuccess { klineResult ->
                val klines = klineResult.list.mapNotNull { entry ->
                    try {
                        com.example.ui.components.KlineData(
                            timeMillis = entry[0].toLong(),
                            open = entry[1].toDouble(),
                            high = entry[2].toDouble(),
                            low = entry[3].toDouble(),
                            close = entry[4].toDouble(),
                            volume = entry[5].toDouble()
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                _uiState.update { it.copy(chartKlines = klines) }
            }
        }
    }
INNER
sed -i 's/^}$//' app/src/main/java/com/example/ui/MainViewModel.kt
echo "}" >> app/src/main/java/com/example/ui/MainViewModel.kt
