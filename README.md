# Atomicio 阿多米西奥 🇨🇳
### 作者：Vevoly
Atomicio: A high-performance, reactive IO framework for IM, IoT, and Gaming, Streaming built on Netty.  
阿多米西奥：一个高性能的事件驱动IO框架，可用于即时通讯，物联网，游戏，流媒体等服务器的底层框架，基于Netty构建。

# Coming Soon !!!
# 框架正在不断迭代开发中，敬请期待 v1.0.0 版本...
Atomicio-Api 模块架构图
```mermaid
classDiagram
    direction LR

    class AtomicIOEngine {
        <<Interface>>
        +start(): Future~Void~
        +shutdown(): void
        +on(EventType, EventListener): void
        +sendToUser(String, AtomicIOMessage): void
        +sendToGroup(String, AtomicIOMessage): void
        +broadcast(AtomicIOMessage): void
        +joinGroup(String, String): void
        +leaveGroup(String, String): void
    }

    class AtomicIOSession {
        <<Interface>>
        +getId(): String
        +send(AtomicIOMessage): void
        +close(): void
        +isActive(): boolean
        +setAttribute(String, Object): void
        +getAttribute(String): Object
    }

    class AtomicIOMessage {
        <<Interface>>
        +getCommandId(): int
        +getPayload(): byte[]
    }
    
    class AtomicIOEventListener {
        <<Interface>>
        +onEvent(Object): void
    }

    class AtomicIOEventType {
        <<Enumeration>>
        CONNECT
        DISCONNECT
        MESSAGE
        ERROR
        IDLE
    }

    AtomicIOEngine "1" -- "N" AtomicIOEventListener : 注册 (registers)
    AtomicIOEngine "1" -- "N" AtomicIOSession : 管理 (manages)
    AtomicIOEngine ..> AtomicIOMessage : 发送 (sends)
    AtomicIOEngine ..> AtomicIOEventType : 使用 (uses)
    
    AtomicIOSession ..> AtomicIOMessage : 发送 (sends)

    AtomicIOEventListener ..> AtomicIOSession : 处理 (handles)
    AtomicIOEventListener ..> AtomicIOMessage : 处理 (handles)
```
Atomicio-Api 模块数据流转图
```mermaid
sequenceDiagram
  participant UserCode as 你的业务代码
  participant Engine as AtomicIOEngine
  participant CoreImpl as 引擎核心实现 (atomicio-core)
  participant Session as AtomicIOSession
  participant Netty as 底层Netty

  Note over UserCode, Netty: === 阶段一: 初始化 ===
  UserCode->>Engine: engine.on(MESSAGE, listener)
  UserCode->>Engine: engine.start()
  Engine->>CoreImpl: (启动Netty服务...)

  Note over UserCode, Netty: === 阶段二: 客户端消息上行 ===
  Netty->>+CoreImpl: (收到网络数据包)
  CoreImpl->>CoreImpl: 解码 -> AtomicIOMessage
  CoreImpl->>CoreImpl: 找到对应的 AtomicIOSession
  CoreImpl->>+Engine: 触发 MESSAGE 事件
  Engine->>+UserCode: listener.onEvent(session, message)
  UserCode->>UserCode: 执行业务逻辑 (e.g., 保存聊天记录)
  

Note over UserCode, Netty: === 阶段三: 服务器消息下行 ===
UserCode->>+Engine: engine.sendToUser("user-B", responseMessage)
Engine->>+CoreImpl: (处理发送请求)
CoreImpl->>CoreImpl: 查找 "user-B" 对应的 Session
CoreImpl->>+Session: session.send(responseMessage)
Session->>+Netty: (调用 channel.writeAndFlush)
Netty-->>-Session: (发送消息给客户端)
Session-->>-CoreImpl: (发送成功)
CoreImpl-->>-Engine: (发送成功) 
Engine-->>-UserCode: (发送成功)
```

## AtomicIOMessage
IM 场景示例：
```java
public class ImChatMessage implements AtomicIOMessage {
    public static final int COMMAND_ID = 2001;

    private String fromUserId;
    private String toUserId;
    private String content;

    // ... constructor, getters, setters ...

    @Override
    public int getCommandId() {
        return COMMAND_ID;
    }

    @Override
    public byte[] getPayload() {
        // 使用者选择用 JSON 序列化
        String json = new Gson().toJson(this);
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
```
游戏场景示例：
```java
// 使用者自己定义的 Protobuf 生成的消息类
// message PlayerMove {
//   int32 x = 1;
//   int32 y = 2;
// }

// 假设 Protobuf 插件已经生成了 PlayerMove 类
public class GamePlayerMoveMessage implements AtomicIOMessage {
    public static final int COMMAND_ID = 2001;
    private PlayerMove protoMessage; // Protobuf 生成的对象

    public GamePlayerMoveMessage(PlayerMove protoMessage) {
        this.protoMessage = protoMessage;
    }

    @Override
    public int getCommandId() {
        return COMMAND_ID;
    }

    @Override
    public byte[] getPayload() {
        // 使用者选择用 Protobuf 序列化
        return protoMessage.toByteArray();
    }
}
```
## AtomicIOSession
1. IM / 在线客服场景:
* 核心需求: 将一个匿名的网络连接与一个具体的“用户”身份绑定。
* AtomicIOSession 的作用:
  * getId(): 用于日志追踪和问题排查，例如 "Session [xxx] disconnected due to heartbeat timeout."
  * send(message): 用于发送私聊或群聊消息。
  * setAttribute("userId", "user-123"): 这是整个场景的命脉。 当用户通过 LOGIN 指令认证成功后，业务逻辑会调用这个方法，将 userId 存入 Session。从此以后，这个 Session 就代表了 user-123。引擎在执行 sendToUser("user-123", ...) 时，就能通过这个属性找到对应的 Session。
