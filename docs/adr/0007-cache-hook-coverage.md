# 7. 补齐写路径失效钩子：覆盖批量写入，对无法按 id 失效的写法快速失败

- 状态：已采纳
- 日期：2026-09-11
- 相关：缓存失效
- 关系：**扩展** ADR 0002。0002 确立了「把写入收敛到 override 的框架方法上」这个策略，
  本 ADR 补上当时遗漏的方法集合，并处理「根本无法按 id 失效」的那一类。

## 背景

ADR 0002 只覆盖了 4 个方法（`save` / `saveOrUpdate` / `updateById` /
`removeById(Serializable)`），而 `IRepository`（`IService` 的父接口）上共有约 **20 个写方法**。

用 `javap -c` 反编译确认了它们各自的实际走向：

| 方法 | 实际调用 |
|---|---|
| `saveBatch` / `updateBatchById` | `executeBatch` + `getSqlStatement`（**静态批处理**） |
| `saveOrUpdateBatch` | `SqlHelper.saveOrUpdateBatch`（**静态批处理**） |
| `removeById(id)` / `removeById(entity)` / `updateById` | `baseMapper.deleteById` / `baseMapper.updateById` |
| `removeByIds` | `baseMapper.deleteByIds` |
| `removeByMap` | `baseMapper.deleteByMap` |
| `removeById(Serializable, boolean)` | **在接口里就直接抛 `UnsupportedOperationException`** |

**关键结论**：批处理方法走的是静态批处理，**完全绕过单条方法**——
也就是说只覆盖 `updateById` 是漏的，`updateBatchById` 不会触发任何失效。

另外，覆盖范围不一致已经导致了**真实漂移**：`UserServiceImpl` 漏了 `saveOrUpdate`
（`PictureServiceImpl` 有）。逐个 Service 复制十几个 override 必然继续漂移。

## 决策

**1）抽一个基类 `CacheAwareServiceImpl<M, T>`，把失效钩子收敛到一处**
子类只实现两个方法：`keyOf(entity)`（缓存 key 是什么）与 `evict(key)`（调哪个缓存区）。
这样覆盖面天然一致，不会再有「这个类漏了某个方法」的问题。

**2）按 id 精确失效：覆盖单条 + 批量写入**
`save` / `saveOrUpdate` / `updateById` / `removeById(id)` / `removeById(entity)` /
`removeByIds` / `removeBatchByIds` / `saveBatch` / `saveOrUpdateBatch` / `updateBatchById`。
批量方法对集合里每个元素逐个失效。

**3）无法确定受影响 id 的写法：抛 `UnsupportedOperationException`**
`update(Wrapper)` / `update(entity, Wrapper)` / `remove(Wrapper)` / `removeByMap(Map)`。

**4）不覆盖 `removeById(Serializable, boolean)`**
它在接口里的实现本身就是抛异常，覆盖它等于假装支持一个并不存在的能力。

## 为什么「抛异常」而不是「记日志后放行」

- 放行会让缓存**静默变脏**：没有报错、没有线索，排查成本极高。
  而这类写法动辄影响多行，影响面还无法预知。
- 抛异常是**安全**的：反编译确认框架内部**没有任何方法**会调用这些 wrapper 写法
  （`removeByIds`、`removeByMap` 等走的都是 `baseMapper`，不是 `remove(Wrapper)`），
  所以拦截它们不会破坏框架行为。
- 项目当前**完全没有使用**这些写法，所以今天不会破坏任何东西；
  真正会被它拦住的，是将来想「图省事」的那次改动。
- 异常消息里直接给出替代写法，让调用方知道该怎么办。

## 后果

**必须知道的残留空洞（本策略无法覆盖）**
- **`update()` / `lambdaUpdate()` 链式 API 拦不住。** 它们由 `getBaseMapper()` 直接构造，
  `.update()` 直通 mapper，**完全绕过 service 层**，任何 service 层的 override 都管不到。
  也就是说用 `pictureService.lambdaUpdate().eq(...).update(...)` 改数据，
  缓存仍然不会被失效，而且不会报错。
  这条只能靠代码评审与注释约束，**是「service 层拦截」这个策略的固有边界**。
- 直接使用 `baseMapper` 写入同样无法拦截。当前全项目已核对，没有这种做法。

**代价**
- 基类让缓存副作用更集中，也更「隐形」：后来人读 `updateById` 的调用点时
  不会预期它有缓存副作用。这类设计的主要成本是认知成本，靠代码注释与本 ADR 传达。
- 抛异常的写法改变了框架方法的语义，属「刻意为之的不兼容」。

**验证方式**
- 用临时 Spring 测试做了端到端验证：插入临时数据 → 读取填充缓存 →
  调 `removeByIds` → 再读必须为 `null`（若钩子没生效会返回缓存旧值，
  即「删了还在」的 bug），并断言两个 wrapper 写法确实抛异常。
- 临时测试与临时数据均已清理。
