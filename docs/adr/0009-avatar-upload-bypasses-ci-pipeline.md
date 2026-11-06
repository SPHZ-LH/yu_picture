# 头像上传不复用图片上传管线

图片上传管线（`FileManager.uploadPicture`）把三件事耦合在一起：校验、经数据万象（CI）持久化生成 `_thumbnail.jpg`、提取图片信息。头像不需要缩略图也不需要进图库，复用该管线会让每次头像上传多一个永远无人读取的 `_thumbnail.jpg` 对象，并把头像功能绑死在 CI 可用性上。因此头像上传端点（`POST /file/upload`）只做 `validPicture` 校验 + `cosManager.putObject` 直传，key 为 `avatar/{userId}/{日期}_{uuid}.{ext}`，旧头像对象按 ADR 0001 保留不删。

**Considered Options**

- 复用 `uploadPicture`：零新代码，但每张头像产生一个无用缩略图对象，且 CI 故障会连带头像不可用。
- 给 `uploadPicture` 加"不生成缩略图"开关：让图片上传的公共方法携带一个只服务于头像的参数，读者无法从图片域理解它。
- 直传（采纳）：约 15 行，职责单一，与图片管线互不影响。

**Consequences**

- 头像对象没有缩略图变体。将来若有人"顺手"把头像上传改走 `putPictureObject`，请视为回归而非优化。
- 后缀白名单（jpeg/jpg/png/webp）可被改名攻击绕过（`木马.exe` 改名为 `.jpg`），故本端点在 `validPicture` 之外追加魔数嗅探（jpeg `FF D8 FF`、png `89 50 4E 47`、webp `RIFF….WEBP`）。该嗅探只加在头像路径，未回溯到图片上传——存量行为不在本次变更范围内。
- 新增图片格式时需同时改后缀白名单与魔数表，两处缺一不可。
