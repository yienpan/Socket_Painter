# Socket_Painter

本專案為使用 Android Studio 開發的專題練習作品，實作一個支援多人即時共同編輯的行動端小畫家。核心目標在於練習 Android 網路通訊（Socket）與自訂繪圖元件（Custom View）的整合應用。

## 專題練習重點與實作架構

### 1. 網路通訊架構（Client-Server 模式）
* **連線建立**：透過 Java 原生 `ServerSocket` 與 `Socket` 建立 TCP 連線。其中一台裝置作為主機（Server）監聽特定 Port，另一台裝置作為客戶端（Client）透過區域網路 IP 位址發起連線。
* **非同步執行緒處理**：由於 Android 限制網路操作不能在主執行緒（UI Thread）執行，專案中透過背景執行緒（`Thread`）處理連線建立、資料監聽與發送，並搭配 `Handler` 或 `runOnUiThread` 將接收到的繪圖指令更新至畫面上。

### 2. 畫布繪製與同步邏輯
* **自訂畫布（Custom View）**：繼承 `View` 並覆寫 `onDraw()` 與 `onTouchEvent()` 方法，利用 `Canvas`、`Paint` 與 `Path` 記錄使用者的觸控軌跡（`ACTION_DOWN`、`ACTION_MOVE`、`ACTION_UP`）。
* **指令序列化與傳輸**：將本地端的繪圖動作（如起點與終點座標、畫筆顏色、線條粗細、清除畫布等狀態）封裝成特定格式的字串或資料封包，透過輸出串流（`OutputStream` / `PrintWriter`）傳送給連線對象。
* **遠端重繪**：接收端透過輸入串流（`InputStream` / `BufferedReader`）持續讀取封包，解析出對應的座標與筆刷參數後，即時在本地端的 `Canvas` 上重繪相同路徑。

## 具備功能
* **雙端連線**：支援建立主機等待連線，以及輸入 IP 位址加入現有畫布。
* **即時筆跡同步**：雙方繪製的線條可即時顯示於彼此的螢幕上。
* **基礎繪圖工具**：支援切換畫筆顏色、調整筆刷粗細、橡皮擦以及一鍵清除畫布。

## 開發環境與技術
* **開發工具**：Android Studio
* **程式語言**：Kotlin / Java（請依實際專案保留）
* **核心技術**：TCP Socket、Multi-threading、Android Custom View (`Canvas` / `Paint`)

## 執行與測試方式
   ```bash
   git clone [https://github.com/yienpan/Socket_Painter.git](https://github.com/yienpan/Socket_Painter.git)
