package org.dromara.warm.flow.core.handler;

/**
 * 默认状态映射：语义 key 直接作为持久化编码，与引入扩展点之前的行为完全一致。
 * <p>
 * 宿主未注册 {@link StatusMapper} 时使用本实现，升级不需要迁移数据。
 *
 * @author may
 */
public class DefaultStatusMapper implements StatusMapper {

}
