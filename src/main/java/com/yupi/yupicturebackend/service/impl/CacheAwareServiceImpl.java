package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.manager.MultiLevelCacheManager;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

/**
 * 带缓存失效钩子的 ServiceImpl 基类。
 * <p>
 * <b>为什么要有这个基类。</b>MyBatis-Plus 的写入入口远不止 {@code updateById} 一个：
 * {@code IRepository} 上有约 20 个写方法，其中批处理方法（{@code saveBatch}、
 * {@code updateBatchById}、{@code saveOrUpdateBatch}）经反编译确认走的是
 * {@code executeBatch} / {@code SqlHelper} 静态批处理，**完全绕过单条方法**——
 * 也就是说只覆盖 {@code updateById} 是漏的。
 * <p>
 * 如果把这些 override 分别写进各自的 ServiceImpl，必然产生漂移
 * （事实上已经漂移过：UserServiceImpl 漏了 saveOrUpdate）。所以收敛到这里，
 * 由 {@link #keyOf(Object)} 与 {@link #evict(Long)} 两个抽象方法让子类接入具体的缓存区。
 * <p>
 * 约定与理由见 docs/adr/0002、docs/adr/0003。
 *
 * @param <M> mapper 类型
 * @param <T> 实体类型
 * @author SPHZ
 */
public abstract class CacheAwareServiceImpl<M extends BaseMapper<T>, T> extends ServiceImpl<M, T> {

    @Resource
    protected MultiLevelCacheManager multiLevelCacheManager;

    /**
     * 取实体的缓存 key（通常是主键）。返回 null 表示本次无需失效。
     */
    protected abstract Long keyOf(T entity);

    /**
     * 真正执行失效。子类接到对应的缓存区（图片 / 用户），
     * 由 {@link MultiLevelCacheManager} 保证「先删 L2、再删 L1」的顺序。
     */
    protected abstract void evict(Long key);

    /**
     * 按 id 批量失效。
     */
    private void evictAll(Collection<?> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        for (Object id : ids) {
            // 用 Convert 容错：拿不到 Long 就跳过，绝不因为一个畸形 id 让写操作失败
            evict(Convert.toLong(id, null));
        }
    }

    /**
     * 无法按 id 失效时统一抛出。见 {@link #unsupported(String)}。
     */
    private static UnsupportedOperationException unsupported(String method) {
        return new UnsupportedOperationException(
                "[缓存一致性] " + method + " 无法确定受影响的 id，因此无法失效缓存。"
                        + "请改用按 id 的写法（save / updateById / removeById / removeByIds / saveBatch / updateBatchById），"
                        + "或在操作后自行调用 MultiLevelCacheManager 的失效方法。"
                        + "详见 docs/adr/0002、docs/adr/0007。");
    }

    // ==================== 单条写入：可精确失效 ====================

    @Override
    public boolean save(T entity) {
        boolean result = super.save(entity);
        evict(keyOf(entity));
        return result;
    }

    @Override
    public boolean saveOrUpdate(T entity) {
        boolean result = super.saveOrUpdate(entity);
        evict(keyOf(entity));
        return result;
    }

    @Override
    public boolean updateById(T entity) {
        // 必须在数据库写入成功之后再失效（docs/adr/0003）
        boolean result = super.updateById(entity);
        evict(keyOf(entity));
        return result;
    }

    @Override
    public boolean removeById(Serializable id) {
        boolean result = super.removeById(id);
        evict(Convert.toLong(id, null));
        return result;
    }

    @Override
    public boolean removeById(T entity) {
        boolean result = super.removeById(entity);
        evict(keyOf(entity));
        return result;
    }

    @Override
    public boolean removeByIds(Collection<?> list) {
        boolean result = super.removeByIds(list);
        evictAll(list);
        return result;
    }

    @Override
    public boolean removeByIds(Collection<?> list, boolean useFill) {
        boolean result = super.removeByIds(list, useFill);
        evictAll(list);
        return result;
    }

    @Override
    public boolean removeBatchByIds(Collection<?> list) {
        boolean result = super.removeBatchByIds(list);
        evictAll(list);
        return result;
    }

    // ==================== 批量写入：反编译确认绕过了单条方法，必须单独挂 ====================

    @Override
    public boolean saveBatch(Collection<T> entityList, int batchSize) {
        boolean result = super.saveBatch(entityList, batchSize);
        evictAllKeys(entityList);
        return result;
    }

    @Override
    public boolean saveOrUpdateBatch(Collection<T> entityList, int batchSize) {
        boolean result = super.saveOrUpdateBatch(entityList, batchSize);
        evictAllKeys(entityList);
        return result;
    }

    @Override
    public boolean updateBatchById(Collection<T> entityList, int batchSize) {
        boolean result = super.updateBatchById(entityList, batchSize);
        evictAllKeys(entityList);
        return result;
    }

    private void evictAllKeys(Collection<T> entityList) {
        if (CollUtil.isEmpty(entityList)) {
            return;
        }
        for (T entity : entityList) {
            evict(keyOf(entity));
        }
    }

    // ==================== 无法按 id 失效的写入：快速失败，绝不静默 ====================
    // 这些方法用 Wrapper / Map 表示条件，受影响的可能是任意多行、且拿不到 id。
    // 已用反编译确认：框架内部没有任何方法会调用它们，所以抛异常是安全的。
    //
    // 之所以选择「抛异常」而不是「记日志后放行」：放行会让缓存静默变脏，
    // 而这类脏数据极难排查（没有报错、没有线索）。宁可让写操作当场失败。

    @Override
    public boolean update(Wrapper<T> updateWrapper) {
        throw unsupported("update(Wrapper)");
    }

    @Override
    public boolean update(T entity, Wrapper<T> updateWrapper) {
        throw unsupported("update(entity, Wrapper)");
    }

    @Override
    public boolean remove(Wrapper<T> queryWrapper) {
        throw unsupported("remove(Wrapper)");
    }

    @Override
    public boolean removeByMap(Map<String, Object> columnMap) {
        throw unsupported("removeByMap(Map)");
    }
}
