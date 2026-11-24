# Online Paint (多人線上畫板) - 架構與 Socket 通訊協 a 規劃

本文件為課程加分題「Online Paint（多人線上畫板）」的完整技術架構建議、JSON 通訊協定設計以及生命週期與異常處理重點。

---

## 一、 建議的類別與檔案結構

專案採用 Java 編寫，最低支援 SDK 25，使用原生 `ServerSocket` / `Socket` 與 `org.json.JSONObject`。

```text
com.example.socket_painter/
│
├── model/
│   ├── Stroke.java          // 筆跡資料模型 (strokeId, userId, userName, color, List<PointF>, timestamp)
│   └── User.java            // 使用者資料模型 (userId, userName, color, socket/writer 參考)
│
├── network/
│   ├── PaintServer.java     // 伺服器主控端：管理 ServerSocket、連線執行緒池、線上清單、筆跡狀態、廣播機制
│   ├── ClientHandler.java   // 伺服器背景執行緒：負責處理單一 Client 的讀取 (BufferedReader) 與寫入 (PrintWriter)
│   └── PaintClient.java     // 用戶端網路模組：連線至 Server、背景接收訊息迴圈、發送請求、提供事件回呼 (Callback)
│
└── ui/
    ├── MainActivity.java    // 首頁：模式選擇（主持 Server / 加入房間）、輸入 IP 與暱稱
    ├── CanvasActivity.java  // 畫板主畫面：整合自訂畫布、工具列、線上名單、撤銷/清除按鈕
    └── PaintView.java       // 自訂 View (extends View)：處理觸控事件 (ACTION_DOWN, MOVE, UP) 與繪製筆跡
```

---

## 二、 完整 JSON 通訊協定設計

所有網路訊息均透過 Socket 的 `PrintWriter.println(jsonString)` 發送，並以 `BufferedReader.readLine()` 接收（以換行符號 `\n` 分隔）。每個訊息皆包含 `"type"` 欄位。

### 1. 訊息類型一覽表

| `type` 欄位 | 走向 | 說明 |
| :--- | :--- | :--- |
| `JOIN_REQUEST` | Client ➔ Server | 用戶端請求加入房間（攜帶暱稱） |
| `JOIN_RESPONSE` | Server ➔ Client | 伺服器回應加入結果（指派顏色、userId、目前所有筆跡與線上名單） |
| `SYNC_STATE` | Server ➔ All | 廣播更新目前線上使用者清單與筆跡 |
| `STROKE_START` | Client ➔ Server ➔ All | 使用者開始畫線（新筆跡建立，包含起始座標） |
| `STROKE_UPDATE` | Client ➔ Server ➔ All | 使用者持續拖曳畫線（新增座標點） |
| `STROKE_FINISH` | Client ➔ Server ➔ All | 使用者放開手指（完成該筆繪製） |
| `STROKE_DATA` | Client ➔ Server ➔ All | *(替代方案)* 單筆完整畫線傳輸（手指放開後傳送完整點序列） |
| `STROKE_DELETE` | Client ➔ Server ➔ All | 刪除指定 `strokeId` 的筆跡 |
| `UNDO_LAST` | Client ➔ Server ➔ All | 撤銷最後一筆（可指定特定 user 或全域） |
| `CLEAR_USER` | Client ➔ Server ➔ All | 清除特定使用者的所有筆跡 |
| `CLEAR_ALL` | Client ➔ Server ➔ All | 清除畫布全部筆跡 |
| `KICK_USER` | Server ➔ Client | 伺服器將特定使用者踢出房間 |
| `LEAVE` | Client ➔ Server | 用戶端主動離開房間 |
| `SERVER_SHUTDOWN` | Server ➔ All | 伺服器關閉通知 |

---

### 2. 詳細 JSON 範例

#### (1) `JOIN_REQUEST` (Client ➔ Server)
```json
{
  "type": "JOIN_REQUEST",
  "userName": "小明"
}
```

#### (2) `JOIN_RESPONSE` (Server ➔ Client)
```json
{
  "type": "JOIN_RESPONSE",
  "status": "SUCCESS",
  "userId": "u_1001",
  "assignedColor": "#FF5722",
  "users": [
    {"userId": "u_1000", "userName": "房主", "color": "#3F51B5"},
    {"userId": "u_1001", "userName": "小明", "color": "#FF5722"}
  ],
  "strokes": [
    {
      "strokeId": "s_1",
      "userId": "u_1000",
      "color": "#3F51B5",
      "points": [{"x": 100.0, "y": 200.0}, {"x": 105.0, "y": 205.0}]
    }
  ]
}
```

#### (3) `STROKE_START` / `STROKE_UPDATE` / `STROKE_FINISH` 或 `STROKE_DATA`
為了流暢度，可於 `ACTION_DOWN` 發送 `STROKE_START`，`ACTION_MOVE` 發送 `STROKE_UPDATE`，`ACTION_UP` 發送 `STROKE_FINISH`。或者簡化為在手指放開（`ACTION_UP`）時打包整條線發送 `STROKE_DATA`：

```json
{
  "type": "STROKE_DATA",
  "strokeId": "s_102",
  "userId": "u_1001",
  "color": "#FF5722",
  "points": [
    {"x": 50.0, "y": 100.0},
    {"x": 52.0, "y": 103.0},
    {"x": 60.0, "y": 115.0}
  ]
}
```

#### (4) `UNDO_LAST` (Client ➔ Server ➔ All)
```json
{
  "type": "UNDO_LAST",
  "userId": "u_1001"
}
```

#### (5) `CLEAR_ALL` (Client ➔ Server ➔ All)
```json
{
  "type": "CLEAR_ALL"
}
```

---

## 三、 連線與斷線生命週期處理重點

1. **避免主執行緒網路例外 (`NetworkOnMainThreadException`)**
   - 所有 Socket 建立、`ServerSocket.accept()`、`BufferedReader.readLine()` 以及寫入操作，**絕對**必須在背景執行緒（Worker Thread / `ExecutorService` / `Thread`）中執行。
   - 收到網路資料需更新 UI 時，必須透過 `runOnUiThread(...)` 或 `new Handler(Looper.getMainLocater()).post(...)` 擲回主執行緒處理。

2. **Server 端防崩潰與異常斷線處理**
   - Server 需維護安全執行緒集合（如 `ConcurrentHashMap<String, ClientHandler>` 與 `CopyOnWriteArrayList<Stroke>`）。
   - 當某個 Client 強制關閉 App 或網路斷線時，`BufferedReader.readLine()` 會回傳 `null` 或拋出 `IOException`。
   - 伺服器必須在 `catch` 區塊中安全地關閉該 Socket、從線上清單移除該 Client，並向其他在線 Client 廣播更新後的線上名單（`SYNC_STATE`），絕不可讓伺服器崩潰（Crash）。

3. **Server 關閉與 Client 安全返回首頁**
   - 當主持人點擊「關閉伺服器」，Server 需先廣播 `SERVER_SHUTDOWN` 訊息給所有連線中的 Client，接著依序關閉所有 Client Socket 與 `ServerSocket`。
   - Client 收到 `SERVER_SHUTDOWN` 或偵測到 Socket 讀取失敗時，彈出提示（Toast / Dialog），並透過 `finish()` 或 `Intent` 安全返回首頁（`MainActivity`）。

4. **權限設定 (`AndroidManifest.xml`)**
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
   ```
