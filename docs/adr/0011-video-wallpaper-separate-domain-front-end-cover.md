# 动态壁纸独立成 video 域，后端中转上传、封面由前端抽帧并充当尺寸来源

动态壁纸（mp4）不复用 picture 表：新建 `video` 表与 `VideoController/VideoService`，只复用 `CosManager` 里与图片无关的通用能力。上传是**一次 multipart 同时交视频和前端抽好的封面**（`/video/upload`，两个 `@RequestPart`），后端**先传封面再传视频**，宽高与格式取自数据万象对封面的 `ImageInfo` 实测值（`CosManager#putImageObjectWithInfo`），只有时长由客户端回传并做量级校验。下载 302 到预签名 GET，用 `ResponseHeaderOverrides` 覆盖 `Content-Disposition` 把文件名还给用户。不引入 FFmpeg，不开通媒体处理，不进多级缓存（`ServiceImpl` 而非 `CacheAwareServiceImpl`）。

压住这条线的三个实测事实：数据万象**媒体处理未开通**（`ci-process=snapshot|mediainfo` 一律 `FunctionNotEnabled`，对不存在的 .mp4 也一样，说明是账号/桶级开关）；COS 源站域名 GET 带 `Content-Disposition: attachment` + `x-cos-force-download: true`，**但只作用于导航层**——Chrome 153 / Edge 下 `<video>` 照常加载与 `drawImage`，裸链接才会变下载；桶上 **CORS 已配 `Access-Control-Allow-Origin: *` 且支持 Range 206**。另外 `PictureUploadTemplate` 第 58 行直接取 `getImageInfo().getWidth()`，"上传"这个动作在现有代码里被写死成"上传即图片"。

**Considered Options**

- 给 picture 加 `picType` 泛化成媒体表：上传模板、CI 缩略图规则、色系提取、TIIA 图库同步、`getImageInfo` 取值，五条链路各要长一个 `if (是视频)`，最后没有一条是干净的；换来的是省掉一套 CRUD。
- 云端生成封面与媒体信息（`ci-process=snapshot` / `mediainfo`）：技术上最省事，但要先开通媒体处理并接受按转码时长计费——在一个还没有内容的品类上先挂一个计费产品是本末倒置。开通后可替换抽帧这一步，`video` 表结构不需要动。
- 前端预签名直传 COS：绕过后端带宽与 Tomcat 线程，但要新增签名接口和"客户端声称的 key 是否真是它传的那个对象"的防伪造校验。30MB 量级下后端中转（Spring 默认把超限请求体直接落磁盘临时文件，内存不是问题）代价可接受。
- 列表用 `<video preload="metadata">` 直接渲染首帧、不存封面文件：省一个对象，但每页 12 个视频元素各自拉字节，非 faststart 的文件还要额外拉尾部，流量无法预算。

**Consequences**

- 封面按视频**原始帧尺寸**入库（降分辨率就取不到真实宽高，4K 会被误判成 2K），因此列表和管理的缩略图靠 URL 参数即时缩放：`cosResizeUrl()` 追加 `imageMogr2/thumbnail/600x`，不为同一张封面再存派生对象。封面体积上限因此是 5MB 而不是头像的 2MB，前端逐级降质压进上限。
- 抽帧顺带成为**可播性闸门**：判据是"采样区 alpha 是否为 255"（`drawImage` 拿不到解码帧时不会画任何东西，canvas 保持透明的 0/0/0/0），解不出帧就报 `media error code`。H.265/HEVC 的 mp4 会在这里被拒——这类文件下载后本地能播、站内不能播，拒掉比入库后成为坏封面好。
- **不能用"帧方差/是否纯色"当解码成败的判据**（2026-09-20 实测踩到）：一条 4K 动漫线稿壁纸全片 5 个采样点方差都是 0，因为画面本来就是白底细线，中心 64×64 落在空白纸面上；而 `seeked` 正常触发、`readyState=4`、alpha=255，帧是好的。方差只配用来在多个候选时间点里挑一张更好看的，挑不到有变化的就退回第一张解出来的帧——纯色、极简、渐变壁纸是这个站点的正当内容，不能当异常拒掉。
- `requestVideoFrameCallback` 对**不在文档流里**的 video 元素不触发（实测等满超时），抽帧路径上不要用它，`seeked` 之后直接画即可；也不要把 currentTime 设成它已有的值，Chrome 不发 `seeked`，会撞满超时。
- 非 faststart 的视频 hover 预览明显起播慢。用头部盒扫描判定（`moov` 是否先于 `mdat`），**只提示不拦**：内容本身可用，且壁纸的实际消费动作是下载。
- `spring.servlet.multipart.max-file-size` 从 10MB 放宽到 32MB 是**全局**的，所有接口都会接收更大的请求体，只靠各自的业务校验兜底（图片 2MB、头像 2MB 不变）。
- 公共图库原本没有任何配额（`PictureServiceImpl#uploadPicture` 只在 `spaceId` 非空时校验），视频单个体积是图片的十几倍，因此给它加了一对代码常量：每用户 200MB / 20 条。
- 两个 PUT 不是原子的：封面传成、视频传败会在桶里留下一个 ≤5MB 的孤儿封面。选择"先封面后视频"就是为了让孤儿的体积是小的那个；记录层面没有半态。
- 视频不支持裁剪/扩展/以图搜图/色系/收藏，详情页与接口层同样拒绝。人工审核是内容的唯一闸门，管理员在 `VideoManagePage` 内嵌播放器审片。
- ADR 编号有两套并存（本目录 0001–0010 与根 `docs/adr/0001-image-edit-processing-via-tencent-ci.md`），本文沿用本目录的连续编号，根目录那篇不动。

**2026-09-25 上限调整（用户定）**：静态图片统一 20MB、格式限 `jpeg/jpg/png/webp`（图库上传与批量抓取同步，前端去掉 GIF 并改用与后端一致的后缀白名单）；动态壁纸 50MB、格式 `mp4`。随之上调 `spring.servlet.multipart.max-file-size` 32MB → 52MB、`max-request-size` 38MB → 58MB（视频 50 + 封面 5 + 余量）。后端后缀判定统一 `toLowerCase`，修掉 `.PNG` 被无故拒绝的问题；`FileManager` 里那套无人调用的 2MB 校验也一并对齐，避免留下过时口径。
