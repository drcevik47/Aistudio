with open("app/src/main/java/com/example/ui/MainViewModel.kt", "r") as f:
    lines = f.readlines()

new_lines = []
skip = False
for i, line in enumerate(lines):
    if "var okxCurrentPrice = _uiState.value.okxCurrentPrice" in line:
        pass
    if "okxPortfolioAnalysis = okxAnalysis" in line:
        pass
    
    # Actually it's easier to just do regex.