2. 游戏服务器场景:
* 核心需求: 管理玩家状态，并将连接与玩家实体关联。
* AtomicIOSession 的作用:
  * isActive(): 在广播玩家位置同步帧之前，可以快速检查玩家是否还在线，避免无效操作。
  * close(): GM（游戏管理员）或反作弊系统可以用这个方法来“踢人下线”。
  * setAttribute(...) 的威力: 它可以被玩出花来。
    * session.setAttribute("playerId", 10086): 绑定玩家ID。
    * session.setAttribute("state", PlayerState.IN_LOBBY): 存储玩家当前的状态（在大厅、房间中、战斗中）。
    * session.setAttribute("roomId", "room-abc"): 存储玩家所在的房间ID，方便业务逻辑快速查找。
3. 物联网 (IoT) 场景:
* 核心需求: 身份认证（通常是设备ID或证书），并能对特定设备下发指令。
* AtomicIOSession 的作用:
  * send(message): 向设备下发远程控制指令（如“开灯”）或请求数据。
  * setAttribute("deviceId", "SN-A1B2C3D4"): 在设备通过认证后，将它的唯一设备ID绑定到 Session 上。
  * session.setAttribute("deviceType", "TemperatureSensor"): 还可以存储设备的类型、固件版本等元数据，方便进行分类管理和消息推送。
```mermaid
sequenceDiagram
    participant Client
    participant Engine
    participant Your_Message_Listener as "你的 onMessage 监听器 (分发器)"
    participant Your_Handlers as "你的 handleLogin 等方法"

    Client->>Engine: 发送消息 (e.g., "1001:userA:mytoken")
    Engine->>Engine: (解码成 TextMessage 对象)
    Engine->>Your_Message_Listener: fireMessageEvent(session, message)
    
    Your_Message_Listener->>Your_Message_Listener: switch (message.getCommandId())
    Note right of Your_Message_Listener: commandId is 1001, dispatching to handleLogin
    
    Your_Message_Listener->>Your_Handlers: handleLogin(session, message)
    Your_Handlers->>Your_Handlers: (执行认证逻辑)
    
    Note right of Your_Handlers: 认证成功!
    Your_Handlers->>Engine: engine.bindUser("userA", session)
    
    Engine->>Client: session.send(responseMessage)
```
Disruptor 业务模型
```mermaid
graph TD
    subgraph "Netty I/O Threads (workerGroup)"
        A[Client Connections] --> B(EngineChannelHandler);
        B -- "1. 收到网络事件(Connect, Read, etc.)" --> C{创建 AtomicIOEvent 对象};
        C -- "2. 极速发布事件 (纯内存操作)" --> D[Disruptor RingBuffer];
    end

    subgraph "High-Performance Buffer"
        D -- "3. 事件在队列中等待" --> E(AtomicIOEvent);
    end

    subgraph "Dedicated Business Threads (Disruptor Consumers)"
        F[AtomicIOEventHandler] -- "4. 消费事件 (阻塞等待)" --> E;
        F -- "5. 执行真正的事件分发" --> G(DefaultAtomicIOEngine);
        G -- "6. fireXxxEvent()" --> H{遍历 Listener 列表};
        H -- "7. 执行业务逻辑" --> I[ onMessage 等回调];
    end
    
    style B fill:#cde4ff,stroke:#333,stroke-width:2px
    style F fill:#d5e8d4,stroke:#333,stroke-width:2px
```
```mermaid
sequenceDiagram
    participant Netty_IO_Thread
    participant EngineChannelHandler
    participant Disruptor_RingBuffer
    participant Business_Thread
    participant AtomicIOEventHandler
    participant Engine
    participant Your_Message_Listener

    Netty_IO_Thread->>+EngineChannelHandler: channelRead(ByteBuf)
    Note right of Netty_IO_Thread: 运行在 I/O 线程
    EngineChannelHandler->>EngineChannelHandler: (解码成 AtomicIOMessage)
    EngineChannelHandler->>Disruptor_RingBuffer: publishEvent(type, session, msg)
    Note right of EngineChannelHandler: 极快地发布事件，然后返回
    EngineChannelHandler-->>-Netty_IO_Thread: (I/O 线程被释放)

    Business_Thread->>+AtomicIOEventHandler: onEvent(AtomicIOEvent)
    Note right of Business_Thread: 运行在业务线程, <br> 阻塞等待 RingBuffer 中的事件
    
    AtomicIOEventHandler->>+Engine: engine.fireMessageEvent(session, msg)
    Engine->>+Your_Message_Listener: listener.onEvent(session, msg)
    Your_Message_Listener->>Your_Message_Listener: (执行数据库、RPC等耗时操作)
    Your_Message_Listener-->>-Engine: (执行完毕)
    Engine-->>-AtomicIOEventHandler: 
    AtomicIOEventHandler-->>-Business_Thread: (等待下一个事件)
```