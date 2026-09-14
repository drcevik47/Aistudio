import re
with open("app/src/main/java/com/example/BybitBotApp.kt", "r") as f:
    content = f.read()

content = content.replace(
    "import com.example.data.repository.BybitRepository",
    "import com.example.data.repository.BybitRepository\nimport com.example.data.repository.OkxRepository"
)

content = content.replace(
    "lateinit var repository: BybitRepository\n        private set",
    "lateinit var repository: BybitRepository\n        private set\n    lateinit var okxRepository: OkxRepository\n        private set"
)

content = content.replace(
    """        repository = BybitRepository(
            preferences = preferences,
            database = database,
            orderDao = database.orderDao(),
            logDao = database.logDao(),
            exchangeTradeDao = database.exchangeTradeDao()
        )""",
    """        repository = BybitRepository(
            preferences = preferences,
            database = database,
            orderDao = database.orderDao(),
            logDao = database.logDao(),
            exchangeTradeDao = database.exchangeTradeDao()
        )
        okxRepository = OkxRepository(
            preferences = preferences,
            database = database
        )"""
)

with open("app/src/main/java/com/example/BybitBotApp.kt", "w") as f:
    f.write(content)

print("Done")
