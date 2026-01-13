###  0.6.2 通信协议 C/S 拆分

```mermaid
graph TD
    subgraph Top-Level
        P["protocol/ (.proto files)"]
        J["java/"]
    end

    subgraph java/common
        direction LR
        CA["common-api"]
        PA["protocol-api"]
        ID["atomicio-id/*"]
        CC["atomicio-codec/*"]
    end

    subgraph java/server
        direction LR
        SA["server-api"]
        SC["server-core"]
        SS["server-starter"]
        SE["server-examples/*"]
    end

    subgraph java/client
        direction LR
        CLA["client-api"]
        CLC["client-core"]
        CLS["client-starter"]
        CLE["client-examples/*"]
    end

    subgraph java/common/atomicio-codec
        direction LR
        CT["codec-text"]
        CP["codec-protobuf"]
    end

    J --> server & client & common

    server --> common
    client --> common

    SA --> CA & PA
    SC --> SA & CC

    CLA --> CA & PA
    CLC --> CLA & CC

    CC --> PA

%% 使用特定样式的节点代替 note
    noteP[语言无关的协议定义] --- P
    noteCA[通用服务接口] --- CA
    notePA[通用数据接口] --- PA
    noteID[ID 生成器实现] --- ID
    noteCC[编解码实现] --- CC

    noteSA[服务器行为接口] --- SA
    noteSC[服务器核心实现] --- SC

    noteCLA[客户端行为接口] --- CLA
    noteCLC[客户端核心实现] --- CLC

%% 样式定义，让备注看起来更像 note
    classDef note fill:#fff5ad,stroke:#ccc,stroke-dasharray: 5 5;
    class noteP,noteCA,notePA,noteID,noteCC,noteSA,noteSC,noteCLA,noteCLC note;
```
```
common 模块 (基础):
common-api: 定义通用服务接口 (IdGenerator) 和配置模型 (AtomicIOProperties)。
protocol-api: 定义通用数据接口 (AtomicIOMessage)。
atomicio-id/*: 提供 IdGenerator 的具体实现。
atomicio-codec/*: 提供可被 C/S 两端复用的、中立的 Handler。但是，CodecProvider 不在这里！
server 模块 (服务器端):
server-api:
定义 AtomicIOEngine 接口。
定义服务器端专属的 ServerCodecProvider 接口。
server-core:
实现 DefaultAtomicIOEngine。
提供服务器端专属的 ServerCodecProvider 实现（如 ServerProtobufCodecProvider），这些实现会去复用 common/codec 中的 Handler。
server-starter, server-examples: 依赖于 server-core 和 server-api。
client 模块 (客户端):
client-api:
定义 AtomicIOClient 接口。
定义客户端专属的 ClientCodecProvider 接口。
client-core:
实现 DefaultAtomicIOClient。
提供客户端专属的 ClientCodecProvider 实现（如 ClientProtobufCodecProvider）。
client-starter, client-examples: 依赖于 client-core 和 client-api。
```