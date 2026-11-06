# 第三批增强：衍生档下载、色调调整、调色板与九色系、标记位、空间动态、草稿

屿图 UI 定稿驱动的五项现有模块增强，一次成形：

- **多分辨率下载**：`GET /picture/download/tiers?pictureId=` 列出可用档位（8K/4K/2K/1080P，只含严格小于原图的档位，1.1 倍余量防「衍生档与原图肉眼无差」），`GET /picture/download/{id}?tier=4k` 重定向到 `url + ?imageMogr2/thumbnail/{W}x`（宽度等比缩放）。档位预算与清晰度档位共用同一组总像素阈值（921600/2073600/8294400/33177600）；非本 COS 域名（外链图）与档位不合法时回退原图；衍生档下载与原图一样计数、记日志。
- **色调调整**：`/picture/transform` 新增 brightness/contrast/saturation 三参（0-200，100 为原图，与前端 CSS 滑杆同语义），服务端换算为 imageMogr2 的 [-100,100] 偏移（腾讯数据万象语义，`/brightness/<v>` 三参同范围，0 = 不变），作为链式管线最后一步。设计稿的滤镜预设（黑白/胶片/暖阳/冷调/青橙）**是前端把预设映射成三组滑杆值**，服务端只认原始参数。
- **调色板与九色系**：ColorExtractor 新增 `extractPalette`（32×32 采样按 5bit/通道量化计数，贪心收录与已选色距离 ≥ 60 的 top-N 色），`picture.palette` 存 JSON（最多 5 个 hex，详情页「画面色彩」色点）；色系分桶对齐屿图 9 色——新增 light（近白冷调）/cream（近白暖相 hue 15-100°），pink 桶并入 purple（260-345°），历史数据 `UPDATE colorFamily='purple' WHERE 'pink'` 已迁移。回填接口改为「colorFamily 或 palette 为空」驱动，失败留痕（colorFamily=unknown、palette="[]"）防死循环。
- **标记位三件套**：`picture.isFeatured`（编辑精选，不互斥，`/picture/list/page/vo` 新增 isFeatured 筛选）、`picture.isDailyPick`（今日一图，服务层互斥，`GET /picture/daily`）、`user.isFeaturedCreator`（本周精选创作者，服务层互斥，`GET /user/featured-creator`）。互斥清位走逐条 updateById——缓存失效钩子只挂在按 id 的写入上（docs/adr/0002），update(Wrapper) 会被 CacheAwareServiceImpl 快速失败。
- **空间动态**：`space_activity_log` 追加流水（upload/edit/delete），埋点在上传/删除/信息编辑/画面编辑/扩图保存五处，记录尽力而为；`POST /space/activity/page` 仅空间成员可看；图片删除后条目保留（picture 字段为 null）。成员作品数：`SpaceUserVO.pictureCount` 由 PictureMapper 按空间+成员 GROUP BY 实时聚合（含未过审——面板统计的是占用事实）。
- **草稿**：审核枚举新增 DRAFT(3)。上传带 `isDraft=true`（仅公共图库生效）落库即 DRAFT，不进审核流、checkPictureReadable 对他人按不存在处理；`POST /picture/draft/submit`（作者/管理员）DRAFT → REVIEWING（管理员直接 PASS）。`/picture/list/page/vo` 对「查自己 userId」的请求放开审核态过滤（否则自己的草稿/待审/被拒作品永远不可见），他人与匿名仍强制 PASS。

**Considered Options**

- 衍生档预先物化（上传时生成 2K/4K 副本存 COS）：下载零计算，但每张图多两份存储、上传变慢，而 imageMogr2 实时缩放对 COS 是毫秒级边缘计算——当前量级不值得物化。
- 滤镜在服务端做色相类算子：imageMogr2 无 hue-rotate/sepia，硬凑要么引入本地像素处理（违背「云上处理」纪律，见 ADR 0001/0007），要么前端 canvas 合成后整图重传（丢掉云上链路与元数据提取）。折中：滤镜预设由前端映射到亮度/对比度/饱和度三参，预览用 CSS filter 近似，保存结果以服务端产物为准。
- 调色板存独立表（picture_color 一行一色）：支持按色板色值检索，但设计只需要详情页展示 5 个色点，JSON 列零 JOIN；真要做「按任意色值找图」时再反范式。
- 空间动态写进现有日志框架（logback/ES）：查询需求是分页列表不是日志检索，业务表最直接；量级起来后再归档。

**Consequences**

- 衍生档尺寸是估算值（宽高各 ±1px），详情页展示用 tiers 接口的估算尺寸即可，不要自行换算。
- 色系 9 档是 breaking 变更：前端色系筛选的取值域需同步（pink 移除、light/cream 新增），历史 pink 行已迁移为 purple。
- 今日一图/精选创作者的互斥在服务层不在库层（MySQL 无部分唯一索引），并发下理论可双精选，展示端取最新一条兜底。
- 空间动态记录失败只 WARN：动态是可丢失旁路数据，绝不回滚图片主流程。
- 草稿只在公共图库路径生效：空间图片上传即 PASS（成员可见），没有「空间内草稿」概念。
