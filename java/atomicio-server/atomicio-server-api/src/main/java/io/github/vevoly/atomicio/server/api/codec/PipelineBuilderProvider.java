package io.github.vevoly.atomicio.server.api.codec;

/**
 * 标志位接口
 * 如果一个 {@link AtomicIOServerCodecProvider} 实现了此接口，
 * 意味着它将通过 {@code buildPipeline()} 方法全权负责 Pipeline 的构建，
 * 引擎将不会为其添加任何后续的通用 Handler。
 * 这主要用于流媒体等需要完全自定义 Pipeline 的高级协议。
 *
 * @since 0.6.3
 * @author vevoly
 */
public interface PipelineBuilderProvider extends AtomicIOServerCodecProvider {
    // 这个接口是空的，只用作类型检查 (instanceof)
}
