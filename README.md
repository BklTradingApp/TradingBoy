# TradingBoy

**TradingBoy** is an automated trading bot designed to execute real-time trades based on custom-defined strategies using Alpaca’s WebSocket API.

## Features

- **Real-time Trade Execution:** Executes trades with low latency to capitalize on market opportunities.
- **Customizable Trading Strategies:** Allows users to define and implement their own trading strategies.
- **Comprehensive Logging and Error Handling:** Ensures robust performance and easy troubleshooting.
- **Real-time Notifications via Telegram:** Sends instant alerts and updates to keep users informed.
- **SQLite Database:** Stores historical trading data and performance metrics for data-driven decision-making and analysis.

## Technologies Used

- **Java**
- **Alpaca API**
- **WebSocket**
- **SQLite**
- **Telegram API**
- **Maven**

## Installation

Follow these steps to set up and run the TradingBoy application on your local machine.

### 1. Clone the Repository

```bash
git clone https://github.com/BklTradingApp/TradingBoy.git
```

### 2. Navigate to the Project Directory

```bash
cd TradingBoy
```

### 3. Install Dependencies

Ensure you have Maven installed. Then, install the project dependencies by running:

```bash
mvn install
```

### 4. Configure Application Properties

Edit the `application.properties` file in the `src/main/resources` directory and add the necessary configuration parameters. This file should include your Alpaca API keys, Telegram bot token, and other settings.

**Example application.properties File:**

```properties
alpaca.api.key=your_alpaca_api_key
alpaca.api.secret=your_alpaca_secret_key
telegram.bot.token=your_telegram_bot_token
telegram.chat.id=your_telegram_chat_id
```

### 5. Run the Application

Start the TradingBoy application using Maven:

```bash
mvn spring-boot:run
```

Alternatively, you can right-click on the `Main.java` file in your IDE and select `Run Main.main()` to start the application.

## Usage

### Configure Trading Strategies:

Edit the configuration file (e.g., `application.properties`) to define your trading strategies. Specify parameters such as RSI thresholds, trading pairs, and other strategy-specific settings.

### Monitor Trades and Notifications:

- Once the application is running, it will begin executing trades based on your defined strategies.
- Receive real-time notifications and alerts via Telegram to stay updated on trade executions, system errors, and other important events.

## Project Structure

```bash
TradingBoy/
├── src/
│   ├── main/
│   │   ├── java/
│   │   └── resources/
│   └── test/
├── pom.xml
└── README.md
```
